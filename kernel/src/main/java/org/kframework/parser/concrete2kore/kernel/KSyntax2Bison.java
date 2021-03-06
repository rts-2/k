// Copyright (c) 2019 K Team. All Rights Reserved.
package org.kframework.parser.concrete2kore.kernel;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.kframework.Collections;
import org.kframework.TopologicalSort;
import org.kframework.attributes.Att;
import org.kframework.backend.kore.ModuleToKORE;
import org.kframework.definition.Associativity;
import org.kframework.definition.Module;
import org.kframework.definition.NonTerminal;
import org.kframework.definition.Production;
import org.kframework.definition.ProductionItem;
import org.kframework.definition.RegexTerminal;
import org.kframework.definition.Sentence;
import org.kframework.definition.SyntaxAssociativity;
import org.kframework.definition.Tag;
import org.kframework.definition.Terminal;
import org.kframework.definition.TerminalLike;
import org.kframework.kore.KLabel;
import org.kframework.kore.Sort;
import org.kframework.parser.concrete2kore.generator.RuleGrammarGenerator;
import org.kframework.utils.StringUtil;
import org.kframework.utils.errorsystem.KEMException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import scala.Tuple2;

import static org.kframework.Collections.*;
import static org.kframework.definition.Constructors.*;
import static org.kframework.kore.KORE.*;

public class KSyntax2Bison {

  private static void computeSide(int idx, Production prod, List<ProductionItem> items, Module module, scala.collection.Set<Tuple2<Tag, Tag>> assoc, Map<Set<Tag>, Integer> ordinals, Set<Tuple2<Sort, Set<Tag>>> nts, MutableInt nextOrdinal) {
    NonTerminal nt = (NonTerminal) items.get(idx);
    Tag parent = new Tag(prod.klabel().get().name());
    Set<Tag> prods = new HashSet<>();
    for (Tag child : iterable(module.priorities().relations().get(parent).getOrElse(() -> Collections.<Tag>Set()))) {
      prods.add(child);
    }
    for (Tuple2<Tag, Tag> entry : iterable(assoc)) {
      if (entry._1().equals(parent)) {
        prods.add(entry._2());
      }
    }
    if (prods.isEmpty()) {
      return;
    }
    int ordinal;
    if (ordinals.containsKey(prods)) {
      ordinal = ordinals.get(prods);
    } else {
      ordinal = nextOrdinal.intValue();
      ordinals.put(prods, nextOrdinal.intValue());
      nextOrdinal.increment();
    }
    items.set(idx, NonTerminal(Sort(nt.sort().name() + "#" + ordinal, nt.sort().params()), nt.name()));
    nts.add(Tuple2.apply(nt.sort(), prods));
  }

  public static Module transformByPriorityAndAssociativity(Module module) {
    Map<Set<Tag>, Integer> ordinals = new HashMap<>();
    MutableInt nextOrdinal = new MutableInt(0);
    Set<Sentence> sentences = new HashSet<>();
    Set<Tuple2<Sort, Set<Tag>>> nts = new HashSet<>();
    for (Sentence s : iterable(module.sentences())) {
      if (s instanceof Production) {
        Production prod = (Production)s;
        if (prod.klabel().isDefined() && prod.params().isEmpty()) {
          List<ProductionItem> items = new ArrayList<>(mutable(prod.items()));
          if (items.get(0) instanceof NonTerminal) {
            computeSide(0, prod, items, module, module.rightAssoc(), ordinals, nts, nextOrdinal);
          }
          if (items.size() > 1 && items.get(items.size() - 1) instanceof NonTerminal) {
            computeSide(items.size()-1, prod, items, module, module.leftAssoc(), ordinals, nts, nextOrdinal);
          }
          sentences.add(Production(prod.klabel(), prod.params(), prod.sort(), immutable(items), prod.att().add(Att.ORIGINAL_PRD(), Production.class, prod)));
        } else {
          sentences.add(prod.withAtt(prod.att().add(Att.ORIGINAL_PRD(), Production.class, prod)));
        }
      } else {
        sentences.add(s);
      }
    }
    module = Module(module.name(), module.imports(), immutable(sentences), module.att());
    Deque<Tuple2<Sort, Set<Tag>>> worklist = new ArrayDeque<>(nts);
    worklist.addAll(nts);
    while (!worklist.isEmpty()) {
      Tuple2<Sort, Set<Tag>> item = worklist.poll();
      for (Production prod : iterable(module.productionsForSort().apply(item._1().head()))) {
        int ordinal = ordinals.get(item._2());
        Sort newNT = Sort(item._1().name() + "#" + ordinal, item._1().params());
        if (prod.isSubsort()) {
          worklist.offer(Tuple2.apply(prod.getSubsortSort(), item._2()));
          sentences.add(Production(prod.klabel(), prod.params(), newNT, Seq(NonTerminal(prod.getSubsortSort(), prod.nonterminals().apply(0).name())), prod.att()));
        } else if (prod.klabel().isEmpty() || !item._2().contains(new Tag(prod.klabel().get().name()))) {
          sentences.add(Production(prod.klabel(), prod.params(), newNT, prod.items(), prod.att()));
        }
      }
    }
    return Module(module.name(), module.imports(), immutable(sentences), module.att());
  }

