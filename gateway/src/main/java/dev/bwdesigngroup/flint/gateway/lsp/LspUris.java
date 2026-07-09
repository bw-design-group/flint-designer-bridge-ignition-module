package dev.bwdesigngroup.flint.gateway.lsp;

/**
 * Maps a project script resource path to a document URI. When the editor's workspace root is known
 * (LSP {@code rootUri}, forwarded by the proxy), produces a real {@code file://} URI under
 * Ignition's on-disk layout so go-to-definition opens the file; otherwise a {@code flint://} URI
 * that agents can still use.
 */
public final class LspUris {

    private LspUris() {}

    public static String scriptUri(String rootUri, String project, String resourcePath) {
        String rel = "ignition/script-python/" + resourcePath + "/code.py";
        if (rootUri != null && !rootUri.isEmpty()) {
            String base =
                    rootUri.endsWith("/") ? rootUri.substring(0, rootUri.length() - 1) : rootUri;
            return base + "/" + rel;
        }
        return "flint://" + (project != null ? project : "project") + "/" + rel;
    }
}
