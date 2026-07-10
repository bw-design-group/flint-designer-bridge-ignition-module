package dev.bwdesigngroup.flint.gateway.lsp.ws;

import com.google.gson.Gson;
import com.inductiveautomation.ignition.gateway.dataroutes.RequestContext;
import dev.bwdesigngroup.flint.gateway.auth.GatewayAuthenticator;
import dev.bwdesigngroup.flint.gateway.lsp.FlintLanguageServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hook-owned coordinator for the raw-LSP-over-WebSocket transport. The gateway hook constructs one
 * and publishes it as a {@code ServletContext} attribute ({@link
 * dev.bwdesigngroup.flint.common.FlintConstants#LSP_WS_BRIDGE_ATTR}) so the container-instantiated
 * WebSocket servlet can find it in {@code init()}.
 *
 * <p>Holds the shared language-server engine, the request authenticator, a single daemon scheduler
 * ({@code flint-lsp-ws}) that debounces diagnostics across all sessions, and the live-session map.
 * Contains no Jetty types, so it is shared verbatim between the 8.1 and 8.3 builds; the per-target
 * servlet supplies a {@link MessageSink} for each connection.
 */
public final class LspWebSocketBridge {

    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Lsp.Ws");

    /** WebSocket status for a server going away (RFC 6455 §7.4.1). */
    private static final int WS_GOING_AWAY = 1001;

    private final FlintLanguageServer engine;
    private final GatewayAuthenticator authenticator;
    private final Gson gson;
    private final String serverVersion;
    private final ScheduledExecutorService scheduler;
    private final Map<String, LspSession> sessions = new ConcurrentHashMap<>();

    public LspWebSocketBridge(
            FlintLanguageServer engine,
            GatewayAuthenticator authenticator,
            Gson gson,
            String serverVersion) {
        this.engine = engine;
        this.authenticator = authenticator;
        this.gson = gson;
        this.serverVersion = serverVersion;
        this.scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "flint-lsp-ws");
                            thread.setDaemon(true);
                            return thread;
                        });
    }

    /** Authenticates a WebSocket upgrade using the same scheme as the HTTP transport. */
    public GatewayAuthenticator.AuthResult authenticate(RequestContext ctx) {
        return authenticator.authenticate(ctx);
    }

    /** Registers a new connection backed by the given sink and returns its session. */
    public LspSession openSession(MessageSink sink) {
        LspSession session =
                new LspSession(engine, gson, scheduler, serverVersion, sink, this::closeSession);
        sessions.put(session.getId(), session);
        logger.debug(
                "Opened LSP WebSocket session {} ({} active)", session.getId(), sessions.size());
        return session;
    }

    /** Deregisters a session by id (invoked from {@link LspSession#onClose()}; idempotent). */
    public void closeSession(String id) {
        sessions.remove(id);
    }

    /** Number of live sessions (used by tests and diagnostics logging). */
    public int activeSessionCount() {
        return sessions.size();
    }

    /** Closes every session with WS 1001 and stops the scheduler. */
    public void shutdown() {
        List<LspSession> snapshot = new ArrayList<>(sessions.values());
        sessions.clear();
        for (LspSession session : snapshot) {
            try {
                session.close(WS_GOING_AWAY, "Gateway shutting down");
            } catch (Exception e) {
                logger.debug("Error closing LSP session on shutdown: {}", e.getMessage());
            }
        }
        scheduler.shutdownNow();
    }
}
