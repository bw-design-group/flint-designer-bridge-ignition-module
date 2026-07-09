package dev.bwdesigngroup.flint.gateway.perspective;

import com.inductiveautomation.perspective.gateway.model.ComponentModel;
import com.inductiveautomation.perspective.gateway.model.ViewModel;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.BindingProfile;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording.RecordingBindingEvent;
import dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording.RecordingEventBatch;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Polling-based state-diffing engine that captures binding state transitions during Perspective
 * view loading. Periodically walks the component tree, compares each binding's current state
 * against the previously recorded state, and enqueues {@link RecordingBindingEvent} instances for
 * every transition.
 *
 * <p>Supports automatic completion when all bindings reach a resolved state and a configurable
 * grace period has elapsed, as well as a hard maximum duration safety net.
 *
 * <p>Delegates reflection-based binding extraction to {@link BindingStateExtractor}.
 */
class BindingRecorder {
    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.BindingRecorder");

    // Configuration
    private final Supplier<List<ViewModel>> viewsSupplier;
    private final String viewPath;
    private final int pollIntervalMs;
    private final int maxDurationMs;
    private final boolean autoStopOnAllResolved;
    private final int autoStopDelayMs;

    // Infrastructure
    private final ScheduledExecutorService scheduler;
    private final BindingStateExtractor extractor;

    // State tracking
    private final Map<String, String> previousBindingStates;
    private final Map<String, String> previousValueHashes;
    private final Map<String, String> internedKeys;
    private final ConcurrentLinkedQueue<RecordingBindingEvent> eventQueue;

    // Counters (volatile for cross-thread visibility)
    private volatile int pendingCount;
    private volatile int resolvedCount;
    private volatile int errorCount;
    private volatile int totalCount;

    // Lifecycle
    private volatile boolean running;
    private volatile boolean complete;
    private volatile String completionReason;

    // Metrics
    private long startTimeMs;
    private int totalEventsRecorded;
    private int transitionEventsRecorded;
    private int totalPollCount;

    // Auto-stop countdown: 0 means not currently counting down
    private long allResolvedSinceMs;

    // Binding count stability tracking: reset auto-stop when new bindings appear
    private int lastKnownBindingCount;

