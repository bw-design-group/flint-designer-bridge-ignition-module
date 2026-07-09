package dev.bwdesigngroup.flint.common.protocol.methods.lsp;

/** LSP WorkspaceSymbol / SymbolInformation: a named symbol with its location and container. */
public class WorkspaceSymbol {
    public String name;
    public int kind;
    public String containerName;
    public Location location;

    public WorkspaceSymbol() {}

    public WorkspaceSymbol(String name, int kind, String containerName, Location location) {
        this.name = name;
        this.kind = kind;
        this.containerName = containerName;
        this.location = location;
    }
}
