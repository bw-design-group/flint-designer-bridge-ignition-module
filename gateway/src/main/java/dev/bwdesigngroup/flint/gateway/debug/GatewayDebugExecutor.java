package dev.bwdesigngroup.flint.gateway.debug;

import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.perspective.gateway.model.PageModel;
import com.inductiveautomation.perspective.gateway.session.InternalSession;
import dev.bwdesigngroup.flint.common.debug.DebugCommand;
import dev.bwdesigngroup.flint.common.debug.DebugEventBatch;
import dev.bwdesigngroup.flint.common.debug.DebugState;
import dev.bwdesigngroup.flint.common.protocol.methods.debug.DebugEvaluateResult;
import dev.bwdesigngroup.flint.common.protocol.methods.debug.DebugScopesResult;
import dev.bwdesigngroup.flint.common.protocol.methods.debug.DebugSetBreakpointsParams.BreakpointInfo;
import dev.bwdesigngroup.flint.common.protocol.methods.debug.DebugStackTraceResult;
import dev.bwdesigngroup.flint.common.protocol.methods.debug.DebugVariablesResult;
import dev.bwdesigngroup.flint.gateway.perspective.PerspectiveSessionInspector;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.python.core.Py;
import org.python.core.PyFile;
import org.python.core.PyObject;
import org.python.core.PySystemState;
import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes debug sessions in the Gateway's Jython interpreter. Handles both Gateway-scope and
 * Perspective-scope debugging.
 */
public class GatewayDebugExecutor {
    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Debug");

    private final GatewayContext context;
    private final PerspectiveSessionInspector perspectiveInspector;
    private final ExecutorService debugExecutor;

    // Active debug sessions: sessionId -> session
    private final Map<String, GatewayDebugSession> sessions = new ConcurrentHashMap<>();

    // Python debugger class, loaded once
    private PyObject flintDebuggerClass;
    private boolean debuggerModuleLoaded = false;

    public GatewayDebugExecutor(
            GatewayContext context, PerspectiveSessionInspector perspectiveInspector) {
        this.context = context;
        this.perspectiveInspector = perspectiveInspector;
        this.debugExecutor =
                Executors.newCachedThreadPool(
                        r -> {
                            Thread t = new Thread(r, "FlintGatewayDebugExecutor");
                            t.setDaemon(true);
                            return t;
                        });

        // Load the Python debugger module
        loadDebuggerModule();
    }

    private void loadDebuggerModule() {
        try {
            // Load the flint_debugger.py from Gateway resources
            InputStream is = getClass().getResourceAsStream("/jython/flint_debugger.py");
            if (is == null) {
                logger.error("Could not find flint_debugger.py resource in Gateway module");
                return;
            }

            try (PythonInterpreter interpreter = new PythonInterpreter()) {
                interpreter.execfile(is, "flint_debugger.py");
                flintDebuggerClass = interpreter.get("FlintDebugger");
            }

            debuggerModuleLoaded = true;
            logger.info("Loaded flint_debugger module for Gateway debugging");
        } catch (Exception e) {
            logger.error("Failed to load flint_debugger module", e);
        }
    }

    // ========================== Session Management ==========================

    /**
     * Creates a new debug session.
     *
     * @return The session ID
     */
    public GatewayDebugSession createSession(
            String code, String filePath, String modulePath, String scope) {
        GatewayDebugSession session = new GatewayDebugSession(code, filePath, modulePath, scope);
        sessions.put(session.getSessionId(), session);
        logger.info("Created Gateway debug session: {} (scope={})", session.getSessionId(), scope);
        return session;
    }

    /** Gets an active session by ID. */
    public GatewayDebugSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /** Stops and removes a debug session. */
    public void stopSession(String sessionId) {
        GatewayDebugSession session = sessions.get(sessionId);
        if (session != null) {
            session.sendCommand(DebugCommand.terminate());
            session.setState(DebugState.TERMINATED);
            sessions.remove(sessionId);
            logger.info("Stopped Gateway debug session: {}", sessionId);
        }
    }