    BindingRecorder(
            Supplier<List<ViewModel>> viewsSupplier,
            String viewPath,
            int pollIntervalMs,
            int maxDurationMs,
            boolean autoStopOnAllResolved,
            int autoStopDelayMs) {
        this.viewsSupplier = viewsSupplier;
        this.viewPath = viewPath;
        this.pollIntervalMs = pollIntervalMs;
        this.maxDurationMs = maxDurationMs;
        this.autoStopOnAllResolved = autoStopOnAllResolved;
        this.autoStopDelayMs = autoStopDelayMs;

        this.scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "FlintBindingRecorder");
                            t.setDaemon(true);
                            return t;
                        });
        this.extractor = new BindingStateExtractor();

        this.previousBindingStates = new HashMap<>();
        this.previousValueHashes = new HashMap<>();
        this.internedKeys = new HashMap<>();
        this.eventQueue = new ConcurrentLinkedQueue<>();

        this.pendingCount = 0;
        this.resolvedCount = 0;
        this.errorCount = 0;
        this.totalCount = 0;

        this.running = false;
        this.complete = false;
        this.completionReason = null;

        this.startTimeMs = 0;
        this.totalEventsRecorded = 0;
        this.transitionEventsRecorded = 0;
        this.totalPollCount = 0;
        this.allResolvedSinceMs = 0;
        this.lastKnownBindingCount = 0;
    }

    /** Captures the initial binding baseline and starts the polling scheduler. */
    void start() {
        if (running) {
            return;
        }
        running = true;
        startTimeMs = System.currentTimeMillis();

        // Capture initial baseline
        captureBaseline();

        logger.info(
                "Binding recorder started for view '{}' (poll={}ms, maxDuration={}ms, autoStop={})",
                viewPath,
                pollIntervalMs,
                maxDurationMs,
                autoStopOnAllResolved);

        // Schedule periodic polling
        scheduler.scheduleWithFixedDelay(
                this::performPoll, pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);

        // Schedule max-duration safety timeout
        scheduler.schedule(() -> stop("maxDuration"), maxDurationMs, TimeUnit.MILLISECONDS);
    }

    /** Stops the recorder with the given reason. */
    void stop(String reason) {
        if (complete) {
            return;
        }
        complete = true;
        completionReason = reason;
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

        logger.info(
                "Binding recorder stopped for view '{}': reason={}, events={}, polls={}",
                viewPath,
                reason,
                totalEventsRecorded,
                totalPollCount);
    }

    /** Drains all queued events into a batch with current snapshot counts. */
    RecordingEventBatch pollEvents() {
        RecordingEventBatch batch = new RecordingEventBatch();

        // Drain event queue
        List<RecordingBindingEvent> events = new ArrayList<>();
        RecordingBindingEvent event;
        while ((event = eventQueue.poll()) != null) {
            events.add(event);
        }
        batch.setEvents(events);

        // Snapshot counts
        batch.setPendingCount(pendingCount);
        batch.setResolvedCount(resolvedCount);
        batch.setErrorCount(errorCount);
        batch.setTotalCount(totalCount);

        // Recording state
        batch.setComplete(complete);
        batch.setCompletionReason(completionReason);

        return batch;
    }

    // ==================== Internal Polling ====================

    /**
     * Walks the component tree, diffs binding states against the previous snapshot, and enqueues
     * events for any transitions.
     */
    private void performPoll() {
        if (!running) {
            return;
        }
        totalPollCount++;

        try {
            long now = System.currentTimeMillis();
            long relativeMs = now - startTimeMs;

            // Re-query views each cycle to discover newly instantiated embedded views
            List<ViewModel> currentViews = viewsSupplier.get();

            // Walk all views' component trees and diff binding states
            for (ViewModel view : currentViews) {
                try {
                    ComponentModel rootContainer = view.getRootContainer();
                    if (rootContainer != null) {
                        walkAndDiff(rootContainer, now, relativeMs);
                    }
                } catch (Exception e) {
                    logger.debug("Error walking view during poll: {}", e.getMessage());
                }
            }

            // Counts are now tracked incrementally in walkAndDiff()

            // Detect new bindings appearing (e.g. FlexRepeater child views being instantiated)
            boolean newBindingsDiscovered = totalCount > lastKnownBindingCount;
            if (newBindingsDiscovered) {
                logger.debug(
                        "New bindings discovered for '{}': {} -> {} (resetting auto-stop countdown)",
                        viewPath,
                        lastKnownBindingCount,
                        totalCount);
                allResolvedSinceMs = 0;
                lastKnownBindingCount = totalCount;
            }

            // Periodic debug logging every ~5 seconds (100 polls at 50ms)
            if (totalPollCount % 100 == 0) {
                logger.info(
                        "Recording poll #{} for '{}': events={}, transitions={}, bindings={}, elapsed={}ms",
                        totalPollCount,
                        viewPath,
                        totalEventsRecorded,
                        transitionEventsRecorded,
                        totalCount,
                        relativeMs);
            }

            // Auto-stop logic: only engage when at least one real transition (not baseline)
            // has been captured, so stable views don't immediately stop.
            if (autoStopOnAllResolved
                    && transitionEventsRecorded > 0
                    && pendingCount == 0
                    && errorCount == 0
                    && totalCount > 0) {
                if (allResolvedSinceMs == 0) {
                    allResolvedSinceMs = now;
                    logger.debug(
                            "All bindings resolved for view '{}', starting auto-stop countdown ({}ms)",
                            viewPath,
                            autoStopDelayMs);
                }
                if (now - allResolvedSinceMs >= autoStopDelayMs) {
                    stop("allResolved");
                }
            } else {
                allResolvedSinceMs = 0;
            }

        } catch (Exception e) {
            logger.debug("Error during binding poll for view '{}': {}", viewPath, e.getMessage());
        }
    }

    /**
     * Captures the initial binding state baseline and emits initial events so the waterfall
     * timeline shows the starting state of all bindings. Counts are tracked incrementally as each
     * binding is discovered.
     */
    private void captureBaseline() {
        long now = System.currentTimeMillis();
        long relativeMs = now - startTimeMs;

        // Reset counts for baseline capture
        pendingCount = 0;
        resolvedCount = 0;
        errorCount = 0;

        List<ViewModel> currentViews = viewsSupplier.get();
        for (ViewModel view : currentViews) {
            try {
                ComponentModel rootContainer = view.getRootContainer();
                if (rootContainer == null) {
                    continue;
                }

                walkComponentTree(
                        rootContainer,
                        (componentPath, componentType, bindings) -> {
                            for (BindingProfile binding : bindings) {
                                String bindingKey =
                                        internKey(componentPath, binding.getPropertyPath());
                                String state = binding.getState();
                                previousBindingStates.put(bindingKey, state);
                                if (binding.getValueHash() != null) {
                                    previousValueHashes.put(bindingKey, binding.getValueHash());
                                }

                                // Track counts incrementally
                                incrementCountForState(state);

                                // Emit initial event (previousState=null) so the waterfall has data
                                RecordingBindingEvent event = new RecordingBindingEvent();
                                event.setTimestampMs(now);
                                event.setRelativeMs(relativeMs);
                                event.setComponentPath(componentPath);
                                event.setComponentType(componentType);
                                event.setPropertyPath(binding.getPropertyPath());
                                event.setBindingType(binding.getBindingType());
                                event.setPreviousState(null);
                                event.setNewState(state);
                                event.setBaseline(true);

                                if ("bad".equals(state)) {
                                    event.setLastError(binding.getLastError());
                                }

                                eventQueue.add(event);
                                totalEventsRecorded++;
                            }
                        });
            } catch (Exception e) {
                logger.debug("Error capturing baseline for view: {}", e.getMessage());
            }
        }

        totalCount = previousBindingStates.size();
        lastKnownBindingCount = totalCount;
        logger.debug(
                "Baseline captured for '{}': {} views, total={}, pending={}, resolved={}, error={}",
                viewPath,
                currentViews.size(),
                totalCount,
                pendingCount,
                resolvedCount,
                errorCount);
    }

    /**
     * Walks the component tree and diffs each binding's value and state against the previous
     * snapshot. Emits events for value changes (execution completed) and quality state changes
     * (errors, pending). Counts are updated incrementally on each state change.
     */
    private void walkAndDiff(ComponentModel rootContainer, long now, long relativeMs) {
        walkComponentTree(
                rootContainer,
                (componentPath, componentType, bindings) -> {
                    for (BindingProfile binding : bindings) {
                        String bindingKey = internKey(componentPath, binding.getPropertyPath());
                        String currentState = binding.getState();
                        String currentValueHash = binding.getValueHash();
                        String previousState = previousBindingStates.get(bindingKey);
                        String previousValueHash = previousValueHashes.get(bindingKey);

                        boolean stateChanged =
                                previousState != null && !currentState.equals(previousState);
                        boolean valueChanged =
                                currentValueHash != null
                                        && previousValueHash != null
                                        && !currentValueHash.equals(previousValueHash);
                        boolean isNew = previousState == null;

                        if (stateChanged || valueChanged || isNew) {
                            // Update counts incrementally
                            if (stateChanged) {
                                decrementCountForState(previousState);
                                incrementCountForState(currentState);
                            } else if (isNew) {
                                incrementCountForState(currentState);
                            }

                            RecordingBindingEvent event = new RecordingBindingEvent();
                            event.setTimestampMs(now);
                            event.setRelativeMs(relativeMs);
                            event.setComponentPath(componentPath);
                            event.setComponentType(componentType);
                            event.setPropertyPath(binding.getPropertyPath());
                            event.setBindingType(binding.getBindingType());
                            event.setPreviousState(previousState);
                            event.setNewState(currentState);

                            if ("bad".equals(currentState)) {
                                event.setLastError(binding.getLastError());
                            }

                            eventQueue.add(event);
                            totalEventsRecorded++;
                            transitionEventsRecorded++;
                        }

                        // Update tracked state and value
                        previousBindingStates.put(bindingKey, currentState);
                        if (currentValueHash != null) {
                            previousValueHashes.put(bindingKey, currentValueHash);
                        }
                    }
                });

        // Keep totalCount in sync with map size (handles newly discovered bindings)
        totalCount = previousBindingStates.size();
    }

    /** Increments the appropriate counter for a binding state. */
    private void incrementCountForState(String state) {
        if ("good".equals(state)) {
            resolvedCount++;
        } else if ("pending".equals(state)) {
            pendingCount++;
        } else if ("bad".equals(state)) {
            errorCount++;
        }
    }

    /** Decrements the appropriate counter for a binding state. */
    private void decrementCountForState(String state) {
        if ("good".equals(state)) {
            resolvedCount--;
        } else if ("pending".equals(state)) {
            pendingCount--;
        } else if ("bad".equals(state)) {
            errorCount--;
        }
    }

    /**
     * Returns a canonical interned key for a binding, avoiding repeated string concatenation on
     * every poll cycle. After baseline capture the map is warm and computeIfAbsent returns cached
     * references.
     */
    private String internKey(String componentPath, String propertyPath) {
        String raw = componentPath + "::" + propertyPath;
        return internedKeys.computeIfAbsent(raw, k -> k);
    }

    // ==================== Component Tree Walking ====================

    /** Recursively walks the component tree and invokes the visitor for each component. */
    private void walkComponentTree(ComponentModel component, ComponentBindingVisitor visitor) {
        String componentPath = getComponentPath(component);
        String componentType = component.getType();

        List<BindingProfile> bindings = extractor.extractBindings(component);
        visitor.visit(componentPath, componentType, bindings);

        // Recurse into children
        Collection<? extends com.inductiveautomation.perspective.gateway.api.Component> children =
                component.getChildren();
        for (com.inductiveautomation.perspective.gateway.api.Component child : children) {
            if (child instanceof ComponentModel) {
                walkComponentTree((ComponentModel) child, visitor);
            }
        }
    }

    /** Gets the qualified path for a component, falling back to name on error. */
    private String getComponentPath(ComponentModel component) {
        try {
            return component.getQualifiedPath();
        } catch (Exception e) {
            return component.getName();
        }
    }

    /** Functional interface for visiting components during tree walks. */
    @FunctionalInterface
    private interface ComponentBindingVisitor {
        void visit(String componentPath, String componentType, List<BindingProfile> bindings);
    }

    // ==================== Getters ====================

    boolean isComplete() {
        return complete;
    }

    boolean isRunning() {
        return running;
    }

    String getCompletionReason() {
        return completionReason;
    }

    long getStartTimeMs() {
        return startTimeMs;
    }

    int getTotalEventsRecorded() {
        return totalEventsRecorded;
    }

    int getTotalPollCount() {
        return totalPollCount;
    }

    int getPendingCount() {
        return pendingCount;
    }

    int getResolvedCount() {
        return resolvedCount;
    }

    int getErrorCount() {
        return errorCount;
    }

    int getTotalCount() {
        return totalCount;
    }

    String getViewPath() {
        return viewPath;
    }
}
