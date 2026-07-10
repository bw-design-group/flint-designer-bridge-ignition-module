package dev.bwdesigngroup.flint.gateway.lsp.ws;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Hover;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Location;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.LspDiagnostic;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.Position;
import dev.bwdesigngroup.flint.gateway.lsp.FlintLanguageServer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps raw Language Server Protocol messages onto {@link FlintLanguageServer}, one instance per
 * connection. Requests get JSON-RPC responses through the supplied {@link Transport}; document-sync
 * notifications drive debounced {@code textDocument/publishDiagnostics} pushes over the same
 * transport.
 *
 * <p>The router parses messages as raw {@code JsonObject}s (rather than binding to a typed request)
 * so it can echo the client's {@code id} verbatim — preserving integer vs. string identity, which a
 * Gson {@code Object} round-trip would coerce to a double and break LSP clients.
 */
public final class LspMessageRouter {

    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Lsp.Ws");

    /** LSP {@code ServerNotInitialized}. Numerically distinct from the JSON-RPC standard codes. */
    private static final int SERVER_NOT_INITIALIZED = -32002;

    /** LSP {@code TextDocumentSyncKind.Full}. */
    private static final int SYNC_FULL = 1;

    /** Per-URI diagnostics debounce window. */
    static final long DIAGNOSTICS_DEBOUNCE_MS = 250;

    /** Outbound channel from the router: frame-and-send JSON, or close the socket. */
    public interface Transport {
        void send(String jsonMessage);

        void close(int statusCode, String reason);
    }

    private final FlintLanguageServer engine;
    private final String sessionId;
    private final Gson gson;

    /**
     * Serializes outbound envelopes with explicit nulls, so a {@code result: null} response (hover
     * with no result, {@code shutdown}, etc.) keeps its {@code result} member — the default Gson
     * silently drops a {@code JsonNull} member and would produce a malformed JSON-RPC response.
     */
    private final Gson outputGson = new GsonBuilder().serializeNulls().create();

    private final ScheduledExecutorService scheduler;
    private final String serverVersion;
    private final Transport transport;

    private final Set<String> openUris = ConcurrentHashMap.newKeySet();
    private final Map<String, ScheduledFuture<?>> pendingDiagnostics = new ConcurrentHashMap<>();

    private volatile boolean initialized;
    private volatile String project;
    private volatile String rootUri;

    public LspMessageRouter(
            FlintLanguageServer engine,
            String sessionId,
            Gson gson,
            ScheduledExecutorService scheduler,
            String serverVersion,
            Transport transport) {
        this.engine = engine;
        this.sessionId = sessionId;
        this.gson = gson;
        this.scheduler = scheduler;
        this.serverVersion = serverVersion;
        this.transport = transport;
    }

    /** Routes one raw LSP message (JSON string). Never throws; failures become JSON-RPC errors. */
    public void handle(String rawJson) {
        JsonObject message;
        try {
            message = JsonParser.parseString(rawJson).getAsJsonObject();
        } catch (Exception e) {
            respondError(
                    JsonNull.INSTANCE, ErrorCodes.PARSE_ERROR, "Parse error: " + e.getMessage());
            return;
        }

        String method = message.has("method") ? asString(message.get("method")) : null;
        JsonElement id = message.has("id") ? message.get("id") : null;
        boolean isRequest = id != null && !id.isJsonNull();
        if (method == null) {
            if (isRequest) {
                respondError(id, ErrorCodes.INVALID_REQUEST, "Missing method");
            }
            return;
        }
        JsonObject params = paramsOf(message);

        try {
            dispatch(method, id, isRequest, params);
        } catch (Exception e) {
            logger.warn("Error handling LSP method {}: {}", method, e.getMessage(), e);
            if (isRequest) {
                respondError(id, ErrorCodes.INTERNAL_ERROR, "Internal error: " + e.getMessage());
            }
        }
    }

    private void dispatch(String method, JsonElement id, boolean isRequest, JsonObject params) {
        // Everything but the lifecycle handshake requires initialize first.
        if (!initialized && !"initialize".equals(method) && !"exit".equals(method)) {
            if (isRequest) {
                respondError(id, SERVER_NOT_INITIALIZED, "Server not initialized");
            }
            return;
        }

        switch (method) {
            case "initialize":
                handleInitialize(id, params);
                return;
            case "initialized":
            case "$/cancelRequest":
            case "$/setTrace":
                return; // no-op notifications
            case "shutdown":
                respond(id, null);
                return;
            case "exit":
                transport.close(1000, "exit");
                return;
            case "textDocument/didOpen":
                handleDidOpen(params);
                return;
            case "textDocument/didChange":
                handleDidChange(params);
                return;
            case "textDocument/didClose":
                handleDidClose(params);
                return;
            case "textDocument/completion":
                handleCompletion(id, params);
                return;
            case "textDocument/hover":
                handleHover(id, params);
                return;
            case "textDocument/definition":
                handleDefinition(id, params);
                return;
            case "textDocument/references":
                handleReferences(id, params);
                return;
            case "textDocument/documentSymbol":
                handleDocumentSymbol(id, params);
                return;
            case "workspace/symbol":
                handleWorkspaceSymbol(id, params);
                return;
            default:
                if (isRequest) {
                    respondError(id, ErrorCodes.METHOD_NOT_FOUND, "Method not found: " + method);
                }
        }
    }

