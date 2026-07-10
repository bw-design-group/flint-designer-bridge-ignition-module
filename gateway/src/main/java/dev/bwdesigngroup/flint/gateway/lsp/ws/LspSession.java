package dev.bwdesigngroup.flint.gateway.lsp.ws;

import com.google.gson.Gson;
import dev.bwdesigngroup.flint.gateway.lsp.FlintLanguageServer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One live LSP-over-WebSocket connection. Owns the inbound {@link LspFrameCodec} and the
 * per-session {@link LspMessageRouter}, and reuses {@link FlintLanguageServer}'s {@code
 * sessionId::uri} keying so a single gateway serves many concurrent editors independently.
 *
 * <p>Teardown funnels through {@link #onClose()} (error and normal close both call it) and is
 * idempotent: it disposes router state (closing every still-open document, cancelling pending
 * diagnostics) and deregisters from the bridge exactly once.
 */
public final class LspSession {

    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Lsp.Ws");

    private final String id = "ws-" + UUID.randomUUID();
    private final MessageSink sink;
    private final LspFrameCodec codec = new LspFrameCodec();
    private final LspMessageRouter router;
    private final Consumer<String> deregister;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    LspSession(
            FlintLanguageServer engine,
            Gson gson,
            ScheduledExecutorService scheduler,
            String serverVersion,
            MessageSink sink,
            Consumer<String> deregister) {
        this.sink = sink;
        this.deregister = deregister;
        this.router =
                new LspMessageRouter(
                        engine,
                        id,
                        gson,
                        scheduler,
                        serverVersion,
                        new LspMessageRouter.Transport() {
                            @Override
                            public void send(String jsonMessage) {
                                emit(jsonMessage);
                            }

                            @Override
                            public void close(int statusCode, String reason) {
                                LspSession.this.close(statusCode, reason);
                            }
                        });
    }

    public String getId() {
        return id;
    }

    /** Feeds an inbound WebSocket text frame through the codec and router. */
    public void onText(String text) {
        route(() -> codec.decode(text));
    }

    /**
     * Feeds an inbound WebSocket binary frame through the codec and router. Clients using {@code
     * ws}'s {@code createWebSocketStream} duplex (the VS Code transport) send LSP as binary frames,
     * so this path is as important as {@link #onText(String)}.
     */
    public void onBinary(byte[] bytes) {
        route(() -> codec.decode(bytes));
    }

    private void route(java.util.function.Supplier<List<String>> decode) {
        List<String> messages;
        try {
            messages = decode.get();
        } catch (RuntimeException e) {
            logger.warn("Closing LSP session {}: {}", id, e.getMessage());
            close(1009, "Message too large"); // 1009 = WS message too big
            return;
        }
        for (String message : messages) {
            router.handle(message);
        }
    }

    /** Normal or error-driven close from the transport. Idempotent. */
    public void onClose() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        router.dispose();
        deregister.accept(id);
    }

    /** Server-initiated close (LSP {@code exit} or gateway shutdown), then local teardown. */
    public void close(int statusCode, String reason) {
        if (sink.isOpen()) {
            sink.close(statusCode, reason);
        }
        onClose();
    }

    private void emit(String jsonMessage) {
        if (sink.isOpen()) {
            sink.send(codec.encode(jsonMessage));
        }
    }
}
