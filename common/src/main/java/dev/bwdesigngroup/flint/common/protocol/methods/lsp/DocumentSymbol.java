package dev.bwdesigngroup.flint.common.protocol.methods.lsp;

import java.util.ArrayList;
import java.util.List;

/** LSP DocumentSymbol: hierarchical outline node. */
public class DocumentSymbol {
    /** LSP SymbolKind: 5=Class, 6=Method, 12=Function, 13=Variable, 14=Constant, 2=Module. */
    public static final int KIND_MODULE = 2;

    public static final int KIND_CLASS = 5;
    public static final int KIND_METHOD = 6;
    public static final int KIND_FUNCTION = 12;
    public static final int KIND_VARIABLE = 13;
    public static final int KIND_CONSTANT = 14;

    public String name;
    public int kind;
    public String detail;
    public Range range;
    public Range selectionRange;
    public List<DocumentSymbol> children = new ArrayList<>();

    public DocumentSymbol() {}

    public DocumentSymbol(String name, int kind, Range range, Range selectionRange) {
        this.name = name;
        this.kind = kind;
        this.range = range;
        this.selectionRange = selectionRange;
    }
}