    /** Sets breakpoints for a session. */
    public List<Integer> setBreakpoints(
            String sessionId, String filePath, List<BreakpointInfo> breakpoints) {
        GatewayDebugSession session = sessions.get(sessionId);
        if (session == null) {
            return new ArrayList<>();
        }
        return session.setBreakpoints(filePath, breakpoints);
    }

    /** Starts execution of a debug session. */
    public void runSession(String sessionId) {
        GatewayDebugSession session = sessions.get(sessionId);
        if (session == null || session.getState() != DebugState.NOT_STARTED) {
            logger.warn("Cannot run session {} - not found or already running", sessionId);
            return;
        }

        debugExecutor.submit(() -> executeDebugSession(session));
        logger.info("Started Gateway debug session execution: {}", sessionId);
    }

    /** Sends a debug command to a session. */
    public void sendCommand(String sessionId, DebugCommand command) {
        GatewayDebugSession session = sessions.get(sessionId);
        if (session != null) {
            session.sendCommand(command);
        }
    }

    /** Polls events from a session. */
    public DebugEventBatch pollEvents(String sessionId, long maxWaitMs) {
        GatewayDebugSession session = sessions.get(sessionId);
        if (session == null) {
            return new DebugEventBatch(new ArrayList<>(), false);
        }
        return session.pollEvents(maxWaitMs);
    }

    // ========================== Debug Execution ==========================

    private void executeDebugSession(GatewayDebugSession session) {
        PyObject debugger = null;
        PrintStream captureOut = null;
        PrintStream captureErr = null;
        PrintStream originalJavaOut = System.out;
        PrintStream originalJavaErr = System.err;
        PySystemState pyState = null;
        PyObject originalPyStdout = null;
        PyObject originalPyStderr = null;

        // Perspective ThreadLocals
        PageModel pageModel = null;
        InternalSession internalSession = null;

        try {
            session.setState(DebugState.RUNNING);

            if (!debuggerModuleLoaded || flintDebuggerClass == null) {
                session.setState(DebugState.ERROR);
                session.notifyOutput("stderr", "Debugger module not loaded in Gateway");
                session.notifyTerminated();
                logger.error("Debugger module not loaded");
                return;
            }

            ScriptManager scriptManager = context.getScriptManager();
            PyObject locals = scriptManager.createLocalsMap();

            // Set up Perspective context if needed
            if (session.isPerspectiveScope() && perspectiveInspector != null) {
                String perspSessionId = session.getPerspectiveSessionId();
                String pageId = session.getPerspectivePageId();

                if (perspSessionId != null && pageId != null) {
                    pageModel = perspectiveInspector.findPageModel(perspSessionId, pageId);
                    if (pageModel != null) {
                        internalSession = pageModel.session;
                        InternalSession.SESSION.set(internalSession);
                        PageModel.PAGE.set(pageModel);
                        logger.debug("Set Perspective ThreadLocals for debug session");
                    }
                }

                // Add Perspective context to locals
                perspectiveInspector.addPerspectiveContextToLocals(
                        locals,
                        perspSessionId,
                        pageId,
                        session.getPerspectiveViewInstanceId(),
                        session.getPerspectiveComponentPath());
            }

            // Create debugger instance
            debugger = flintDebuggerClass.__call__(Py.java2py(session));

            // Enable tracing BEFORE running code so breakpoints work
            debugger.invoke("enable_tracing");

            // Set up output capture
            captureOut =
                    new PrintStream(new OutputCaptureStream(session, "stdout", originalJavaOut));
            captureErr =
                    new PrintStream(new OutputCaptureStream(session, "stderr", originalJavaErr));

            System.setOut(captureOut);
            System.setErr(captureErr);

            pyState = Py.getSystemState();
            originalPyStdout = pyState.stdout;
            originalPyStderr = pyState.stderr;
            pyState.stdout = new PyFile(captureOut);
            pyState.stderr = new PyFile(captureErr);

            // Inject session into locals for wrapper code
            locals.__setitem__(Py.newString("_flint_debug_session"), Py.java2py(session));

            // Prepend stdout capture code
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
                            + "del _FlintOutputCapture\n";

            String wrappedCode = wrapperCode + session.getCode();
            String filename =
                    session.getFilePath() != null ? session.getFilePath() : "<flint-gateway-debug>";

            try {
                scriptManager.runCode(wrappedCode, locals, filename);
            } catch (Exception e) {
                session.notifyOutput("stderr", e.getMessage());
                throw e;
            }

            session.setState(DebugState.COMPLETED);
            logger.info("Gateway debug session completed: {}", session.getSessionId());

        } catch (Exception e) {
            session.setState(DebugState.ERROR);
            logger.error("Gateway debug session error: {}", e.getMessage(), e);
        } finally {
            // Restore Jython's sys.stdout/sys.stderr
            if (pyState != null && originalPyStdout != null) {
                pyState.stdout = originalPyStdout;
            }
            if (pyState != null && originalPyStderr != null) {
                pyState.stderr = originalPyStderr;
            }

            // Restore Java System.out/System.err
            System.setOut(originalJavaOut);
            System.setErr(originalJavaErr);

            // Flush capture streams
            if (captureOut != null) {
                captureOut.flush();
            }
            if (captureErr != null) {
                captureErr.flush();
            }

            // Clear Perspective ThreadLocals
            if (internalSession != null) {
                InternalSession.SESSION.remove();
            }
            if (pageModel != null) {
                PageModel.PAGE.remove();
            }

            // Disable tracing
            if (debugger != null) {
                try {
                    debugger.invoke("disable_tracing");
                } catch (Exception e) {
                    logger.debug("Error disabling tracing: {}", e.getMessage());
                }
            }

            // Notify terminated BEFORE removing session, to ensure polling gets the event
            session.notifyTerminated();
            session.setState(DebugState.TERMINATED);

            // Brief delay to allow polling to receive the terminated event
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            sessions.remove(session.getSessionId());
        }
    }

