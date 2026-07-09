package dev.bwdesigngroup.flint.gateway.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.CompletionResult;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Hover;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Location;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Position;
import dev.bwdesigngroup.flint.gateway.lsp.FlintLanguageServer;
import java.util.HashMap;
import java.util.Map;

/**
 * Dispatch extension routing headless LSP methods ({@code lsp.*}) to {@link FlintLanguageServer}.
 * Document-sync methods are treated as notifications by the transport (the dispatcher suppresses
 * responses when the request has no id); feature methods return LSP-shaped results.
 */
public class LspDispatchExtension implements GatewayRpcDispatcher.DispatchExtension {

    private final FlintLanguageServer server;

    public LspDispatchExtension(FlintLanguageServer server) {
        this.server = server;
    }

    @Override
    public JsonRpcResponse tryDispatch(JsonRpcRequest request) {
        String method = request.getMethod();
        Object id = request.getId();
        JsonObject params = params(request);

        switch (method) {
            case FlintConstants.METHOD_LSP_DID_OPEN:
                {
                    server.didOpen(
                            str(params, "sessionId"), str(params, "uri"), str(params, "text"));
                    return ok(id);
                }
            case FlintConstants.METHOD_LSP_DID_CHANGE:
                {
                    server.didChange(
                            str(params, "sessionId"), str(params, "uri"), str(params, "text"));
                    return ok(id);
                }
            case FlintConstants.METHOD_LSP_DID_CLOSE:
                {
                    server.didClose(str(params, "sessionId"), str(params, "uri"));
                    return ok(id);
                }
            case FlintConstants.METHOD_LSP_DIAGNOSTICS:
                {
                    String uri = str(params, "uri");
                    if (uri == null) {
                        return missing(id, "uri");
                    }
                    Map<String, Object> result = new HashMap<>();
                    result.put("uri", uri);
                    if (params.has("text")) {
                        result.put("diagnostics", server.diagnosticsFor(uri, str(params, "text")));
                    } else {
                        result.put(
                                "diagnostics", server.diagnostics(str(params, "sessionId"), uri));
                    }
                    return JsonRpcResponse.success(result, id);
                }
            case FlintConstants.METHOD_LSP_DOCUMENT_SYMBOL:
                {
                    String uri = str(params, "uri");
                    if (uri == null) {
                        return missing(id, "uri");
                    }
                    Map<String, Object> result = new HashMap<>();
                    result.put("uri", uri);
                    result.put(
                            "symbols",
                            server.documentSymbols(
                                    str(params, "sessionId"), uri, str(params, "text")));
                    return JsonRpcResponse.success(result, id);
                }
            case FlintConstants.METHOD_LSP_HOVER:
                {
                    String uri = str(params, "uri");
                    Position pos = position(params);
                    if (uri == null || pos == null) {
                        return missing(id, "uri, position");
                    }
                    Hover hover =
                            server.hover(str(params, "sessionId"), uri, str(params, "text"), pos);
                    return JsonRpcResponse.success(hover, id); // null result => no hover
                }
            case FlintConstants.METHOD_LSP_DEFINITION:
                {
                    String uri = str(params, "uri");
                    Position pos = position(params);
                    if (uri == null || pos == null) {
                        return missing(id, "uri, position");
                    }
                    Location loc =
                            server.definition(
                                    str(params, "sessionId"),
                                    uri,
                                    str(params, "text"),
                                    pos,
                                    str(params, "project"),
                                    str(params, "rootUri"));
                    return JsonRpcResponse.success(loc, id); // null => no definition
                }
            case FlintConstants.METHOD_LSP_REFERENCES:
                {
                    String uri = str(params, "uri");
                    Position pos = position(params);
                    if (uri == null || pos == null) {
                        return missing(id, "uri, position");
                    }
                    boolean includeDecl = bool(params, "includeDeclaration", true);
                    Map<String, Object> result = new HashMap<>();
                    result.put(
                            "references",
                            server.references(
                                    str(params, "sessionId"),
                                    uri,
                                    str(params, "text"),
                                    pos,
                                    includeDecl));
                    return JsonRpcResponse.success(result, id);
                }
            case FlintConstants.METHOD_LSP_COMPLETION:
                {
                    String uri = str(params, "uri");
                    Position pos = position(params);
                    if (uri == null || pos == null) {
                        return missing(id, "uri, position");
                    }
                    CompletionResult result = new CompletionResult();
                    result.setItems(
                            server.completion(
                                    str(params, "sessionId"),
                                    uri,
                                    str(params, "text"),
                                    pos,
                                    str(params, "project")));
                    return JsonRpcResponse.success(result, id);
                }
            case FlintConstants.METHOD_LSP_WORKSPACE_SYMBOL:
                {
                    Map<String, Object> result = new HashMap<>();
                    result.put(
                            "symbols",
                            server.workspaceSymbols(
                                    str(params, "project"),
                                    str(params, "query"),
                                    str(params, "rootUri")));
                    return JsonRpcResponse.success(result, id);
                }
            case FlintConstants.METHOD_LSP_REINDEX:
                {
                    server.invalidateProjectIndex(str(params, "project"));
                    return ok(id);
                }
            default:
                return null; // not an LSP method handled here
        }
    }

    private JsonRpcResponse ok(Object id) {
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return JsonRpcResponse.success(result, id);
    }

    private JsonRpcResponse missing(Object id, String param) {
        return JsonRpcResponse.error(
                ErrorCodes.INVALID_PARAMS, "Missing required parameter: " + param, id);
    }

    private JsonObject params(JsonRpcRequest request) {
        JsonElement paramsElement = request.getParams();
        if (paramsElement != null && paramsElement.isJsonObject()) {
            return paramsElement.getAsJsonObject();
        }
        return new JsonObject();
    }

    private String str(JsonObject params, String key) {
        if (params.has(key) && params.get(key).isJsonPrimitive()) {
            return params.get(key).getAsString();
        }
        return null;
    }

    private boolean bool(JsonObject params, String key, boolean def) {
        if (params.has(key) && params.get(key).isJsonPrimitive()) {
            try {
                return params.get(key).getAsBoolean();
            } catch (Exception ignored) {
                return def;
            }
        }
        return def;
    }

    /** Accepts either a nested {@code position:{line,character}} or top-level line/character. */
    private Position position(JsonObject params) {
        JsonObject p = params;
        if (params.has("position") && params.get("position").isJsonObject()) {
            p = params.getAsJsonObject("position");
        }
        if (!p.has("line") || !p.has("character")) {
            return null;
        }
        try {
            return new Position(p.get("line").getAsInt(), p.get("character").getAsInt());
        } catch (Exception e) {
            return null;
        }
    }
}
