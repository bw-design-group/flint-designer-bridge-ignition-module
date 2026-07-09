package dev.bwdesigngroup.flint.designer.handlers;

import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import dev.bwdesigngroup.flint.common.FlintConstants;
import dev.bwdesigngroup.flint.common.debug.DebugEvent;
import dev.bwdesigngroup.flint.common.debug.DebugEventBatch;
import dev.bwdesigngroup.flint.common.protocol.ErrorCodes;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcRequest;
import dev.bwdesigngroup.flint.common.protocol.JsonRpcResponse;
import dev.bwdesigngroup.flint.common.protocol.methods.debug.*;
import dev.bwdesigngroup.flint.designer.debug.DebugCommand;
import dev.bwdesigngroup.flint.designer.debug.DebugSession;
import dev.bwdesigngroup.flint.designer.debug.DebugState;
import dev.bwdesigngroup.flint.designer.platform.PlatformFactory;
import dev.bwdesigngroup.flint.designer.rpc.GatewayRpcClient;
import dev.bwdesigngroup.flint.designer.server.FlintWebSocketHandler;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.python.core.Py;
import org.python.core.PyFile;
import org.python.core.PyObject;
import org.python.core.PySystemState;
import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for debug-related JSON-RPC methods. Manages debug sessions and routes debug commands.
 * Supports Designer scope (local) and Gateway/Perspective scope (remote via RPC).
 */
public class DebugScriptHandler implements MethodHandler {
    private static final Logger logger = LoggerFactory.getLogger("flint.Designer.Debug");
    private static final long EVENT_POLL_INTERVAL_MS =
            100; // Poll every 100ms for responsive debugging

    private final FlintWebSocketHandler handler;
    private final DesignerContext context;
    private final GatewayRpcClient gatewayRpcClient;

    // Local Designer sessions
    private final Map<String, DebugSession> sessions = new ConcurrentHashMap<>();

    // Remote Gateway/Perspective sessions - just track session IDs and their scope info
    private final Map<String, RemoteSessionInfo> remoteSessions = new ConcurrentHashMap<>();
    private final Set<String> activeRemotePolling = ConcurrentHashMap.newKeySet();

    private final ExecutorService debugExecutor;
    private final ScheduledExecutorService eventPollingExecutor;

    // Python debugger class and helper functions, loaded once
    private PyObject flintDebuggerClass;
    private PyObject getVariablesFromDictFunc;
    private PyObject getVariablesFromObjectFunc;
    private PyObject evaluateExpressionFunc;

    /** Info about a remote debug session running in Gateway/Perspective scope. */
    private static class RemoteSessionInfo {
        final String scope;
        final DebugStartSessionParams originalParams;

        RemoteSessionInfo(String scope, DebugStartSessionParams params) {
            this.scope = scope;
            this.originalParams = params;
        }
    }

    public DebugScriptHandler(FlintWebSocketHandler handler) {
        this.handler = handler;
        this.context = handler.getContext();
        this.gatewayRpcClient = PlatformFactory.createGatewayRpcClient();
        this.gatewayRpcClient.initialize();

        this.debugExecutor =
                Executors.newCachedThreadPool(
                        r -> {
                            Thread t = new Thread(r, "FlintDebugExecutor");
                            t.setDaemon(true);
                            return t;
                        });

        this.eventPollingExecutor =
                Executors.newScheduledThreadPool(
                        2,
                        r -> {
                            Thread t = new Thread(r, "FlintDebugEventPoller");
                            t.setDaemon(true);
                            return t;
                        });

        // Load the Python debugger module
        loadDebuggerModule();
    }

    private void loadDebuggerModule() {
        try {
            // Load the flint_debugger.py from resources
            InputStream is = getClass().getResourceAsStream("/jython/flint_debugger.py");
            if (is == null) {
                logger.error("Could not find flint_debugger.py resource");
                return;
            }

            PythonInterpreter interpreter = new PythonInterpreter();
            interpreter.execfile(is, "flint_debugger.py");

            flintDebuggerClass = interpreter.get("FlintDebugger");
            getVariablesFromDictFunc = interpreter.get("get_variables_from_dict");
            getVariablesFromObjectFunc = interpreter.get("get_variables_from_object");
            evaluateExpressionFunc = interpreter.get("evaluate_expression");

            logger.info("Loaded flint_debugger module");
        } catch (Exception e) {
            logger.error("Failed to load flint_debugger module", e);
        }
    }

