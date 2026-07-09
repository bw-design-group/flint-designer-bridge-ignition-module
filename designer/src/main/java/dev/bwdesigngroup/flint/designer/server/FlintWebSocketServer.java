package dev.bwdesigngroup.flint.designer.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcNotification;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocket server for Flint VS Code extension communication. Binds to localhost only and
 * implements JSON-RPC 2.0 protocol.
 */
public class FlintWebSocketServer extends WebSocketServer {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.WebSocket");

    private final DesignerContext context;
    private final String secret;
    private final Map<WebSocket, FlintWebSocketHandler> handlers;
    private final Map<WebSocket, ScheduledFuture<?>> authTimeouts;
    private final ScheduledExecutorService scheduler;
    private final Gson gson;

    // Startup synchronization
    private final CountDownLatch startupLatch = new CountDownLatch(1);
    private final AtomicReference<Exception> startupError = new AtomicReference<>();
    private volatile boolean startedSuccessfully = false;

    public FlintWebSocketServer(InetSocketAddress address, DesignerContext context, String secret) {
        super(address);
        this.context = context;
        this.secret = secret;
        this.handlers = new ConcurrentHashMap<>();
        this.authTimeouts = new ConcurrentHashMap<>();
        this.scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "FlintAuthTimeout");
                            t.setDaemon(true);
                            return t;
                        });
        this.gson = new GsonBuilder().create();

        // Enable SO_REUSEADDR to allow quick port reuse after shutdown
        setReuseAddr(true);

        // Set connection lost timeout (ping/pong)
        setConnectionLostTimeout(30);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String remoteAddress = conn.getRemoteSocketAddress().toString();
        logger.info("New connection from: {}", remoteAddress);

        // Create handler for this connection
        FlintWebSocketHandler handler = new FlintWebSocketHandler(conn, context, secret, this);
        handlers.put(conn, handler);

        // Set authentication timeout
        ScheduledFuture<?> timeout =
                scheduler.schedule(
                        () -> {
                            if (!handler.isAuthenticated()) {
                                logger.warn(
                                        "Authentication timeout for connection: {}", remoteAddress);
                                conn.close(4001, "Authentication timeout");
                            }
                        },
                        FlintConstants.AUTH_TIMEOUT_MS,
                        TimeUnit.MILLISECONDS);
        authTimeouts.put(conn, timeout);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String remoteAddress =
                conn.getRemoteSocketAddress() != null
                        ? conn.getRemoteSocketAddress().toString()
                        : "unknown";
        logger.info(
                "Connection closed: {} (code={}, reason={}, remote={})",
                remoteAddress,
                code,
                reason,
                remote);

        // Cleanup
        handlers.remove(conn);
        ScheduledFuture<?> timeout = authTimeouts.remove(conn);
        if (timeout != null) {
            timeout.cancel(false);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        FlintWebSocketHandler handler = handlers.get(conn);
        if (handler != null) {
            handler.handleMessage(message);
        } else {
            logger.warn("Received message for unknown connection");
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        String remoteAddress =
                conn != null && conn.getRemoteSocketAddress() != null
                        ? conn.getRemoteSocketAddress().toString()
                        : "unknown";
        logger.error("WebSocket error for {}: {}", remoteAddress, ex.getMessage(), ex);

        // If this is a startup error (conn is null for server-level errors), signal the latch
        if (conn == null && startupLatch.getCount() > 0) {
            startupError.set(ex);
            startupLatch.countDown();
        }
    }

    @Override
    public void onStart() {
        logger.info("Flint WebSocket server started on {}", getAddress());
        startedSuccessfully = true;
        startupLatch.countDown();
    }

    /**
     * Starts the server and waits for it to be ready or fail.
     *
     * @param timeoutMs Maximum time to wait for startup in milliseconds
     * @throws Exception if startup fails or times out
     */
    public void startAndWait(long timeoutMs) throws Exception {
        // Start the server (asynchronous)
        start();

        // Wait for startup to complete
        boolean completed = startupLatch.await(timeoutMs, TimeUnit.MILLISECONDS);

        if (!completed) {
            throw new Exception("WebSocket server startup timed out");
        }

        // Check if there was a startup error
        Exception error = startupError.get();
        if (error != null) {
            throw error;
        }

        if (!startedSuccessfully) {
            throw new Exception("WebSocket server failed to start");
        }
    }

    /** Returns true if the server started successfully. */
    public boolean isStartedSuccessfully() {
        return startedSuccessfully;
    }

    @Override
    public void stop(int timeout) throws InterruptedException {
        // Shutdown scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(timeout, TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Stop server
        super.stop(timeout);
    }

    /** Called when a connection is authenticated. Cancels the authentication timeout. */
    public void onAuthenticated(WebSocket conn) {
        ScheduledFuture<?> timeout = authTimeouts.remove(conn);
        if (timeout != null) {
            timeout.cancel(false);
        }
    }

    /**
     * Broadcasts a JSON-RPC notification to all authenticated clients.
     *
     * @param method The notification method name
     * @param params Optional parameters for the notification
     */
    public void broadcastNotification(String method, Object params) {
        JsonRpcNotification notification = new JsonRpcNotification(method, params);
        String json = gson.toJson(notification);

        int sentCount = 0;
        for (Map.Entry<WebSocket, FlintWebSocketHandler> entry : handlers.entrySet()) {
            WebSocket conn = entry.getKey();
            FlintWebSocketHandler handler = entry.getValue();

            // Only send to authenticated connections
            if (handler.isAuthenticated() && conn.isOpen()) {
                try {
                    conn.send(json);
                    sentCount++;
                } catch (Exception e) {
                    logger.warn("Failed to send notification to client: {}", e.getMessage());
                }
            }
        }

        if (sentCount > 0) {
            logger.debug("Broadcast notification '{}' to {} clients", method, sentCount);
        }
    }
}
