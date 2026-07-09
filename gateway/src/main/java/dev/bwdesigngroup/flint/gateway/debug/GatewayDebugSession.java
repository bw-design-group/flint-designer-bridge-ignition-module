package dev.bwdesigngroup.flint.gateway.debug;

import dev.bwdesigngroup.flint.common.debug.DebugCommand;
import dev.bwdesigngroup.flint.common.debug.DebugEvent;
import dev.bwdesigngroup.flint.common.debug.DebugEventBatch;
import dev.bwdesigngroup.flint.common.debug.DebugState;
import dev.bwdesigngroup.flint.common.protocol.methods.debug.DebugSetBreakpointsParams.BreakpointInfo;
import dev.bwdesigngroup.flint.common.protocol.methods.debug.DebugStackTraceResult.StackFrame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.python.core.PyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages state for a debug session running in the Gateway scope. Unlike the Designer's
 * DebugSession, this uses queues for event streaming since communication happens over RPC rather
 * than WebSocket.
 */
public class GatewayDebugSession {
    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Debug");

    private final String sessionId;
    private final String code;
    private final String filePath;
    private final String modulePath;
    private final String scope; // "gateway" or "perspective"

    // Perspective context (only used when scope is "perspective")
    private String perspectiveSessionId;
    private String perspectivePageId;
    private String perspectiveViewInstanceId;
    private String perspectiveComponentPath;

    private volatile DebugState state = DebugState.NOT_STARTED;

    // Breakpoints: filePath -> line -> breakpoint info
    private final Map<String, Map<Integer, BreakpointInfo>> breakpoints = new HashMap<>();
    private final AtomicInteger breakpointIdCounter = new AtomicInteger(0);

    // Command queue - commands from Designer via RPC
    private final BlockingQueue<DebugCommand> commandQueue = new LinkedBlockingQueue<>();

    // Event queue - events to be sent to Designer via RPC polling
    private final BlockingQueue<DebugEvent> eventQueue = new LinkedBlockingQueue<>();

    // Current execution state when paused
    private volatile List<StackFrame> currentStackFrames = new ArrayList<>();
    private volatile int currentThreadId = 1;

    // Variable references for inspection
    private final AtomicInteger variableRefCounter = new AtomicInteger(1000);
    private final Map<Integer, PyObject> variableReferences = new HashMap<>();

    // Frame references
    private final Map<Integer, FrameInfo> frameReferences = new HashMap<>();