    // ========================== Inspection Methods ==========================

    /** Gets the current stack trace for a session. */
    public DebugStackTraceResult getStackTrace(String sessionId) {
        GatewayDebugSession session = sessions.get(sessionId);
        if (session == null) {
            return new DebugStackTraceResult(new ArrayList<>(), 0);
        }
        List<DebugStackTraceResult.StackFrame> frames = session.getCurrentStackFrames();
        return new DebugStackTraceResult(frames, frames.size());
    }

    /** Gets scopes for a frame. */
    public DebugScopesResult getScopes(String sessionId, int frameId) {
        GatewayDebugSession session = sessions.get(sessionId);
        if (session == null) {
            return new DebugScopesResult(new ArrayList<>());
        }

        GatewayDebugSession.FrameInfo frameInfo = session.getFrameReference(frameId);
        List<DebugScopesResult.Scope> scopes = new ArrayList<>();

        if (frameInfo != null) {
            int localRef = session.registerVariableReference(frameInfo.locals);
            scopes.add(new DebugScopesResult.Scope("Locals", localRef, false));

            int globalRef = session.registerVariableReference(frameInfo.globals);
            scopes.add(new DebugScopesResult.Scope("Globals", globalRef, true));
        }

        return new DebugScopesResult(scopes);
    }

    /**
     * Gets variables for a reference. Uses pure Java/Jython APIs to avoid classloader issues with
     * Python importing Java classes.
     */
    public DebugVariablesResult getVariables(String sessionId, int variablesReference) {
        GatewayDebugSession session = sessions.get(sessionId);
        if (session == null) {
            return new DebugVariablesResult(new ArrayList<>());
        }

        PyObject obj = session.getVariableReference(variablesReference);
        if (obj == null) {
            return new DebugVariablesResult(new ArrayList<>());
        }

        List<DebugVariablesResult.Variable> variables = new ArrayList<>();

        try {
            // Check if it's a dictionary-like object
            if (obj instanceof org.python.core.PyDictionary || obj.__findattr__("items") != null) {
                extractDictVariables(obj, session, variables);
            } else if (obj instanceof org.python.core.PyList
                    || obj instanceof org.python.core.PyTuple) {
                extractListVariables(obj, session, variables);
            } else {
                extractObjectVariables(obj, session, variables);
            }
        } catch (Exception e) {
            logger.error("Error getting variables: {}", e.getMessage(), e);
        }

        return new DebugVariablesResult(variables);
    }