  public static void writeParser(Module module, Scanner scanner, Sort start, File path) {
    module = transformByPriorityAndAssociativity(module);
    StringBuilder bison = new StringBuilder();
    bison.append("%{\n" +
        "#include <stdio.h>\n" +
        "#include \"node.h\"\n" +
        "#include \"parser.tab.h\"\n" +
        "int yylex(void);\n" +
        "void yyerror(const char *);\n" +
        "char *enquote(char *);\n" +
        "node *result;\n" +
        "%}\n\n");
    bison.append("%define api.value.type union\n");
    bison.append("%define parse.error verbose\n");
    for (int kind : scanner.kinds()) {
      TerminalLike tok = scanner.getTokenByKind(kind);
      String val;
      if (tok instanceof Terminal) {
        val = ((Terminal)tok).value();
      } else {
        val = ((RegexTerminal)tok).regex();
      }
      bison.append("%token <char *> TOK_" + kind + " " + kind + " " + StringUtil.enquoteCString(val) + "\n");
    }
    for (Sort sort : iterable(module.allSorts())) {
      bison.append("%nterm <node *> ");
      encode(sort, bison);
      bison.append("\n");
    }
    bison.append("%start top");
    bison.append("\n");
    bison.append("\n%%\n");
    bison.append("top: ");
    encode(start, bison);
    bison.append(" { result = $1; } ;\n");
    Map<Sort, List<Production>> prods = stream(module.productions()).collect(Collectors.groupingBy(p -> p.sort()));
    for (Sort sort : iterable(module.allSorts())) {
      encode(sort, bison);
      bison.append(":\n");
      String conn = "";
      for (Production prod : Optional.ofNullable(prods.get(sort)).orElse(java.util.Collections.emptyList())) {
        bison.append("  " + conn);
        processProduction(prod, module, scanner, bison);
        conn = "|";
      }
      bison.append(";\n");
    }
    bison.append("\n%%\n");
    bison.append("\n" +
        "void yyerror (const char *s) {\n" +
        "    fprintf (stderr, \"%s\\n\", s);\n" +
        "}\n");
    try {
      FileUtils.write(path, bison);
    } catch (IOException e) {
      throw KEMException.internalError("Failed to write file for parser", e);
    }
  }

  private static final Pattern identChar = Pattern.compile("[A-Za-z0-9]");

  private static void encode(Sort sort, StringBuilder sb) {
    sb.append("Sort");
    StringUtil.encodeStringToAlphanumeric(sb, sort.name(), StringUtil.asciiReadableEncodingDefault, identChar, "_");
    sb.append("_");
    String conn = "";
    for (Sort param : iterable(sort.params())) {
      sb.append(conn);
      encode(param, sb);
      conn = "_";
    }
    sb.append("_");
  }

