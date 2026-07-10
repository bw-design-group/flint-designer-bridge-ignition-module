package dev.bwdesigngroup.flint.gateway.lsp.ws;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bwdesigngroup.flint.common.protocol.methods.lsp.CompletionItem;
import dev.bwdesigngroup.flint.gateway.lsp.FlintLanguageServer;
import dev.bwdesigngroup.flint.gateway.lsp.HintsSource;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LspMessageRouter")
class LspMessageRouterTest {

    private static final Gson GSON = new Gson();

    private ScheduledExecutorService scheduler;
    private RecordingTransport transport;
    private LspMessageRouter router;

    /** Fake system.* hints so completion exercises the real engine without the SDK hint tree. */
    private static final HintsSource FAKE_HINTS =
            new HintsSource() {
                @Override
                public Set<String> rootNames() {
                    return Set.of("system");
                }

                @Override
                public List<CompletionItem> members(String dottedPath, String partial) {
                    if ("system".equals(dottedPath)) {
                        return List.of(new CompletionItem("tag", 9));
                    }
                    return List.of();
                }
            };

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        transport = new RecordingTransport();
        router =
                new LspMessageRouter(
                        new FlintLanguageServer(FAKE_HINTS),
                        "ws-test",
                        GSON,
                        scheduler,
                        "9.9.9",
                        transport);
    }

    @AfterEach
    void tearDown() {
        router.dispose();
        scheduler.shutdownNow();
    }

    @Test
    @DisplayName("initialize returns the advertised capabilities and serverInfo")
    void initializeCapabilities() {
        router.handle(initialize());

        JsonObject result = responseFor(1).getAsJsonObject("result");
        JsonObject capabilities = result.getAsJsonObject("capabilities");
        assertEquals(1, capabilities.get("textDocumentSync").getAsInt());
        assertTrue(capabilities.get("hoverProvider").getAsBoolean());
        assertTrue(capabilities.get("definitionProvider").getAsBoolean());
        assertTrue(capabilities.get("referencesProvider").getAsBoolean());
        assertTrue(capabilities.get("documentSymbolProvider").getAsBoolean());
        assertTrue(capabilities.get("workspaceSymbolProvider").getAsBoolean());
        assertEquals(
                ".",
                capabilities
                        .getAsJsonObject("completionProvider")
                        .getAsJsonArray("triggerCharacters")
                        .get(0)
                        .getAsString());
        JsonObject serverInfo = result.getAsJsonObject("serverInfo");
        assertEquals("flint-gateway-lsp", serverInfo.get("name").getAsString());
        assertEquals("9.9.9", serverInfo.get("version").getAsString());
    }

    @Test
    @DisplayName("preserves integer request ids verbatim (no double coercion)")
    void preservesIntegerId() {
        router.handle(initialize());
        JsonObject response = responseFor(1);
        assertTrue(response.get("id").getAsJsonPrimitive().isNumber());
        assertEquals("1", response.get("id").getAsString());
    }

    @Test
    @DisplayName("a request before initialize is rejected with -32002")
    void requestBeforeInitialize() {
        router.handle(
                request(
                        5,
                        "textDocument/completion",
                        "{\"textDocument\":{\"uri\":\"file:///a.py\"},"
                                + "\"position\":{\"line\":0,\"character\":0}}"));
        assertEquals(-32002, responseFor(5).getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    @DisplayName("didOpen then completion round-trips through the engine")
    void completionRoundTrip() {
        router.handle(initialize());
        router.handle(
                notification(
                        "textDocument/didOpen",
                        "{\"textDocument\":{\"uri\":\"file:///a.py\",\"text\":\"\"}}"));
        router.handle(
                request(
                        2,
                        "textDocument/completion",
                        "{\"textDocument\":{\"uri\":\"file:///a.py\"},"
                                + "\"position\":{\"line\":0,\"character\":0}}"));

        JsonArray items = responseFor(2).getAsJsonArray("result");
        assertFalse(items.isEmpty());
        assertTrue(labels(items).contains("system"), "hint roots should surface in completion");
    }

    @Test
    @DisplayName("documentSymbol round-trips through the engine")
    void documentSymbolRoundTrip() {
        router.handle(initialize());
        router.handle(
                notification(
                        "textDocument/didOpen",
                        "{\"textDocument\":{\"uri\":\"file:///a.py\","
                                + "\"text\":\"def foo():\\n    pass\\n\"}}"));
        router.handle(
                request(
                        3,
                        "textDocument/documentSymbol",
                        "{\"textDocument\":{\"uri\":\"file:///a.py\"}}"));

        JsonArray symbols = responseFor(3).getAsJsonArray("result");
        boolean hasFoo = false;
        for (int i = 0; i < symbols.size(); i++) {
            if ("foo".equals(symbols.get(i).getAsJsonObject().get("name").getAsString())) {
                hasFoo = true;
            }
        }
        assertTrue(hasFoo, "expected the 'foo' function symbol");
    }

    @Test
    @DisplayName("didChange on a syntax error pushes publishDiagnostics")
    void pushesDiagnostics() throws Exception {
        router.handle(initialize());
        router.handle(
                notification(
                        "textDocument/didOpen",
                        "{\"textDocument\":{\"uri\":\"file:///bad.py\",\"text\":\"def (\"}}"));

        JsonObject push = awaitMessage("textDocument/publishDiagnostics");
        assertNotNull(push, "expected a diagnostics push");
        JsonObject params = push.getAsJsonObject("params");
        assertEquals("file:///bad.py", params.get("uri").getAsString());
        assertFalse(params.getAsJsonArray("diagnostics").isEmpty());
    }

    @Test
    @DisplayName("shutdown returns null and exit closes the socket")
    void shutdownAndExit() {
        router.handle(initialize());
        router.handle(request(9, "shutdown", "{}"));
        assertTrue(responseFor(9).get("result").isJsonNull());

        router.handle(notification("exit", "{}"));
        assertEquals(1000, transport.closeCode);
    }

    @Test
    @DisplayName("an unknown request yields -32601")
    void unknownMethod() {
        router.handle(initialize());
        router.handle(request(7, "textDocument/foldingRange", "{}"));
        assertEquals(-32601, responseFor(7).getAsJsonObject("error").get("code").getAsInt());
    }

    // ==================== helpers ====================

    private static String initialize() {
        return request(1, "initialize", "{\"rootUri\":\"file:///proj\"}");
    }

    private static String request(int id, String method, String params) {
        return "{\"jsonrpc\":\"2.0\",\"id\":"
                + id
                + ",\"method\":\""
                + method
                + "\",\"params\":"
                + params
                + "}";
    }

    private static String notification(String method, String params) {
        return "{\"jsonrpc\":\"2.0\",\"method\":\"" + method + "\",\"params\":" + params + "}";
    }

    private JsonObject responseFor(int id) {
        for (String raw : transport.sent) {
            JsonObject message = JsonParser.parseString(raw).getAsJsonObject();
            if (message.has("id")
                    && message.get("id").isJsonPrimitive()
                    && message.get("id").getAsJsonPrimitive().isNumber()
                    && message.get("id").getAsInt() == id) {
                return message;
            }
        }
        throw new AssertionError("No response for id " + id + " in " + transport.sent);
    }

    private JsonObject awaitMessage(String method) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            for (String raw : transport.sent) {
                JsonObject message = JsonParser.parseString(raw).getAsJsonObject();
                if (message.has("method") && method.equals(message.get("method").getAsString())) {
                    return message;
                }
            }
            Thread.sleep(20);
        }
        return null;
    }

    private static Set<String> labels(JsonArray items) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            JsonObject item = items.get(i).getAsJsonObject();
            if (item.has("label")) {
                out.add(item.get("label").getAsString());
            }
        }
        return out;
    }

    private static final class RecordingTransport implements LspMessageRouter.Transport {
        final List<String> sent = new CopyOnWriteArrayList<>();
        volatile int closeCode = -1;
        volatile String closeReason;

        @Override
        public void send(String jsonMessage) {
            sent.add(jsonMessage);
        }

        @Override
        public void close(int statusCode, String reason) {
            this.closeCode = statusCode;
            this.closeReason = reason;
        }
    }
}
