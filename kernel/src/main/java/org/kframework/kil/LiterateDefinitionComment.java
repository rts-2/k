// Copyright (c) 2012-2019 K Team. All Rights Reserved.
package org.kframework.kil;

public class LiterateDefinitionComment extends DefinitionItem implements LiterateComment {
    private String value;
    private LiterateCommentType lcType = LiterateCommentType.COMMON;

    public LiterateDefinitionComment(String value, LiterateCommentType lcType) {
        this.value = value;
        this.lcType = lcType;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public LiterateCommentType getType() {
        return lcType;
    }

    @Override
    public String toString() {
        return "/*"+lcType+value+"*/";
    }
}
