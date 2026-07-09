package dev.bwdesigngroup.flint.common.protocol.methods.lsp;

/** LSP Range: a span from start to end position. */
public class Range {
    public Position start;
    public Position end;

    public Range() {}

    public Range(Position start, Position end) {
        this.start = start;
        this.end = end;
    }

    public static Range of(int startLine, int startChar, int endLine, int endChar) {
        return new Range(new Position(startLine, startChar), new Position(endLine, endChar));
    }
}