    @Override
    public JsonRpcResponse handle(JsonRpcRequest request) {
        String method = request.getMethod();
        Object id = request.getId();

        try {
            switch (method) {
                case FlintConstants.METHOD_DEBUG_START_SESSION:
                    return handleStartSession(request);
                case FlintConstants.METHOD_DEBUG_STOP_SESSION:
                    return handleStopSession(request);
                case FlintConstants.METHOD_DEBUG_SET_BREAKPOINTS:
                    return handleSetBreakpoints(request);
                case FlintConstants.METHOD_DEBUG_RUN:
                    return handleRun(request);
                case FlintConstants.METHOD_DEBUG_CONTINUE:
                    return handleContinue(request);
                case FlintConstants.METHOD_DEBUG_STEP_OVER:
                    return handleStepOver(request);
                case FlintConstants.METHOD_DEBUG_STEP_INTO:
                    return handleStepInto(request);
                case FlintConstants.METHOD_DEBUG_STEP_OUT:
                    return handleStepOut(request);
                case FlintConstants.METHOD_DEBUG_PAUSE:
                    return handlePause(request);
                case FlintConstants.METHOD_DEBUG_GET_STACK_TRACE:
                    return handleGetStackTrace(request);
                case FlintConstants.METHOD_DEBUG_GET_SCOPES:
                    return handleGetScopes(request);
                case FlintConstants.METHOD_DEBUG_GET_VARIABLES:
                    return handleGetVariables(request);
                case FlintConstants.METHOD_DEBUG_EVALUATE:
                    return handleEvaluate(request);
                default:
                    return JsonRpcResponse.error(
                            ErrorCodes.METHOD_NOT_FOUND, "Unknown debug method: " + method, id);
            }
        } catch (Exception e) {
            logger.error("Error handling debug method {}: {}", method, e.getMessage(), e);
            return JsonRpcResponse.error(
                    ErrorCodes.INTERNAL_ERROR, "Debug error: " + e.getMessage(), id);
        }
    }

    private JsonRpcResponse handleStartSession(JsonRpcRequest request) {
        Object id = request.getId();

        DebugStartSessionParams params;
        try {
            params = handler.getGson().fromJson(request.getParams(), DebugStartSessionParams.class);
        } catch (Exception e) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Invalid params: " + e.getMessage(), id);
        }

