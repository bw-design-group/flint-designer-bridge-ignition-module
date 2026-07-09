package dev.bwdesigngroup.flint.designer.listeners;

import com.inductiveautomation.ignition.common.script.PackageTreeNode;
import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketServer;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects changes to project scripts by periodically checking if the ScriptManager's hints tree has
 * changed. When changes are detected, notifies VS Code to invalidate its LSP cache.
 */
public class ScriptChangeDetector {
    private static final Logger logger =
            LoggerFactory.getLogger("flint.Designer.ScriptChangeDetector");

    /** Check interval in milliseconds - how often to check for changes */
    private static final long CHECK_INTERVAL_MS = 1000;

    private final DesignerContext context;
    private final FlintWebSocketServer webSocketServer;
    private final ScheduledExecutorService scheduler;

    private volatile int lastHintsTreeHash = 0;
    private volatile String lastHintsTreeSummary = "";
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

    /** Checks if the hints tree has changed since the last check. */
    private void checkForChanges() {
        if (!running) {
            return;
        }

        try {
            ScriptManager scriptManager = context.getScriptManager();
            if (scriptManager == null) {
                return;
            }

            PackageTreeNode hintsTree = scriptManager.getHintsTree();
            if (hintsTree == null) {
                return;
            }

            int currentHash = hintsTree.hashCode();
            String currentSummary = computeHintsTreeSummary(hintsTree);

            // Check if anything changed
            if (currentHash != lastHintsTreeHash || !currentSummary.equals(lastHintsTreeSummary)) {
                logger.debug(
                        "Script changes detected: hash {} -> {}, summary {} -> {}",
                        lastHintsTreeHash,
                        currentHash,
                        lastHintsTreeSummary,
                        currentSummary);

                // Update baseline
                lastHintsTreeHash = currentHash;
                lastHintsTreeSummary = currentSummary;

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
                PackageTreeNode hintsTree = scriptManager.getHintsTree();
                if (hintsTree != null) {
                    lastHintsTreeHash = hintsTree.hashCode();
                    lastHintsTreeSummary = computeHintsTreeSummary(hintsTree);
                    logger.debug(
                            "Baseline updated: hash={}, summary={}",
                            lastHintsTreeHash,
                            lastHintsTreeSummary);
                }
            }
        } catch (Exception e) {
            logger.debug("Error updating baseline: {}", e.getMessage());
        }
    }

    /**
     * Computes a summary string of the hints tree for change detection. This includes the top-level
     * keys and the count of children/methods per key.
     */
    private String computeHintsTreeSummary(PackageTreeNode tree) {
        StringBuilder sb = new StringBuilder();
        if (tree.children() != null) {
            for (Map.Entry<String, PackageTreeNode> entry : tree.children().entrySet()) {
                sb.append(entry.getKey());
                sb.append(':');
                PackageTreeNode child = entry.getValue();
                int childCount = child.children() != null ? child.children().size() : 0;
                int methodCount = child.methods() != null ? child.methods().size() : 0;
                sb.append(childCount).append('/').append(methodCount);
                sb.append(';');
            }
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
