package dev.bwdesigngroup.flint.common.protocol.methods.lsp;

/**
 * LSP Hover result. {@code value} is markdown; the transport proxy wraps it as {@code
 * {contents:{kind:"markdown", value}, range}} for the editor.
 */
public class Hover {
    public String value;
    public Range range;

    public Hover() {}

    public Hover(String value, Range range) {
        this.value = value;
        this.range = range;
    }
}