    private void extractDictVariables(
            PyObject dict,
            GatewayDebugSession session,
            List<DebugVariablesResult.Variable> variables) {
        try {
            PyObject items = dict.invoke("items");
            for (PyObject item : items.asIterable()) {
                PyObject key = item.__getitem__(0);
                PyObject value = item.__getitem__(1);

                String name = key.toString();
                // Skip internal names
                if (name.startsWith("__") && name.endsWith("__")) {
                    continue;
                }

                variables.add(createVariable(name, value, session));

                if (variables.size() >= 1000) break;
            }
        } catch (Exception e) {
            logger.debug("Error extracting dict variables: {}", e.getMessage());
        }
    }

    private void extractListVariables(
            PyObject list,
            GatewayDebugSession session,
            List<DebugVariablesResult.Variable> variables) {
        try {
            int length = list.__len__();
            for (int i = 0; i < length && i < 1000; i++) {
                PyObject value = list.__getitem__(i);
                variables.add(createVariable(String.valueOf(i), value, session));
            }
        } catch (Exception e) {
            logger.debug("Error extracting list variables: {}", e.getMessage());
        }
    }

    private void extractObjectVariables(
            PyObject obj,
            GatewayDebugSession session,
            List<DebugVariablesResult.Variable> variables) {
        try {
            // Get dir() of object
            PyObject dir = Py.getSystemState().getBuiltins().__getitem__(Py.newString("dir"));
            PyObject attrs = dir.__call__(obj);

            for (PyObject attr : attrs.asIterable()) {
                String name = attr.toString();

                // Skip internal attributes
                if (name.startsWith("_")) {
                    continue;
                }

                try {
                    PyObject value = obj.__getattr__(name);

                    // Skip methods/callables
                    if (value.isCallable()) {
                        continue;
                    }

                    variables.add(createVariable(name, value, session));

                    if (variables.size() >= 1000) break;
                } catch (Exception e) {
                    // Skip attributes that can't be accessed
                }
            }
        } catch (Exception e) {
            logger.debug("Error extracting object variables: {}", e.getMessage());
        }
    }

    private DebugVariablesResult.Variable createVariable(
            String name, PyObject value, GatewayDebugSession session) {
        String valueStr = formatPyValue(value);
        String typeStr = value.getType().getName();
        int varRef = 0;

        // Check if value can be expanded
        if (value instanceof org.python.core.PyDictionary) {
            int size = value.__len__();
            if (size > 0) {
                varRef = session.registerVariableReference(value);
            }
        } else if (value instanceof org.python.core.PyList
                || value instanceof org.python.core.PyTuple) {
            int size = value.__len__();
            if (size > 0) {
                varRef = session.registerVariableReference(value);
            }
        } else if (value.__findattr__("__dict__") != null && !value.isCallable()) {
            varRef = session.registerVariableReference(value);
        }

        return new DebugVariablesResult.Variable(name, valueStr, typeStr, varRef);
    }

