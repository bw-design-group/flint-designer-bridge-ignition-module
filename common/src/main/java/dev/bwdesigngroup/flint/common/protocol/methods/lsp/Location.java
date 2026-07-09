package dev.bwdesigngroup.flint.common.protocol.methods.lsp;

/** LSP Location: a range within a document identified by URI. */
public class Location {
    public String uri;
    public Range range;

    public Location() {}

    public Location(String uri, Range range) {
        this.uri = uri;
        this.range = range;
    }
}