        if (params == null || params.getCode() == null || params.getCode().isEmpty()) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing required parameter: code", id);
        }

        // Check if this is a remote scope (gateway or perspective)
        if (params.isRemoteScope()) {
            return handleStartRemoteSession(params, id);
        }

        // Create local Designer debug session
        return handleStartLocalSession(params, id);
    }

    /** Starts a local Designer-scope debug session. */
    private JsonRpcResponse handleStartLocalSession(DebugStartSessionParams params, Object id) {
        DebugSession session =
                new DebugSession(params.getCode(), params.getFilePath(), params.getModulePath());

        // Set up event callback to send events to VS Code
        session.setEventCallback(
                new DebugSession.DebugEventCallback() {
                    @Override
                    public void onStopped(String reason, int threadId) {
                        sendDebugEvent("stopped", DebugStoppedEvent.breakpoint(threadId));
                    }

                    @Override
                    public void onContinued(int threadId) {
                        // Send continued event if needed
                    }

                    @Override
                    public void onTerminated() {
                        sendDebugEvent("terminated", null);
                    }

                    @Override
                    public void onOutput(String category, String output) {
                        Map<String, Object> body = new HashMap<>();
                        body.put("category", category);
                        body.put("output", output);
                        sendDebugEvent("output", body);
                    }
                });

        sessions.put(session.getSessionId(), session);

        logger.info(
                "Created local debug session: {} (waiting for debug.run)", session.getSessionId());
        return JsonRpcResponse.success(DebugStartSessionResult.success(session.getSessionId()), id);
    }

    /** Starts a remote Gateway/Perspective-scope debug session via RPC. */
    private JsonRpcResponse handleStartRemoteSession(DebugStartSessionParams params, Object id) {
        String scope = params.getEffectiveScope();

        logger.info("Starting remote debug session (scope={})", scope);

        try {
            DebugStartSessionResult result =
                    gatewayRpcClient
                            .getRpc()
                            .debugStartSession(
                                    params.getCode(),
                                    params.getFilePath(),
                                    params.getModulePath(),
                                    scope,
                                    params.getPerspectiveSessionId(),
                                    params.getPerspectivePageId(),
                                    params.getPerspectiveViewInstanceId(),
                                    params.getPerspectiveComponentPath());

            if (result.isSuccess()) {
                String sessionId = result.getSessionId();
                remoteSessions.put(sessionId, new RemoteSessionInfo(scope, params));
                logger.info("Created remote debug session: {} (scope={})", sessionId, scope);
                return JsonRpcResponse.success(result, id);
            } else {
                return JsonRpcResponse.error(
                        ErrorCodes.INTERNAL_ERROR,
                        "Failed to start remote debug session: " + result.getError(),
                        id);
            }

        } catch (Exception e) {
            logger.error("Failed to start remote debug session", e);
            return JsonRpcResponse.error(
                    ErrorCodes.GATEWAY_RPC_ERROR,
                    "Failed to start remote debug session: " + e.getMessage(),
                    id);
        }
    }

    /** Starts polling for events from a remote debug session. */
    private void startRemoteEventPolling(String sessionId) {
        if (activeRemotePolling.contains(sessionId)) {
            return; // Already polling
        }

        activeRemotePolling.add(sessionId);
        logger.debug("Starting event polling for remote session: {}", sessionId);

        eventPollingExecutor.submit(() -> pollRemoteEvents(sessionId));
    }

    /** Polls for events from a remote debug session and forwards them to VS Code. */
    private void pollRemoteEvents(String sessionId) {
        while (activeRemotePolling.contains(sessionId)) {
            try {
                DebugEventBatch batch =
                        gatewayRpcClient
                                .getRpc()
                                .debugPollEvents(sessionId, EVENT_POLL_INTERVAL_MS);

                for (DebugEvent event : batch.getEvents()) {
                    forwardRemoteDebugEvent(event);
                }

                // If session is no longer active, stop polling
                if (!batch.isSessionActive()) {
                    logger.debug(
                            "Remote session {} is no longer active, stopping event polling",
                            sessionId);
                    stopRemoteEventPolling(sessionId);
                    break;
                }

            } catch (Exception e) {
                logger.error(
                        "Error polling remote debug events for session {}: {}",
                        sessionId,
                        e.getMessage());
                // Brief pause on error to avoid spinning
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /** Forwards a remote debug event to VS Code. */
    private void forwardRemoteDebugEvent(DebugEvent event) {
        Map<String, Object> body = event.getBody();

        switch (event.getType()) {
            case STOPPED:
                sendDebugEvent(
                        "stopped",
                        DebugStoppedEvent.breakpoint(
                                body.get("threadId") != null
                                        ? ((Number) body.get("threadId")).intValue()
                                        : 1));
                break;
            case CONTINUED:
                // VS Code usually infers this, but we can send if needed
                break;
            case TERMINATED:
                sendDebugEvent("terminated", null);
                break;
            case OUTPUT:
                sendDebugEvent("output", body);
                break;
            default:
                logger.debug("Unknown remote debug event type: {}", event.getType());
        }
    }

    /** Stops polling for events from a remote debug session. */
    private void stopRemoteEventPolling(String sessionId) {
        activeRemotePolling.remove(sessionId);
        remoteSessions.remove(sessionId);
    }

    /** Checks if a session ID is a remote session. */
    private boolean isRemoteSession(String sessionId) {
        return remoteSessions.containsKey(sessionId);
    }

    private void executeDebugSession(DebugSession session) {
        PyObject debugger = null;
        PrintStream originalJavaOut = null;
        PrintStream originalJavaErr = null;
        PrintStream captureOut = null;
        PrintStream captureErr = null;
        PySystemState pyState = null;
        PyObject originalPyStdout = null;
        PyObject originalPyStderr = null;

        try {
            session.setState(DebugState.RUNNING);

            if (flintDebuggerClass == null) {
                session.setState(DebugState.ERROR);
                logger.error("Debugger module not loaded");
                return;
            }

            ScriptManager scriptManager = context.getScriptManager();
            PyObject locals = scriptManager.createLocalsMap();

            // Create debugger instance
            debugger = flintDebuggerClass.__call__(Py.java2py(session));

            // Enable tracing BEFORE running code so breakpoints work
            debugger.invoke("enable_tracing");

            // Run the code using ScriptManager for proper namespace (project scripts accessible)
            String code = session.getCode();
            String filename =
                    session.getFilePath() != null ? session.getFilePath() : "<flint-debug>";

            // Save original Java System.out/err
            originalJavaOut = System.out;
            originalJavaErr = System.err;

            // Create capture streams that write to both original console AND notify VS Code
            captureOut =
                    new PrintStream(new OutputCaptureStream(session, "stdout", originalJavaOut));
            captureErr =
                    new PrintStream(new OutputCaptureStream(session, "stderr", originalJavaErr));

            // Redirect Java's System.out/System.err
            System.setOut(captureOut);
            System.setErr(captureErr);

            // ALSO redirect Jython's sys.stdout/sys.stderr
            // This is necessary because Jython's sys.stdout caches a reference to the old
            // System.out
            pyState = Py.getSystemState();
            originalPyStdout = pyState.stdout;
            originalPyStderr = pyState.stderr;
            pyState.stdout = new PyFile(captureOut);
            pyState.stderr = new PyFile(captureErr);

            // Inject the session into locals so the wrapper code can access it
            locals.__setitem__(Py.newString("_flint_debug_session"), Py.java2py(session));

            // Prepend stdout capture code to the user's script
            // This runs WITHIN ScriptManager's execution context, so it redirects the right
            // sys.stdout
            String wrapperCode =
                    "import sys as _flint_sys\n"
                            + "_flint_original_stdout = _flint_sys.stdout\n"
                            + "_flint_original_stderr = _flint_sys.stderr\n"
                            + "class _FlintOutputCapture:\n"
                            + "    def __init__(self, original, session, category):\n"
                            + "        self.original = original\n"
                            + "        self.session = session\n"
                            + "        self.category = category\n"
                            + "    def write(self, text):\n"
                            + "        if self.original:\n"
                            + "            self.original.write(text)\n"
                            + "        if text:\n"
                            + "            try:\n"
                            + "                self.session.notifyOutput(self.category, text)\n"
                            + "            except:\n"
                            + "                pass\n"
                            + "    def flush(self):\n"
                            + "        if self.original and hasattr(self.original, 'flush'):\n"
                            + "            self.original.flush()\n"
                            + "_flint_sys.stdout = _FlintOutputCapture(_flint_original_stdout, _flint_debug_session, 'stdout')\n"
                            + "_flint_sys.stderr = _FlintOutputCapture(_flint_original_stderr, _flint_debug_session, 'stderr')\n"
                            + "del _FlintOutputCapture\n"; // Clean up the class from namespace

            // Combine wrapper with user code
            String wrappedCode = wrapperCode + code;
            logger.debug(
                    "Debug session {} - stdout/stderr capture enabled, sending output to VS Code",
                    session.getSessionId());

            try {
                scriptManager.runCode(wrappedCode, locals, filename);
            } catch (Exception e) {
                // Notify about the error via debug output
                session.notifyOutput("stderr", e.getMessage());
                throw e;
            }

            session.setState(DebugState.COMPLETED);
            logger.info("Debug session completed: {}", session.getSessionId());

        } catch (Exception e) {
            session.setState(DebugState.ERROR);
            logger.error("Debug session error: {}", e.getMessage(), e);
        } finally {
            // Restore Jython's sys.stdout/sys.stderr FIRST (before Java's)
            if (pyState != null && originalPyStdout != null) {
                pyState.stdout = originalPyStdout;
            }
            if (pyState != null && originalPyStderr != null) {
                pyState.stderr = originalPyStderr;
            }

            // Restore original Java System.out/System.err
            if (originalJavaOut != null) {
                System.setOut(originalJavaOut);
            }
            if (originalJavaErr != null) {
                System.setErr(originalJavaErr);
            }

            // Flush capture streams (don't close - they wrap the original streams)
            if (captureOut != null) {
                captureOut.flush();
            }
            if (captureErr != null) {
                captureErr.flush();
            }

            // Always disable tracing
            if (debugger != null) {
                try {
                    debugger.invoke("disable_tracing");
                } catch (Exception e) {
                    logger.debug("Error disabling tracing: {}", e.getMessage());
                }
            }
            sessions.remove(session.getSessionId());
        }
    }

    private JsonRpcResponse handleStopSession(JsonRpcRequest request) {
        Object id = request.getId();

        DebugControlParams params;
        try {
            params = handler.getGson().fromJson(request.getParams(), DebugControlParams.class);
        } catch (Exception e) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Invalid params: " + e.getMessage(), id);
        }

        if (params == null || params.getSessionId() == null) {
            return JsonRpcResponse.error(ErrorCodes.INVALID_PARAMS, "Missing sessionId", id);
        }

        String sessionId = params.getSessionId();

        // Check if remote session
        if (isRemoteSession(sessionId)) {
            stopRemoteEventPolling(sessionId);
            gatewayRpcClient.getRpc().debugStopSession(sessionId);
            logger.info("Stopped remote debug session: {}", sessionId);
            return JsonRpcResponse.success(DebugControlResult.success(), id);
        }

        // Local session
        DebugSession session = sessions.get(sessionId);
        if (session == null) {
            return JsonRpcResponse.error(ErrorCodes.INVALID_PARAMS, "Session not found", id);
        }

        session.sendCommand(DebugCommand.terminate());
        session.setState(DebugState.TERMINATED);
        sessions.remove(sessionId);

        logger.info("Stopped local debug session: {}", sessionId);
        return JsonRpcResponse.success(DebugControlResult.success(), id);
    }

    private JsonRpcResponse handleSetBreakpoints(JsonRpcRequest request) {
        Object id = request.getId();

        DebugSetBreakpointsParams params;
        try {
            params =
                    handler.getGson()
                            .fromJson(request.getParams(), DebugSetBreakpointsParams.class);
        } catch (Exception e) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Invalid params: " + e.getMessage(), id);
        }

        if (params == null || params.getSessionId() == null) {
            return JsonRpcResponse.error(ErrorCodes.INVALID_PARAMS, "Missing sessionId", id);
        }

        String sessionId = params.getSessionId();
        List<DebugSetBreakpointsParams.BreakpointInfo> bps = params.getBreakpoints();
        if (bps == null) {
            bps = new ArrayList<>();
        }

        // Check if remote session
        if (isRemoteSession(sessionId)) {
            List<Integer> ids =
                    gatewayRpcClient
                            .getRpc()
                            .debugSetBreakpoints(sessionId, params.getFilePath(), bps);

            // Build verified breakpoints
            List<DebugSetBreakpointsResult.VerifiedBreakpoint> verified = new ArrayList<>();
            for (int i = 0; i < bps.size(); i++) {
                DebugSetBreakpointsParams.BreakpointInfo bp = bps.get(i);
                int bpId = i < ids.size() ? ids.get(i) : i + 1;
                verified.add(
                        new DebugSetBreakpointsResult.VerifiedBreakpoint(bpId, true, bp.getLine()));
            }

            return JsonRpcResponse.success(new DebugSetBreakpointsResult(verified), id);
        }

        // Local session
        DebugSession session = sessions.get(sessionId);
        if (session == null) {
            return JsonRpcResponse.error(ErrorCodes.INVALID_PARAMS, "Session not found", id);
        }

        List<Integer> ids = session.setBreakpoints(params.getFilePath(), bps);

        // Build verified breakpoints
        List<DebugSetBreakpointsResult.VerifiedBreakpoint> verified = new ArrayList<>();
        for (int i = 0; i < bps.size(); i++) {
            DebugSetBreakpointsParams.BreakpointInfo bp = bps.get(i);
            verified.add(
                    new DebugSetBreakpointsResult.VerifiedBreakpoint(
                            ids.get(i), true, bp.getLine()));
        }

        return JsonRpcResponse.success(new DebugSetBreakpointsResult(verified), id);
    }

    private JsonRpcResponse handleRun(JsonRpcRequest request) {
        Object id = request.getId();

        DebugControlParams params;
        try {
            params = handler.getGson().fromJson(request.getParams(), DebugControlParams.class);
        } catch (Exception e) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Invalid params: " + e.getMessage(), id);
        }

        if (params == null || params.getSessionId() == null) {
            return JsonRpcResponse.error(ErrorCodes.INVALID_PARAMS, "Missing sessionId", id);
        }

        String sessionId = params.getSessionId();

        // Check if remote session
        if (isRemoteSession(sessionId)) {
            gatewayRpcClient.getRpc().debugRun(sessionId);
            // Start polling for events from the remote session
            startRemoteEventPolling(sessionId);
            logger.info("Started remote debug session execution: {}", sessionId);
            return JsonRpcResponse.success(DebugControlResult.success(), id);
        }

        // Local session
        DebugSession session = sessions.get(sessionId);
        if (session == null) {
            return JsonRpcResponse.error(ErrorCodes.INVALID_PARAMS, "Session not found", id);
        }

        if (session.getState() != DebugState.NOT_STARTED) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Session already running or completed", id);
        }

        // Start execution in background now that breakpoints are set
        debugExecutor.submit(() -> executeDebugSession(session));

        logger.info("Started local debug session execution: {}", sessionId);
        return JsonRpcResponse.success(DebugControlResult.success(), id);
    }

    private JsonRpcResponse handleContinue(JsonRpcRequest request) {
        return handleDebugCommand(request, DebugCommand.continueExecution());
    }

    private JsonRpcResponse handleStepOver(JsonRpcRequest request) {
        return handleDebugCommand(request, DebugCommand.stepOver());
    }

    private JsonRpcResponse handleStepInto(JsonRpcRequest request) {
        return handleDebugCommand(request, DebugCommand.stepInto());
    }

    private JsonRpcResponse handleStepOut(JsonRpcRequest request) {
        return handleDebugCommand(request, DebugCommand.stepOut());
    }

    private JsonRpcResponse handlePause(JsonRpcRequest request) {
        return handleDebugCommand(request, DebugCommand.pause());
    }

    private JsonRpcResponse handleDebugCommand(JsonRpcRequest request, DebugCommand command) {
        Object id = request.getId();

        DebugControlParams params;
        try {
            params = handler.getGson().fromJson(request.getParams(), DebugControlParams.class);
        } catch (Exception e) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Invalid params: " + e.getMessage(), id);
        }

        if (params == null || params.getSessionId() == null) {
            return JsonRpcResponse.error(ErrorCodes.INVALID_PARAMS, "Missing sessionId", id);
        }

        String sessionId = params.getSessionId();

        // Check if remote session
        if (isRemoteSession(sessionId)) {
            String commandName = command.getType().name().toLowerCase();
            gatewayRpcClient.getRpc().debugSendCommand(sessionId, commandName);
            return JsonRpcResponse.success(DebugControlResult.success(), id);
        }

        // Local session
        DebugSession session = sessions.get(sessionId);
        if (session == null) {
            return JsonRpcResponse.error(ErrorCodes.INVALID_PARAMS, "Session not found", id);
        }

        session.sendCommand(command);
        return JsonRpcResponse.success(DebugControlResult.success(), id);
    }

    private JsonRpcResponse handleGetStackTrace(JsonRpcRequest request) {
        Object id = request.getId();

        DebugStackTraceParams params;
        try {
            params = handler.getGson().fromJson(request.getParams(), DebugStackTraceParams.class);
        } catch (Exception e) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Invalid params: " + e.getMessage(), id);
        }

        if (params == null || params.getSessionId() == null) {
            return JsonRpcResponse.error(ErrorCodes.INVALID_PARAMS, "Missing sessionId", id);
        }

        String sessionId = params.getSessionId();

        // Check if remote session
        if (isRemoteSession(sessionId)) {
            DebugStackTraceResult result = gatewayRpcClient.getRpc().debugGetStackTrace(sessionId);
            return JsonRpcResponse.success(result, id);
        }

        // Local session
        DebugSession session = sessions.get(sessionId);
        if (session == null) {
            return JsonRpcResponse.error(ErrorCodes.INVALID_PARAMS, "Session not found", id);
        }

        List<DebugStackTraceResult.StackFrame> frames = session.getCurrentStackFrames();
        return JsonRpcResponse.success(new DebugStackTraceResult(frames, frames.size()), id);
    }

    private JsonRpcResponse handleGetScopes(JsonRpcRequest request) {
        Object id = request.getId();

        DebugScopesParams params;
        try {
            params = handler.getGson().fromJson(request.getParams(), DebugScopesParams.class);
        } catch (Exception e) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Invalid params: " + e.getMessage(), id);
        }

        if (params == null || params.getSessionId() == null) {
            return JsonRpcResponse.error(ErrorCodes.INVALID_PARAMS, "Missing sessionId", id);
        }

        String sessionId = params.getSessionId();

        // Check if remote session
        if (isRemoteSession(sessionId)) {
            DebugScopesResult result =
                    gatewayRpcClient.getRpc().debugGetScopes(sessionId, params.getFrameId());
            return JsonRpcResponse.success(result, id);
        }

        // Local session
        DebugSession session = sessions.get(sessionId);
        if (session == null) {
            return JsonRpcResponse.error(ErrorCodes.INVALID_PARAMS, "Session not found", id);
        }

        // Get frame info
        DebugSession.FrameInfo frameInfo = session.getFrameReference(params.getFrameId());

        List<DebugScopesResult.Scope> scopes = new ArrayList<>();

        if (frameInfo != null) {
            // Local scope
            int localRef = session.registerVariableReference(frameInfo.locals);
            scopes.add(new DebugScopesResult.Scope("Locals", localRef, false));

            // Global scope
            int globalRef = session.registerVariableReference(frameInfo.globals);
            scopes.add(new DebugScopesResult.Scope("Globals", globalRef, true));
        }

        return JsonRpcResponse.success(new DebugScopesResult(scopes), id);
    }

    private JsonRpcResponse handleGetVariables(JsonRpcRequest request) {
        Object id = request.getId();

        DebugVariablesParams params;
        try {
            params = handler.getGson().fromJson(request.getParams(), DebugVariablesParams.class);
        } catch (Exception e) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Invalid params: " + e.getMessage(), id);
        }

        if (params == null || params.getSessionId() == null) {
            return JsonRpcResponse.error(ErrorCodes.INVALID_PARAMS, "Missing sessionId", id);
        }

        String sessionId = params.getSessionId();

        // Check if remote session
        if (isRemoteSession(sessionId)) {
            DebugVariablesResult result =
                    gatewayRpcClient
                            .getRpc()
                            .debugGetVariables(sessionId, params.getVariablesReference());
            return JsonRpcResponse.success(result, id);
        }

        // Local session
        DebugSession session = sessions.get(sessionId);
        if (session == null) {
            return JsonRpcResponse.error(ErrorCodes.INVALID_PARAMS, "Session not found", id);
        }

        PyObject obj = session.getVariableReference(params.getVariablesReference());
        if (obj == null) {
            return JsonRpcResponse.success(new DebugVariablesResult(new ArrayList<>()), id);
        }

        // Use Python helper to get variables
        List<DebugVariablesResult.Variable> variables = new ArrayList<>();

        try {
            PyObject pyVars;
            if (obj.__findattr__("items") != null) {
                // Dictionary-like object
                pyVars = getVariablesFromDictFunc.__call__(obj, Py.java2py(session));
            } else {
                // Other object
                pyVars = getVariablesFromObjectFunc.__call__(obj, Py.java2py(session));
            }

            // Convert Java ArrayList from Jython
            @SuppressWarnings("unchecked")
            List<DebugVariablesResult.Variable> varList =
                    (List<DebugVariablesResult.Variable>) pyVars.__tojava__(List.class);
            variables.addAll(varList);

        } catch (Exception e) {
            logger.error("Error getting variables: {}", e.getMessage(), e);
        }

        return JsonRpcResponse.success(new DebugVariablesResult(variables), id);
    }

    private JsonRpcResponse handleEvaluate(JsonRpcRequest request) {
        Object id = request.getId();

        DebugEvaluateParams params;
        try {
            params = handler.getGson().fromJson(request.getParams(), DebugEvaluateParams.class);
        } catch (Exception e) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Invalid params: " + e.getMessage(), id);
        }

        if (params == null || params.getSessionId() == null || params.getExpression() == null) {
            return JsonRpcResponse.error(
                    ErrorCodes.INVALID_PARAMS, "Missing sessionId or expression", id);
        }

        String sessionId = params.getSessionId();

        // Check if remote session
        if (isRemoteSession(sessionId)) {
            DebugEvaluateResult result =
                    gatewayRpcClient
                            .getRpc()
                            .debugEvaluate(sessionId, params.getExpression(), params.getFrameId());
            return JsonRpcResponse.success(result, id);
        }

        // Local session
        DebugSession session = sessions.get(sessionId);
        if (session == null) {
            return JsonRpcResponse.error(ErrorCodes.INVALID_PARAMS, "Session not found", id);
        }

        try {
            // Get frame info if frame ID provided
            PyObject locals = Py.None;
            PyObject globals = Py.None;

            if (params.getFrameId() != null) {
                DebugSession.FrameInfo frameInfo = session.getFrameReference(params.getFrameId());
                if (frameInfo != null) {
                    locals = frameInfo.locals;
                    globals = frameInfo.globals;
                }
            }

            // Evaluate expression
            PyObject result =
                    evaluateExpressionFunc.__call__(
                            Py.java2py(params.getExpression()), locals, globals);

            String resultStr = result != null ? result.toString() : "None";
            String resultType = result != null ? result.getType().getName() : "NoneType";

            DebugEvaluateResult evalResult = new DebugEvaluateResult(resultStr, resultType);

            // Check if result can be expanded
            if (result != null && result.__findattr__("__iter__") != null) {
                int ref = session.registerVariableReference(result);
                evalResult.setVariablesReference(ref);
            }

            return JsonRpcResponse.success(evalResult, id);

        } catch (Exception e) {
            return JsonRpcResponse.error(
                    ErrorCodes.INTERNAL_ERROR, "Evaluation error: " + e.getMessage(), id);
        }
    }

    private void sendDebugEvent(String eventType, Object data) {
        // Send as JSON-RPC notification (no id = notification)
        JsonRpcRequest notification = new JsonRpcRequest();
        notification.setMethod("debug.event." + eventType);
        if (data != null) {
            notification.setParams(handler.getGson().toJsonTree(data));
        }

        // Send the notification as a JSON message
        String json = handler.getGson().toJson(notification);
        logger.debug("Sending debug event: {}", json);
        handler.getConnection().send(json);
    }

    /** Shuts down the debug handler. */
    public void shutdown() {
        // Stop all remote event polling
        activeRemotePolling.clear();

        // Terminate all remote sessions
        for (String sessionId : remoteSessions.keySet()) {
            try {
                gatewayRpcClient.getRpc().debugStopSession(sessionId);
            } catch (Exception e) {
                logger.debug("Error stopping remote session {}: {}", sessionId, e.getMessage());
            }
        }
        remoteSessions.clear();

        // Terminate all local sessions
        for (DebugSession session : sessions.values()) {
            session.sendCommand(DebugCommand.terminate());
        }
        sessions.clear();

        // Shutdown executors
        debugExecutor.shutdown();
        eventPollingExecutor.shutdown();

        try {
            if (!debugExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                debugExecutor.shutdownNow();
            }
            if (!eventPollingExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                eventPollingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            debugExecutor.shutdownNow();
            eventPollingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Output stream that captures output and sends it to VS Code debug console. This is a fallback
     * for Java-level output; Python print statements are captured via code injection in
     * executeDebugSession().
     */
    private static class OutputCaptureStream extends OutputStream {
        private final DebugSession session;
        private final String category;
        private final OutputStream original;
        private final StringBuilder buffer = new StringBuilder();

        // Pattern to detect logging framework output (has timestamp at start)
        private static final java.util.regex.Pattern LOG_PATTERN =
                java.util.regex.Pattern.compile("^\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+\\[");

        public OutputCaptureStream(DebugSession session, String category, OutputStream original) {
            this.session = session;
            this.category = category;
            this.original = original;
        }

        @Override
        public void write(int b) {
            // Write to original stream (Designer console)
            if (original != null) {
                try {
                    original.write(b);
                } catch (Exception e) {
                    // Ignore
                }
            }

            // Buffer the character
            char c = (char) b;
            buffer.append(c);

            // Flush on newlines for responsive output
            if (c == '\n') {
                flushBuffer();
            }
        }

        @Override
        public void write(byte[] b, int off, int len) {
            // Write to original stream (Designer console)
            if (original != null) {
                try {
                    original.write(b, off, len);
                } catch (Exception e) {
                    // Ignore
                }
            }

            // Buffer the text
            String text = new String(b, off, len);
            buffer.append(text);

            // Flush on newlines
            if (text.contains("\n")) {
                flushBuffer();
            }
        }

        @Override
        public void flush() {
            if (original != null) {
                try {
                    original.flush();
                } catch (Exception e) {
                    // Ignore
                }
            }
            flushBuffer();
        }

        @Override
        public void close() {
            flushBuffer();
        }

        private void flushBuffer() {
            if (buffer.length() > 0) {
                String text = buffer.toString();
                buffer.setLength(0);

                // Skip logging framework output and internal debugger messages
                // We only want to send actual script output to VS Code
                if (!text.startsWith("[FlintDebugger]") && !LOG_PATTERN.matcher(text).find()) {
                    try {
                        session.notifyOutput(category, text);
                    } catch (Exception e) {
                        // Silently ignore - don't want to spam logs
                    }
                }
            }
        }
    }
}
