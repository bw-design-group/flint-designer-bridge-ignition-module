package dev.bwdesigngroup.flint.designer.handlers;

import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording.RecordingEventBatch;
import dev.bwdesigngroup.flint.designer.rpc.GatewayRpcClient;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketServer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Polls the Gateway for recording event batches and broadcasts them as WebSocket notifications to
 * connected VS Code clients. Follows the same pattern as {@link
 * dev.bwdesigngroup.flint.designer.listeners.ScriptChangeDetector}.
 */
public class RecordingPollingBridge {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.RecordingBridge");

    private static final long POLL_INTERVAL_MS = 100;

    private final GatewayRpcClient gatewayRpcClient;
    private final FlintWebSocketServer webSocketServer;
    private final String recordingId;
    private final ScheduledExecutorService scheduler;

    private volatile boolean running = false;

    public RecordingPollingBridge(
            GatewayRpcClient gatewayRpcClient,
            FlintWebSocketServer webSocketServer,
            String recordingId) {
        this.gatewayRpcClient = gatewayRpcClient;
        this.webSocketServer = webSocketServer;
        this.recordingId = recordingId;
        this.scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "FlintRecordingBridge");
                            t.setDaemon(true);
                            return t;
                        });
    }

    /** Starts polling the Gateway for recording events. */
    public void start() {
        if (running) {
            return;
        }
        running = true;

        scheduler.scheduleWithFixedDelay(
                this::pollAndBroadcast, POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);

        logger.info(
                "Recording polling bridge started for recording: {} (interval={}ms)",
                recordingId,
                POLL_INTERVAL_MS);
    }

    /** Stops polling. */
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
        logger.info("Recording polling bridge stopped for recording: {}", recordingId);
    }

    /** Polls the Gateway for events and broadcasts non-empty batches to VS Code. */
    private void pollAndBroadcast() {
        if (!running) {
            return;
        }

        try {
            RecordingEventBatch batch =
                    gatewayRpcClient.getRpc().perspectivePollRecordingEvents(recordingId);

            if (batch == null) {
                return;
            }

            // Broadcast if there are events or if snapshot counts changed
            boolean hasEvents = batch.getEvents() != null && !batch.getEvents().isEmpty();
            if (hasEvents) {
                webSocketServer.broadcastNotification(
                        FlintConstants.NOTIFICATION_PERSPECTIVE_RECORDING_EVENT, batch);
            }

            // Check if recording is complete
            if (batch.isComplete()) {
                logger.info("Recording {} completed: {}", recordingId, batch.getCompletionReason());

                Map<String, Object> completeParams = new HashMap<>();
                completeParams.put("recordingId", recordingId);
                completeParams.put("reason", batch.getCompletionReason());
                completeParams.put("pendingCount", batch.getPendingCount());
                completeParams.put("resolvedCount", batch.getResolvedCount());
                completeParams.put("errorCount", batch.getErrorCount());
                completeParams.put("totalCount", batch.getTotalCount());

                webSocketServer.broadcastNotification(
                        FlintConstants.NOTIFICATION_PERSPECTIVE_RECORDING_COMPLETE, completeParams);

                // Auto-stop polling
                running = false;
                scheduler.shutdown();
            }
        } catch (Exception e) {
            logger.debug("Error polling recording events: {}", e.getMessage());
        }
    }
}
