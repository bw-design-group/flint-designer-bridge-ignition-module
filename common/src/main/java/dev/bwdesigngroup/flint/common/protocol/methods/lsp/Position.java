package dev.bwdesigngroup.flint.common.protocol.methods.lsp;

/** LSP Position: zero-based line and character (UTF-16) offset. */
public class Position {
    public int line;
    public int character;

    public Position() {}

    public Position(int line, int character) {
        this.line = line;
        this.character = character;
    }
}