  private static void processProduction(Production prod, Module module, Scanner scanner, StringBuilder bison) {
    int i = 1;
    List<Integer> nts = new ArrayList<>();
    for (ProductionItem item : iterable(prod.items())) {
      if (item instanceof NonTerminal) {
        NonTerminal nt = (NonTerminal)item;
        encode(nt.sort(), bison);
        bison.append(" ");
        nts.add(i);
      } else {
        TerminalLike t = (TerminalLike)item;
        if (!(t instanceof Terminal && ((Terminal)t).value().equals(""))) {
          bison.append("TOK_" + scanner.resolve(t) + " ");
        } else {
          i--;
        }
      }
      i++;
    }
    prod = prod.att().getOptional(Att.ORIGINAL_PRD(), Production.class).orElse(prod);
    if (prod.att().contains("token") && !prod.isSubsort()) {
      bison.append("{\n" +
          "  node *n = malloc(sizeof(node));\n" +
          "  n->symbol = ");
      boolean isString = module.sortAttributesFor().get(prod.sort().head()).getOrElse(() -> Att.empty()).getOptional("hook").orElse("").equals("STRING.String");
      if (!isString) {
        bison.append("enquote(");
      }
      bison.append("$1");
      if (!isString) {
        bison.append(")");
      }
      bison.append(";\n" +
          "  n->str = true;\n" +
          "  n->nchildren = 0;\n" +
          "  node *n2 = malloc(sizeof(node) + sizeof(node *));\n" +
          "  n2->symbol = \"\\\\dv{");
      encodeKore(prod.sort(), bison);
      bison.append("}\";\n" +
          "  n2->str = false;\n" +
          "  n2->nchildren = 1;\n" +
          "  n2->children[0] = n;\n" +
          "  $$ = n2;\n" +
          "}\n");
    } else if (!prod.att().contains("token") && prod.isSubsort() && !prod.att().contains(RuleGrammarGenerator.NOT_INJECTION)) {
      bison.append("{\n" +
          "  node *n = malloc(sizeof(node) + sizeof(node *));\n" +
          "  n->symbol = \"inj{");
      encodeKore(prod.getSubsortSort(), bison);
      bison.append(", ");
      encodeKore(prod.sort(), bison);
      bison.append("}\";\n" +
          "  n->str = false;\n" +
          "  n->nchildren = 1;\n" +
          "  n->children[0] = $1;\n" +
          "  $$ = n;\n" +
          "}\n");
    } else if (prod.att().contains("token") && prod.isSubsort()) {
      bison.append("{\n" +
          "  node *n = malloc(sizeof(node) + sizeof(node *));\n" +
          "  n->symbol = \"\\\\dv{");
      encodeKore(prod.sort(), bison);
      bison.append("}\";\n" +
          "  n->str = false;\n" +
          "  n->nchildren = 1;\n" +
          "  n->children[0] = $1->children[0];\n" +
          "  $$ = n;\n" +
          "}\n");
    } else if (prod.klabel().isDefined()) {
      bison.append("{\n" +
          "  node *n = malloc(sizeof(node) + sizeof(node *)*").append(nts.size()).append(");\n" +
          "  n->symbol = \"");
      encodeKore(prod.klabel().get(), bison);
      bison.append("\";\n" +
          "  n->str = false;\n" +
          "  n->nchildren = ").append(nts.size()).append(";\n");
      for (i = 0; i < nts.size(); i++) {
        bison.append(
          "  n->children[").append(i).append("] = $").append(nts.get(i)).append(";\n");
      }
      bison.append(
          "  $$ = n;\n" +
          "}\n");
    } else if (prod.att().contains("bracket")) {
      bison.append("{\n" +
          "  $$ = $").append(nts.get(0)).append(";\n" +
          "}\n");
    }
    bison.append("\n");
  }

  private static void encodeKore(KLabel klabel, StringBuilder bison) {
    StringBuilder sb = new StringBuilder();
    ModuleToKORE.convert(klabel, sb);
    String quoted = StringUtil.enquoteCString(sb.toString());
    bison.append(quoted.substring(1, quoted.length() - 1));
  }

  private static void encodeKore(Sort sort, StringBuilder bison) {
    StringBuilder sb = new StringBuilder();
    ModuleToKORE.convert(sort, sb);
    String quoted = StringUtil.enquoteCString(sb.toString());
    bison.append(quoted.substring(1, quoted.length() - 1));
  }
}