    // ==================== Lifecycle ====================

    private void handleInitialize(JsonElement id, JsonObject params) {
        JsonObject options = childObject(params, "initializationOptions");
        if (options != null) {
            project = asString(options.get("project"));
        }
        rootUri = asString(params.get("rootUri"));
        if (rootUri == null
                && params.has("workspaceFolders")
                && params.get("workspaceFolders").isJsonArray()
                && params.getAsJsonArray("workspaceFolders").size() > 0) {
            JsonElement first = params.getAsJsonArray("workspaceFolders").get(0);
            if (first.isJsonObject()) {
                rootUri = asString(first.getAsJsonObject().get("uri"));
            }
        }
        initialized = true;
        respond(id, buildInitializeResult());
    }

    private Map<String, Object> buildInitializeResult() {
        Map<String, Object> completionProvider = new HashMap<>();
        completionProvider.put("triggerCharacters", new String[] {"."});

        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("textDocumentSync", SYNC_FULL);
        capabilities.put("completionProvider", completionProvider);
        capabilities.put("hoverProvider", true);
        capabilities.put("definitionProvider", true);
        capabilities.put("referencesProvider", true);
        capabilities.put("documentSymbolProvider", true);
        capabilities.put("workspaceSymbolProvider", true);

        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", "flint-gateway-lsp");
        serverInfo.put("version", serverVersion);

        Map<String, Object> result = new HashMap<>();
        result.put("capabilities", capabilities);
        result.put("serverInfo", serverInfo);
        return result;
    }

    // ==================== Document sync ====================

    private void handleDidOpen(JsonObject params) {
        JsonObject doc = childObject(params, "textDocument");
        if (doc == null) {
            return;
        }
        String uri = asString(doc.get("uri"));
        if (uri == null) {
            return;
        }
        engine.didOpen(sessionId, uri, asString(doc.get("text")));
        openUris.add(uri);
        scheduleDiagnostics(uri);
    }

    private void handleDidChange(JsonObject params) {
        JsonObject doc = childObject(params, "textDocument");
        if (doc == null) {
            return;
        }
        String uri = asString(doc.get("uri"));
        if (uri == null) {
            return;
        }
        // Full sync: the last content change carries the whole document text.
        String text = null;
        if (params.has("contentChanges") && params.get("contentChanges").isJsonArray()) {
            int size = params.getAsJsonArray("contentChanges").size();
            if (size > 0) {
                JsonElement last = params.getAsJsonArray("contentChanges").get(size - 1);
                if (last.isJsonObject()) {
                    text = asString(last.getAsJsonObject().get("text"));
                }
            }
        }
        engine.didChange(sessionId, uri, text);
        openUris.add(uri);
        scheduleDiagnostics(uri);
    }

    private void handleDidClose(JsonObject params) {
        JsonObject doc = childObject(params, "textDocument");
        if (doc == null) {
            return;
        }
        String uri = asString(doc.get("uri"));
        if (uri == null) {
            return;
        }
        cancelDiagnostics(uri);
        engine.didClose(sessionId, uri);
        openUris.remove(uri);
        publishDiagnostics(uri, List.of()); // clear markers on close (mirrors the legacy proxy)
    }

    // ==================== Feature requests ====================

    private void handleCompletion(JsonElement id, JsonObject params) {
        String uri = documentUri(params);
        Position pos = position(params);
        if (uri == null || pos == null) {
            respond(id, List.of());
            return;
        }
        respond(id, engine.completion(sessionId, uri, null, pos, project));
    }

    private void handleHover(JsonElement id, JsonObject params) {
        String uri = documentUri(params);
        Position pos = position(params);
        if (uri == null || pos == null) {
            respond(id, null);
            return;
        }
        Hover hover = engine.hover(sessionId, uri, null, pos);
        if (hover == null || hover.value == null || hover.value.isEmpty()) {
            respond(id, null);
            return;
        }
        Map<String, Object> contents = new HashMap<>();
        contents.put("kind", "markdown");
        contents.put("value", hover.value);
        Map<String, Object> result = new HashMap<>();
        result.put("contents", contents);
        if (hover.range != null) {
            result.put("range", hover.range);
        }
        respond(id, result);
    }

