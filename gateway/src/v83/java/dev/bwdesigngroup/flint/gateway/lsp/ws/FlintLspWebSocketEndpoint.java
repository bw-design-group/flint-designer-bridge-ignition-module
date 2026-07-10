package dev.bwdesigngroup.flint.gateway.lsp.ws;

import java.nio.ByteBuffer;
import java.time.Duration;
import org.eclipse.jetty.websocket.api.Callback;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ignition 8.3 (Jetty 12 ee10, {@code jakarta}) WebSocket endpoint. Bridges Jetty's {@link
 * Session.Listener.AutoDemanding} callbacks to a shared {@link LspSession} and exposes the socket
 * back to the router as a {@link MessageSink}. One instance per connection.
 */
public class FlintLspWebSocketEndpoint implements Session.Listener.AutoDemanding, MessageSink {

    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Lsp.Ws");
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(5);

    private final LspWebSocketBridge bridge;
    private volatile Session session;
    private volatile LspSession lspSession;

    public FlintLspWebSocketEndpoint(LspWebSocketBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void onWebSocketOpen(Session session) {
        this.session = session;
        session.setIdleTimeout(IDLE_TIMEOUT);
        this.lspSession = bridge.openSession(this);
    }

    @Override
    public void onWebSocketText(String message) {
        if (lspSession != null) {
            lspSession.onText(message);
        }
    }

    @Override
    public void onWebSocketBinary(ByteBuffer payload, Callback callback) {
        // Copy out before succeeding: Jetty may recycle the buffer once the callback completes.
        byte[] frame = new byte[payload.remaining()];
        payload.get(frame);
        callback.succeed();
        if (lspSession != null) {
            lspSession.onBinary(frame);
        }
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        if (lspSession != null) {
            lspSession.onClose();
        }
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        logger.debug("LSP WebSocket error: {}", cause.getMessage());
        if (lspSession != null) {
            lspSession.onClose();
        }
    }

    // ==================== MessageSink ====================

    @Override
    public void send(String message) {
        Session current = session;
        if (current == null || !current.isOpen()) {
            return;
        }
        try {
            current.sendText(message, Callback.NOOP);
        } catch (Exception e) {
            logger.debug("Failed to send LSP frame: {}", e.getMessage());
        }
    }

    @Override
    public void close(int statusCode, String reason) {
        Session current = session;
        if (current != null && current.isOpen()) {
            current.close(statusCode, reason, Callback.NOOP);
        }
    }

    @Override
    public boolean isOpen() {
        Session current = session;
        return current != null && current.isOpen();
    }
}
