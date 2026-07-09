package dev.bwdesigngroup.flint.designer.listeners;

import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.common.script.hints.ScriptFunctionHint;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketServer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects changes to project scripts by periodically checking if the ScriptManager's hints map has
 * changed. When changes are detected, notifies VS Code to invalidate its LSP cache.
 *
 * <p>This approach is used because the ProjectResourceListener interface requires SDK 8.3+
 * operation classes that don't exist in 8.1.
 */
public class ScriptChangeDetector {
    private static final Logger logger =
            LoggerFactory.getLogger("flint.Designer.ScriptChangeDetector");

    /** Check interval in milliseconds - how often to check for changes */
    private static final long CHECK_INTERVAL_MS = 1000;

    private final DesignerContext context;
    private final FlintWebSocketServer webSocketServer;
    private final ScheduledExecutorService scheduler;

    private volatile int lastHintsMapSize = -1;
    private volatile String lastHintsMapHash = "";
    private volatile boolean running = false;

    public ScriptChangeDetector(DesignerContext context, FlintWebSocketServer webSocketServer) {
        this.context = context;
        this.webSocketServer = webSocketServer;
        this.scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "FlintScriptChangeDetector");
                            t.setDaemon(true);
                            return t;
                        });
    }

    /** Starts the change detection polling. */
    public void start() {
        if (running) {
            return;
        }
        running = true;

        // Initialize baseline
        updateBaseline();

        // Schedule periodic checks
        scheduler.scheduleWithFixedDelay(
                this::checkForChanges, CHECK_INTERVAL_MS, CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);

        logger.info("Script change detector started (checking every {}ms)", CHECK_INTERVAL_MS);
    }

    /** Stops the change detection polling. */
    public void stop() {
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("Script change detector stopped");
    }

    /** Checks if the hints map has changed since the last check. */
    private void checkForChanges() {
        if (!running) {
            return;
        }

        try {
            ScriptManager scriptManager = context.getScriptManager();
            if (scriptManager == null) {
                return;
            }

            Map<String, List<ScriptFunctionHint>> hintsMap = scriptManager.getHintsMap();
            if (hintsMap == null) {
                return;
            }

            int currentSize = hintsMap.size();
            String currentHash = computeHintsHash(hintsMap);

            // Check if anything changed
            if (currentSize != lastHintsMapSize || !currentHash.equals(lastHintsMapHash)) {
                logger.debug(
                        "Script changes detected: size {} -> {}, hash {} -> {}",
                        lastHintsMapSize,
                        currentSize,
                        lastHintsMapHash,
                        currentHash);

                // Update baseline
                lastHintsMapSize = currentSize;
                lastHintsMapHash = currentHash;

                // Notify VS Code
                notifyCacheInvalidation();
            }
        } catch (Exception e) {
            logger.debug("Error checking for script changes: {}", e.getMessage());
        }
    }

    /** Updates the baseline state for change detection. */
    private void updateBaseline() {
        try {
            ScriptManager scriptManager = context.getScriptManager();
            if (scriptManager != null) {
                Map<String, List<ScriptFunctionHint>> hintsMap = scriptManager.getHintsMap();
                if (hintsMap != null) {
                    lastHintsMapSize = hintsMap.size();
                    lastHintsMapHash = computeHintsHash(hintsMap);
                    logger.debug(
                            "Baseline updated: size={}, hash={}",
                            lastHintsMapSize,
                            lastHintsMapHash);
                }
            }
        } catch (Exception e) {
            logger.debug("Error updating baseline: {}", e.getMessage());
        }
    }

    /**
     * Computes a simple hash of the hints map for change detection. This includes the keys and the
     * count of hints per key.
     */
    private String computeHintsHash(Map<String, List<ScriptFunctionHint>> hintsMap) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<ScriptFunctionHint>> entry : hintsMap.entrySet()) {
            sb.append(entry.getKey());
            sb.append(':');
            sb.append(entry.getValue() != null ? entry.getValue().size() : 0);
            sb.append(';');
        }
        return String.valueOf(sb.toString().hashCode());
    }

    /** Sends a cache invalidation notification to all connected VS Code clients. */
    private void notifyCacheInvalidation() {
        if (webSocketServer != null) {
            logger.info("Broadcasting LSP cache invalidation notification (scripts changed)");

            webSocketServer.broadcastNotification(
                    FlintConstants.NOTIFICATION_LSP_CACHE_INVALIDATED,
                    new CacheInvalidationParams("modified", 1));
        }
    }

    /** Parameters sent with the cache invalidation notification. */
    public static class CacheInvalidationParams {
        private final String reason;
        private final int count;

        public CacheInvalidationParams(String reason, int count) {
            this.reason = reason;
            this.count = count;
        }

        public String getReason() {
            return reason;
        }

        public int getCount() {
            return count;
        }
    }
}
