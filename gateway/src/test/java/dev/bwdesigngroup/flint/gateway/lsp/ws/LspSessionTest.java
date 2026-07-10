package dev.bwdesigngroup.flint.gateway.lsp.ws;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import dev.bwdesigngroup.flint.gateway.auth.GatewayAuthenticator;
import dev.bwdesigngroup.flint.gateway.lsp.FlintLanguageServer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LspSession")
class LspSessionTest {

    private static final String URI = "file:///a.py";

    @Nested
    @DisplayName("diagnostics debounce")
    class Debounce {

        @Test
        @DisplayName("a rapid didChange cancels the pending diagnostics future and reschedules")
        void coalescesDiagnostics() {
            ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            doReturn(future).when(scheduler).schedule(any(Runnable.class), anyLong(), any());

            LspSession session =
                    new LspSession(
                            new FlintLanguageServer(),
                            new Gson(),
                            scheduler,
                            "1",
                            new FakeSink(),
                            id -> {});

            session.onText(initialize());
            session.onText(didOpen("x = 1"));
            session.onText(didChange("x = 2"));

            verify(scheduler, times(2))
                    .schedule(
                            any(Runnable.class),
                            eq(LspMessageRouter.DIAGNOSTICS_DEBOUNCE_MS),
                            eq(TimeUnit.MILLISECONDS));
            verify(future, times(1)).cancel(false);
        }
    }

    @Nested
    @DisplayName("inbound frames")
    class InboundFrames {

        @Test
        @DisplayName("a Content-Length initialize as a TEXT frame gets a response")
        void textFrameResponds() {
            FakeSink sink = respondTo(session -> session.onText(framed(initialize())));
            assertResponded(sink);
        }

        @Test
        @DisplayName("the identical initialize as a BINARY frame gets the same response")
        void binaryFrameResponds() {
            FakeSink sink =
                    respondTo(
                            session ->
                                    session.onBinary(
                                            framed(initialize())
                                                    .getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8)));
            assertResponded(sink);
        }

        private FakeSink respondTo(java.util.function.Consumer<LspSession> feed) {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            FakeSink sink = new FakeSink();
            try {
                LspSession session =
                        new LspSession(
                                new FlintLanguageServer(),
                                new Gson(),
                                scheduler,
                                "1",
                                sink,
                                id -> {});
                feed.accept(session);
            } finally {
                scheduler.shutdownNow();
            }
            return sink;
        }

        private void assertResponded(FakeSink sink) {
            assertEquals(1, sink.sent.size(), "expected exactly one response frame");
            assertTrue(
                    sink.sent.get(0).contains("flint-gateway-lsp"),
                    "response should carry the initialize result: " + sink.sent);
        }
    }

    @Nested
    @DisplayName("disconnect")
    class Disconnect {

        @Test
        @DisplayName("onClose releases the engine's document state (idempotent)")
        void cleansEngineState() {
            FlintLanguageServer engine = new FlintLanguageServer();
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            try {
                LspSession session =
                        new LspSession(
                                engine, new Gson(), scheduler, "1", new FakeSink(), id -> {});
                session.onText(initialize());
                session.onText(didOpen("x = 1"));
                assertNotNull(engine.getText(session.getId(), URI));

                session.onClose();
                assertNull(engine.getText(session.getId(), URI));
                assertDoesNotThrow(session::onClose); // idempotent
            } finally {
                scheduler.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("bridge lifecycle")
    class BridgeLifecycle {

        @Test
        @DisplayName("closing a session empties the bridge's session map")
        void emptiesMapOnClose() {
            LspWebSocketBridge bridge = newBridge();
            LspSession session = bridge.openSession(new FakeSink());
            assertEquals(1, bridge.activeSessionCount());

            session.onClose();
            assertEquals(0, bridge.activeSessionCount());
            bridge.shutdown();
        }

        @Test
        @DisplayName("shutdown closes every live session with WS 1001 and empties the map")
        void shutdownClosesSessions() {
            LspWebSocketBridge bridge = newBridge();
            FakeSink sink = new FakeSink();
            bridge.openSession(sink);

            bridge.shutdown();

            assertEquals(0, bridge.activeSessionCount());
            assertEquals(1001, sink.closeCode);
            assertFalse(sink.isOpen());
        }

        private LspWebSocketBridge newBridge() {
            GatewayAuthenticator authenticator = ctx -> GatewayAuthenticator.AuthResult.ok("test");
            return new LspWebSocketBridge(
                    new FlintLanguageServer(), authenticator, new Gson(), "1");
        }
    }

    // ==================== helpers ====================

    private static String initialize() {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}";
    }

    /** Wraps a JSON message in LSP Content-Length framing (byte-accurate). */
    private static String framed(String json) {
        int length = json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        return "Content-Length: " + length + "\r\n\r\n" + json;
    }

    private static String didOpen(String text) {
        return "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":"
                + "{\"textDocument\":{\"uri\":\""
                + URI
                + "\",\"text\":\""
                + text
                + "\"}}}";
    }

    private static String didChange(String text) {
        return "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didChange\",\"params\":"
                + "{\"textDocument\":{\"uri\":\""
                + URI
                + "\"},\"contentChanges\":[{\"text\":\""
                + text
                + "\"}]}}";
    }

    private static final class FakeSink implements MessageSink {
        final List<String> sent = new CopyOnWriteArrayList<>();
        volatile boolean open = true;
        volatile int closeCode = -1;

        @Override
        public void send(String message) {
            sent.add(message);
        }

        @Override
        public void close(int statusCode, String reason) {
            this.closeCode = statusCode;
            this.open = false;
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }
}
