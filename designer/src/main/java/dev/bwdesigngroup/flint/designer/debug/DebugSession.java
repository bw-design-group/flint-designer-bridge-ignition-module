package dev.bwdesigngroup.flint.designer.debug;

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
 * Manages state for a single debug session. Handles breakpoints, stepping commands, and variable
 * references.
 */
public class DebugSession {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.Debug");

    private final String sessionId;
    private final String code;
    private final String filePath;
    private final String modulePath;

    private volatile DebugState state = DebugState.NOT_STARTED;

    // Breakpoints: filePath -> line -> breakpoint info
    private final Map<String, Map<Integer, BreakpointInfo>> breakpoints = new HashMap<>();
    private final AtomicInteger breakpointIdCounter = new AtomicInteger(0);

    // Command queue for debugger control
    private final BlockingQueue<DebugCommand> commandQueue = new LinkedBlockingQueue<>();

    // Current execution state when paused
    private volatile List<StackFrame> currentStackFrames = new ArrayList<>();
    private volatile int currentThreadId = 1;

    // Variable references for inspection
    private final AtomicInteger variableRefCounter = new AtomicInteger(1000);
    private final Map<Integer, PyObject> variableReferences = new HashMap<>();

    // Frame references
    private final Map<Integer, FrameInfo> frameReferences = new HashMap<>();

    // Callback for sending events to VS Code
    private volatile DebugEventCallback eventCallback;

    public DebugSession(String code, String filePath, String modulePath) {
        this.sessionId = UUID.randomUUID().toString();
        this.code = code;
        this.filePath = filePath;
        this.modulePath = modulePath;
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

    public DebugState getState() {
        return state;
    }

    public void setState(DebugState state) {
        this.state = state;
    }

    public void setEventCallback(DebugEventCallback callback) {
        this.eventCallback = callback;
    }

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

    /**
     * Gets all breakpoints map for flexible matching. Used by the Python debugger to match
     * breakpoints when file paths differ.
     */
    public synchronized Map<String, Map<Integer, BreakpointInfo>> getBreakpoints() {
        return breakpoints;
    }

    /** Sends a command to the debugger. */
    public void sendCommand(DebugCommand command) {
        commandQueue.offer(command);
    }

    /** Waits for a command from VS Code. */
    public DebugCommand waitForCommand(long timeoutMs) throws InterruptedException {
        return commandQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /** Updates the current stack frames. */
    public void setCurrentStackFrames(List<StackFrame> frames) {
        this.currentStackFrames = new ArrayList<>(frames);
    }

    public List<StackFrame> getCurrentStackFrames() {
        return new ArrayList<>(currentStackFrames);
    }

    public int getCurrentThreadId() {
        return currentThreadId;
    }

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

    /** Sends a stopped event to VS Code. */
    public void notifyStopped(String reason) {
        if (eventCallback != null) {
            eventCallback.onStopped(reason, currentThreadId);
        }
    }

    /** Sends a continued event to VS Code. */
    public void notifyContinued() {
        if (eventCallback != null) {
            eventCallback.onContinued(currentThreadId);
        }
    }

    /** Sends a terminated event to VS Code. */
    public void notifyTerminated() {
        if (eventCallback != null) {
            eventCallback.onTerminated();
        }
    }

    /** Sends an output event to VS Code. */
    public void notifyOutput(String category, String output) {
        if (eventCallback != null) {
            eventCallback.onOutput(category, output);
        }
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

    /** Callback interface for debug events. */
    public interface DebugEventCallback {
        void onStopped(String reason, int threadId);

        void onContinued(int threadId);

        void onTerminated();

        void onOutput(String category, String output);
    }
}
