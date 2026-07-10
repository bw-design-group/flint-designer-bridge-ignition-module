package dev.bwdesigngroup.flint.gateway.lsp.ws;

import java.time.Duration;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ignition 8.1 (Jetty 10, {@code javax}) WebSocket endpoint. Bridges Jetty's {@link
 * WebSocketListener} callbacks to a shared {@link LspSession} and exposes the socket back to the
 * router as a {@link MessageSink}. One instance per connection.
 */
public class FlintLspWebSocketEndpoint implements WebSocketListener, MessageSink {

    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Lsp.Ws");
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(5);

    private final LspWebSocketBridge bridge;
    private volatile Session session;
    private volatile LspSession lspSession;

    public FlintLspWebSocketEndpoint(LspWebSocketBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void onWebSocketConnect(Session session) {
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
    public void onWebSocketBinary(byte[] payload, int offset, int len) {
        if (lspSession == null || payload == null) {
            return;
        }
        byte[] frame = new byte[len];
        System.arraycopy(payload, offset, frame, 0, len);
        lspSession.onBinary(frame);
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
            current.getRemote().sendString(message);
        } catch (Exception e) {
            logger.debug("Failed to send LSP frame: {}", e.getMessage());
        }
    }

    @Override
    public void close(int statusCode, String reason) {
        Session current = session;
        if (current != null && current.isOpen()) {
            current.close(statusCode, reason);
        }
    }

    @Override
    public boolean isOpen() {
        Session current = session;
        return current != null && current.isOpen();
    }
}
