module A-LEMMAS
imports A

rule (X +Int Y) +Int Z => Z +Int (X +Int Y) [simplification, symbolic(X), concrete(Y, Z)]

rule (X +Int Y) +Int Z => Y +Int (X +Int Z) [simplification, concrete(X), symbolic(Y), concrete(Z)]

rule X +Int (Y +Int Z) => Z +Int (X +Int Y) [simplification, concrete(X, Y), symbolic(Z)]

rule X +Int (Y +Int Z) => Y +Int (X +Int Z) [simplification, concrete(X), symbolic(Y), concrete(Z)]

rule final(n +Int M:Int) => 0 [simplification, concrete(M)]

endmodule

module A1-SPEC
imports A-LEMMAS

rule [s1]:
  <k> run(5) => .K </k> <counter> n => 0 </counter>

endmodule