    public GatewayDebugSession(String code, String filePath, String modulePath, String scope) {
        this.sessionId = UUID.randomUUID().toString();
        this.code = code;
        this.filePath = filePath;
        this.modulePath = modulePath;
        this.scope = scope != null ? scope : "gateway";
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getCode() {
        return code;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getModulePath() {
        return modulePath;
    }

    public String getScope() {
        return scope;
    }

    public boolean isPerspectiveScope() {
        return "perspective".equals(scope);
    }

    // Perspective context getters/setters
    public String getPerspectiveSessionId() {
        return perspectiveSessionId;
    }

    public void setPerspectiveSessionId(String perspectiveSessionId) {
        this.perspectiveSessionId = perspectiveSessionId;
    }

    public String getPerspectivePageId() {
        return perspectivePageId;
    }

    public void setPerspectivePageId(String perspectivePageId) {
        this.perspectivePageId = perspectivePageId;
    }

    public String getPerspectiveViewInstanceId() {
        return perspectiveViewInstanceId;
    }

    public void setPerspectiveViewInstanceId(String perspectiveViewInstanceId) {
        this.perspectiveViewInstanceId = perspectiveViewInstanceId;
    }

    public String getPerspectiveComponentPath() {
        return perspectiveComponentPath;
    }

    public void setPerspectiveComponentPath(String perspectiveComponentPath) {
        this.perspectiveComponentPath = perspectiveComponentPath;
    }

    public DebugState getState() {
        return state;
    }

    public void setState(DebugState state) {
        this.state = state;
    }

    public boolean isActive() {
        return state == DebugState.RUNNING
                || state == DebugState.PAUSED
                || state == DebugState.NOT_STARTED;
    }

    // ========================== Breakpoint Management ==========================

    /** Sets breakpoints for a file. */
    public synchronized List<Integer> setBreakpoints(String filePath, List<BreakpointInfo> bps) {
        Map<Integer, BreakpointInfo> fileBreakpoints =
                breakpoints.computeIfAbsent(filePath, k -> new HashMap<>());
        fileBreakpoints.clear();

        List<Integer> ids = new ArrayList<>();
        for (BreakpointInfo bp : bps) {
            int id = breakpointIdCounter.incrementAndGet();
            fileBreakpoints.put(bp.getLine(), bp);
            ids.add(id);
        }

        logger.info(
                "Set {} breakpoints for file: {} at lines: {}",
                bps.size(),
                filePath,
                bps.stream()
                        .map(bp -> String.valueOf(bp.getLine()))
                        .collect(java.util.stream.Collectors.joining(", ")));
        return ids;
    }

    /** Checks if there's a breakpoint at the given file and line. */
    public synchronized boolean hasBreakpoint(String filePath, int line) {
        Map<Integer, BreakpointInfo> fileBreakpoints = breakpoints.get(filePath);
        return fileBreakpoints != null && fileBreakpoints.containsKey(line);
    }

    /** Gets breakpoint info at the given location. */
    public synchronized BreakpointInfo getBreakpoint(String filePath, int line) {
        Map<Integer, BreakpointInfo> fileBreakpoints = breakpoints.get(filePath);
        return fileBreakpoints != null ? fileBreakpoints.get(line) : null;
    }

    /** Gets all breakpoints map for flexible matching. */
    public synchronized Map<String, Map<Integer, BreakpointInfo>> getBreakpoints() {
        return breakpoints;
    }

    // ========================== Command Queue (from Designer) ==========================

    /** Sends a command to the debugger (called by RPC handler). */
    public void sendCommand(DebugCommand command) {
        commandQueue.offer(command);
        logger.debug("Command queued: {}", command.getType());
    }

    /** Waits for a command from Designer (called by Python debugger). */
    public DebugCommand waitForCommand(long timeoutMs) throws InterruptedException {
        return commandQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    // ========================== Event Queue (to Designer) ==========================

    /** Queues an event to be sent to Designer. */
    public void queueEvent(DebugEvent event) {
        eventQueue.offer(event);
        logger.debug("Event queued: {}", event.getType());
    }

    /**
     * Polls events to send to Designer (called by RPC handler). Returns all currently queued events
     * as a batch.
     *
     * @param maxWaitMs Maximum time to wait if no events are immediately available
     * @return Batch of events (may be empty)
     */
    public DebugEventBatch pollEvents(long maxWaitMs) {
        List<DebugEvent> events = new ArrayList<>();

        // First, try to get any immediately available events
        eventQueue.drainTo(events);

        // If no events were available, wait briefly for one
        if (events.isEmpty() && maxWaitMs > 0) {
            try {
                DebugEvent event = eventQueue.poll(maxWaitMs, TimeUnit.MILLISECONDS);
                if (event != null) {
                    events.add(event);
                    // Drain any additional events that arrived while waiting
                    eventQueue.drainTo(events);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return new DebugEventBatch(events, isActive());
    }

    // ========================== Stack Frame Management ==========================

    /** Updates the current stack frames. */
    public void setCurrentStackFrames(List<StackFrame> frames) {
        this.currentStackFrames = new ArrayList<>(frames);
    }

    /**
     * Updates the current stack frames from Python data. This is a helper method for Python code
     * that can't directly instantiate StackFrame due to classloader isolation in the ScriptManager
     * context.
     *
     * @param frameDataList A Python list of dicts with keys: id, name, filePath, line, modulePath
     */
    @SuppressWarnings("unchecked")
    public void setCurrentStackFramesFromPython(Object frameDataList) {
        List<StackFrame> frames = new ArrayList<>();

        if (frameDataList instanceof List) {
            for (Object item : (List<?>) frameDataList) {
                if (item instanceof Map) {
                    Map<String, Object> data = (Map<String, Object>) item;
                    int id = ((Number) data.get("id")).intValue();
                    String name = (String) data.get("name");
                    String filePath = (String) data.get("filePath");
                    int line = ((Number) data.get("line")).intValue();

                    StackFrame frame = new StackFrame(id, name, filePath, line);
                    if (data.containsKey("modulePath")) {
                        frame.setModulePath((String) data.get("modulePath"));
                    }
                    frames.add(frame);
                }
            }
        }

        this.currentStackFrames = frames;
    }

    public List<StackFrame> getCurrentStackFrames() {
        return new ArrayList<>(currentStackFrames);
    }

    public int getCurrentThreadId() {
        return currentThreadId;
    }

    // ========================== Variable References ==========================

    /** Registers a variable reference for later inspection. */
    public int registerVariableReference(PyObject obj) {
        int ref = variableRefCounter.incrementAndGet();
        synchronized (variableReferences) {
            variableReferences.put(ref, obj);
        }
        return ref;
    }

    /** Gets a variable by reference. */
    public PyObject getVariableReference(int ref) {
        synchronized (variableReferences) {
            return variableReferences.get(ref);
        }
    }

    /** Registers a frame reference for later inspection. */
    public void registerFrameReference(int frameId, FrameInfo info) {
        synchronized (frameReferences) {
            frameReferences.put(frameId, info);
        }
    }

    /** Gets frame info by ID. */
    public FrameInfo getFrameReference(int frameId) {
        synchronized (frameReferences) {
            return frameReferences.get(frameId);
        }
    }

    /**
     * Creates and registers a frame reference. This is a helper method for Python code that can't
     * directly instantiate FrameInfo due to classloader isolation in the ScriptManager context.
     */
    public void createAndRegisterFrameReference(int frameId, PyObject locals, PyObject globals) {
        FrameInfo info = new FrameInfo(locals, globals);
        synchronized (frameReferences) {
            frameReferences.put(frameId, info);
        }
    }

    /** Clears all variable and frame references (called when resuming). */
    public void clearReferences() {
        synchronized (variableReferences) {
            variableReferences.clear();
        }
        synchronized (frameReferences) {
            frameReferences.clear();
        }
        variableRefCounter.set(1000);
    }

    // ========================== Event Notification Methods ==========================

    /** Notifies that execution stopped (at breakpoint or step). */
    public void notifyStopped(String reason) {
        queueEvent(DebugEvent.stopped(reason, currentThreadId));
    }

    /** Notifies that execution continued. */
    public void notifyContinued() {
        queueEvent(DebugEvent.continued(currentThreadId));
    }

    /** Notifies that debug session terminated. */
    public void notifyTerminated() {
        queueEvent(DebugEvent.terminated());
    }

    /** Notifies about console output. */
    public void notifyOutput(String category, String output) {
        queueEvent(DebugEvent.output(category, output));
    }

    /** Information about a stack frame for variable inspection. */
    public static class FrameInfo {
        public final PyObject locals;
        public final PyObject globals;

        public FrameInfo(PyObject locals, PyObject globals) {
            this.locals = locals;
            this.globals = globals;
        }
    }
}