    private String formatPyValue(PyObject value) {
        try {
            if (value == Py.None) {
                return "None";
            } else if (value instanceof org.python.core.PyBoolean) {
                return value.toString();
            } else if (value instanceof org.python.core.PyInteger
                    || value instanceof org.python.core.PyLong
                    || value instanceof org.python.core.PyFloat) {
                return value.toString();
            } else if (value instanceof org.python.core.PyString
                    || value instanceof org.python.core.PyUnicode) {
                String s = value.toString();
                if (s.length() > 200) {
                    return "'" + s.substring(0, 200) + "...'";
                }
                return "'" + s + "'";
            } else if (value instanceof org.python.core.PyList) {
                return "list[" + value.__len__() + "]";
            } else if (value instanceof org.python.core.PyTuple) {
                return "tuple[" + value.__len__() + "]";
            } else if (value instanceof org.python.core.PyDictionary) {
                return "dict{" + value.__len__() + "}";
            } else {
                String s = value.toString();
                if (s.length() > 200) {
                    return s.substring(0, 200) + "...";
                }
                return s;
            }
        } catch (Exception e) {
            return "<error>";
        }
    }

    /**
     * Evaluates an expression in a session. Uses pure Java/Jython APIs to avoid classloader issues.
     */
    public DebugEvaluateResult evaluate(String sessionId, String expression, Integer frameId) {
        GatewayDebugSession session = sessions.get(sessionId);
        if (session == null) {
            return new DebugEvaluateResult("Session not found", "error");
        }

        try {
            PyObject locals = new org.python.core.PyDictionary();
            PyObject globals = new org.python.core.PyDictionary();

            if (frameId != null) {
                GatewayDebugSession.FrameInfo frameInfo = session.getFrameReference(frameId);
                if (frameInfo != null) {
                    locals = frameInfo.locals;
                    globals = frameInfo.globals;
                }
            }

            // Use Python's eval() builtin directly
            PyObject evalFunc = Py.getSystemState().getBuiltins().__getitem__(Py.newString("eval"));
            PyObject result = evalFunc.__call__(Py.newString(expression), globals, locals);

            String resultStr = result != null ? formatPyValue(result) : "None";
            String resultType = result != null ? result.getType().getName() : "NoneType";

            DebugEvaluateResult evalResult = new DebugEvaluateResult(resultStr, resultType);

            // Check if result can be expanded
            if (result != null) {
                if (result instanceof org.python.core.PyDictionary
                        || result instanceof org.python.core.PyList
                        || result instanceof org.python.core.PyTuple
                        || result.__findattr__("__dict__") != null) {
                    int ref = session.registerVariableReference(result);
                    evalResult.setVariablesReference(ref);
                }
            }

            return evalResult;

        } catch (Exception e) {
            return new DebugEvaluateResult("Error: " + e.getMessage(), "error");
        }
    }

    // ========================== Lifecycle ==========================

    /** Shuts down the executor. */
    public void shutdown() {
        // Terminate all sessions
        for (GatewayDebugSession session : sessions.values()) {
            session.sendCommand(DebugCommand.terminate());
        }
        sessions.clear();

        debugExecutor.shutdown();
        try {
            if (!debugExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                debugExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            debugExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** Output stream that captures output and queues it as debug events. */
    private static class OutputCaptureStream extends OutputStream {
        private final GatewayDebugSession session;
        private final String category;
        private final OutputStream original;
        private final StringBuilder buffer = new StringBuilder();

        private static final java.util.regex.Pattern LOG_PATTERN =
                java.util.regex.Pattern.compile("^\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+\\[");

        public OutputCaptureStream(
                GatewayDebugSession session, String category, OutputStream original) {
            this.session = session;
            this.category = category;
            this.original = original;
        }

        @Override
        public void write(int b) {
            if (original != null) {
                try {
                    original.write(b);
                } catch (Exception e) {
                    // Ignore
                }
            }

            char c = (char) b;
            buffer.append(c);

            if (c == '\n') {
                flushBuffer();
            }
        }

        @Override
        public void write(byte[] b, int off, int len) {
            if (original != null) {
                try {
                    original.write(b, off, len);
                } catch (Exception e) {
                    // Ignore
                }
            }

            String text = new String(b, off, len);
            buffer.append(text);

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

                if (!text.startsWith("[FlintDebugger]") && !LOG_PATTERN.matcher(text).find()) {
                    try {
                        session.notifyOutput(category, text);
                    } catch (Exception e) {
                        // Silently ignore
                    }
                }
            }
        }
    }
}