    private void handleDefinition(JsonElement id, JsonObject params) {
        String uri = documentUri(params);
        Position pos = position(params);
        if (uri == null || pos == null) {
            respond(id, null);
            return;
        }
        Location loc = engine.definition(sessionId, uri, null, pos, project, rootUri);
        if (loc == null || loc.uri == null || loc.uri.isEmpty()) {
            respond(id, null);
            return;
        }
        respond(id, loc);
    }

    private void handleReferences(JsonElement id, JsonObject params) {
        String uri = documentUri(params);
        Position pos = position(params);
        if (uri == null || pos == null) {
            respond(id, List.of());
            return;
        }
        boolean includeDecl = true;
        JsonObject context = childObject(params, "context");
        if (context != null && context.has("includeDeclaration")) {
            includeDecl = asBoolean(context.get("includeDeclaration"), true);
        }
        respond(id, engine.references(sessionId, uri, null, pos, includeDecl));
    }

    private void handleDocumentSymbol(JsonElement id, JsonObject params) {
        String uri = documentUri(params);
        if (uri == null) {
            respond(id, List.of());
            return;
        }
        respond(id, engine.documentSymbols(sessionId, uri, null));
    }

    private void handleWorkspaceSymbol(JsonElement id, JsonObject params) {
        String query = asString(params.get("query"));
        respond(id, engine.workspaceSymbols(project, query, rootUri));
    }

    // ==================== Diagnostics ====================

    private void scheduleDiagnostics(String uri) {
        cancelDiagnostics(uri);
        ScheduledFuture<?> future =
                scheduler.schedule(
                        () -> {
                            pendingDiagnostics.remove(uri);
                            try {
                                List<LspDiagnostic> diagnostics =
                                        engine.diagnostics(sessionId, uri);
                                publishDiagnostics(uri, diagnostics);
                            } catch (Exception e) {
                                logger.debug("Diagnostics for {} failed: {}", uri, e.getMessage());
                            }
                        },
                        DIAGNOSTICS_DEBOUNCE_MS,
                        TimeUnit.MILLISECONDS);
        pendingDiagnostics.put(uri, future);
    }

    private void cancelDiagnostics(String uri) {
        ScheduledFuture<?> previous = pendingDiagnostics.remove(uri);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    private void publishDiagnostics(String uri, List<LspDiagnostic> diagnostics) {
        JsonObject params = new JsonObject();
        params.addProperty("uri", uri);
        params.add("diagnostics", gson.toJsonTree(diagnostics));
        JsonObject message = new JsonObject();
        message.addProperty("jsonrpc", "2.0");
        message.addProperty("method", "textDocument/publishDiagnostics");
        message.add("params", params);
        transport.send(outputGson.toJson(message));
    }

    /** Releases engine state and cancels pending work when the connection ends (idempotent). */
    public void dispose() {
        for (ScheduledFuture<?> future : pendingDiagnostics.values()) {
            future.cancel(false);
        }
        pendingDiagnostics.clear();
        for (String uri : openUris) {
            engine.didClose(sessionId, uri);
        }
        openUris.clear();
    }

    // ==================== JSON helpers ====================

    private void respond(JsonElement id, Object result) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("result", result == null ? JsonNull.INSTANCE : gson.toJsonTree(result));
        response.add("id", id == null ? JsonNull.INSTANCE : id);
        transport.send(outputGson.toJson(response));
    }

    private void respondError(JsonElement id, int code, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("error", error);
        response.add("id", id == null ? JsonNull.INSTANCE : id);
        transport.send(outputGson.toJson(response));
    }

    private JsonObject paramsOf(JsonObject message) {
        if (message.has("params") && message.get("params").isJsonObject()) {
            return message.getAsJsonObject("params");
        }
        return new JsonObject();
    }

    private String documentUri(JsonObject params) {
        JsonObject doc = childObject(params, "textDocument");
        return doc != null ? asString(doc.get("uri")) : null;
    }

    private Position position(JsonObject params) {
        JsonObject pos = childObject(params, "position");
        if (pos == null || !pos.has("line") || !pos.has("character")) {
            return null;
        }
        try {
            return new Position(pos.get("line").getAsInt(), pos.get("character").getAsInt());
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonObject childObject(JsonObject parent, String key) {
        if (parent.has(key) && parent.get(key).isJsonObject()) {
            return parent.getAsJsonObject(key);
        }
        return null;
    }

    private static String asString(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) {
            return element.getAsString();
        }
        return null;
    }

    private static boolean asBoolean(JsonElement element, boolean fallback) {
        if (element != null && element.isJsonPrimitive()) {
            try {
                return element.getAsBoolean();
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
