package dev.bwdesigngroup.flint.gateway.perspective;

import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
import com.inductiveautomation.perspective.gateway.model.PageModel;
import com.inductiveautomation.perspective.gateway.session.InternalSession;
import dev.bwdesigngroup.flint.common.protocol.methods.ExecuteScriptResult;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.python.core.PyObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes Python scripts in the context of a Perspective session. Provides access to session,
 * page, view, and component context via script locals.
 */
public class PerspectiveScriptExecutor {
    private static final Logger logger =
            LoggerFactory.getLogger("flint.Gateway.Perspective.Script");

    private final GatewayContext gatewayContext;
    private final PerspectiveSessionInspector sessionInspector;
    private final ExecutorService executor;

    /** Session storage for variable persistence across script executions. */
    private final Map<String, PyObject> sessionLocals = new ConcurrentHashMap<>();

    public PerspectiveScriptExecutor(
            GatewayContext gatewayContext, PerspectiveSessionInspector sessionInspector) {
        this.gatewayContext = gatewayContext;
        this.sessionInspector = sessionInspector;
        this.executor =
                Executors.newCachedThreadPool(
                        r -> {
                            Thread t = new Thread(r, "FlintPerspectiveScriptExecutor");
                            t.setDaemon(true);
                            return t;
                        });
    }

    /**
     * Executes a script in the context of a Perspective session.
     *
     * @param code The Python code to execute
     * @param timeoutMs Maximum execution time
     * @param perspectiveSessionId The Perspective session ID for context
     * @param pageId Optional page ID for narrower context
     * @param viewInstanceId Optional view instance ID
     * @param componentPath Optional component path to bind as 'self'
     * @param scriptSessionId Optional script session ID for variable persistence
     * @param resetSession If true, clear the script session before execution
     * @return Execution result with stdout, stderr, and timing
     */
    public ExecuteScriptResult execute(
            String code,
            int timeoutMs,
            String perspectiveSessionId,
            String pageId,
            String viewInstanceId,
            String componentPath,
            String scriptSessionId,
            boolean resetSession) {

        long startTime = System.currentTimeMillis();

        // Handle session reset
        if (scriptSessionId != null && resetSession) {
            sessionLocals.remove(scriptSessionId);
            logger.debug("Reset script session: {}", scriptSessionId);
        }

        // Create output streams for capturing stdout/stderr
        ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrStream = new ByteArrayOutputStream();
        PrintStream stdoutPrint = new PrintStream(stdoutStream);
        PrintStream stderrPrint = new PrintStream(stderrStream);

        Callable<Void> scriptTask =
                () -> {
                    executeScript(
                            code,
                            stdoutPrint,
                            stderrPrint,
                            perspectiveSessionId,
                            pageId,
                            viewInstanceId,
                            componentPath,
                            scriptSessionId);
                    return null;
                };

        Future<Void> future = executor.submit(scriptTask);

        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS);

            long executionTime = System.currentTimeMillis() - startTime;
            String stdout = stdoutStream.toString();
            String stderr = stderrStream.toString();

            logger.debug("Perspective script executed successfully in {}ms", executionTime);
            return ExecuteScriptResult.success(stdout, stderr, executionTime);

        } catch (TimeoutException e) {
            future.cancel(true);
            long executionTime = System.currentTimeMillis() - startTime;
            String stdout = stdoutStream.toString();
            String stderr = stderrStream.toString();

            logger.warn("Perspective script execution timed out after {}ms", timeoutMs);
            return ExecuteScriptResult.failure(
                    "Script execution timed out after " + timeoutMs + "ms",
                    stdout,
                    stderr,
                    executionTime);

        } catch (ExecutionException e) {
            long executionTime = System.currentTimeMillis() - startTime;
            String stdout = stdoutStream.toString();
            String stderr = stderrStream.toString();

            Throwable cause = e.getCause();
            String errorMessage = cause != null ? cause.getMessage() : e.getMessage();

            logger.error("Perspective script execution failed: {}", errorMessage, cause);
            return ExecuteScriptResult.failure(errorMessage, stdout, stderr, executionTime);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long executionTime = System.currentTimeMillis() - startTime;
            return ExecuteScriptResult.failure(
                    "Script execution interrupted",
                    stdoutStream.toString(),
                    stderrStream.toString(),
                    executionTime);
        } finally {
            stdoutPrint.close();
            stderrPrint.close();
        }
    }

    private void executeScript(
            String code,
            PrintStream stdout,
            PrintStream stderr,
            String perspectiveSessionId,
            String pageId,
            String viewInstanceId,
            String componentPath,
            String scriptSessionId)
            throws Exception {

        ScriptManager scriptManager = gatewayContext.getScriptManager();

        // Add output streams
        scriptManager.addStdOutStream(stdout);
        scriptManager.addStdErrStream(stderr);

        // Get the PageModel and InternalSession for thread-local context (required for
        // system.perspective.* functions)
        PageModel pageModel = null;
        InternalSession internalSession = null;
        if (perspectiveSessionId != null && pageId != null) {
            pageModel = sessionInspector.findPageModel(perspectiveSessionId, pageId);
            if (pageModel != null) {
                // Get the InternalSession from the PageModel
                internalSession = pageModel.session;

                // Set both ThreadLocals so system.perspective.* functions work
                // InternalSession.SESSION is checked by AbstractScriptingFunctions.getSession()
                InternalSession.SESSION.set(internalSession);
                // PageModel.PAGE is used by some operations that need the current page
                PageModel.PAGE.set(pageModel);

                logger.debug(
                        "Set InternalSession and PageModel ThreadLocals for session={}, page={}",
                        perspectiveSessionId,
                        pageId);
            } else {
                logger.warn(
                        "Could not find PageModel for session={}, page={} - system.perspective.* functions may not work",
                        perspectiveSessionId,
                        pageId);
            }
        }

        try {
            // Get or create locals for this session
            PyObject locals;
            if (scriptSessionId != null) {
                locals =
                        sessionLocals.computeIfAbsent(
                                scriptSessionId,
                                k -> {
                                    logger.debug(
                                            "Creating new Perspective script session locals for: {}",
                                            k);
                                    return scriptManager.createLocalsMap();
                                });
            } else {
                // No session - create fresh locals
                locals = scriptManager.createLocalsMap();
            }

            // Add Perspective context to locals
            addPerspectiveContext(
                    locals, perspectiveSessionId, pageId, viewInstanceId, componentPath);

            // Clear project modules from sys.modules for fresh imports
            clearProjectModules(scriptManager, locals);

            // Execute the code
            scriptManager.runCode(code, locals, "<flint-perspective-console>");

        } finally {
            // Always clear the ThreadLocals
            if (internalSession != null) {
                InternalSession.SESSION.remove();
            }
            if (pageModel != null) {
                PageModel.PAGE.remove();
            }
            if (internalSession != null || pageModel != null) {
                logger.debug("Cleared Perspective ThreadLocals");
            }

            // Always remove the streams
            scriptManager.removeStdOutStream(stdout);
            scriptManager.removeStdErrStream(stderr);
        }
    }

    /**
     * Adds Perspective context variables to the script locals. Delegates to
     * PerspectiveSessionInspector which handles lazy loading of Perspective SDK classes.
     */
    private void addPerspectiveContext(
            PyObject locals,
            String perspectiveSessionId,
            String pageId,
            String viewInstanceId,
            String componentPath) {

        // Delegate to session inspector which handles lazy loading of Perspective classes
        sessionInspector.addPerspectiveContextToLocals(
                locals, perspectiveSessionId, pageId, viewInstanceId, componentPath);
    }

    /** Clears project-specific modules from sys.modules for fresh imports. */
    private void clearProjectModules(ScriptManager scriptManager, PyObject locals) {
        String clearScript =
                "import sys\n"
                        + "_system_prefixes = ('java', 'javax', 'org.', 'com.inductiveautomation', 'com.google', "
                        + "'__', '_', 'sys', 'os', 're', 'json', 'time', 'datetime', 'math', 'random', 'copy', "
                        + "'collections', 'itertools', 'functools', 'operator', 'string', 'types', 'traceback', "
                        + "'threading', 'Queue', 'socket', 'struct', 'codecs', 'encodings', 'xml', 'urllib', "
                        + "'httplib', 'email', 'logging', 'weakref', 'gc', 'imp', 'zipimport', 'pkgutil', "
                        + "'StringIO', 'cStringIO', 'io', 'array', 'binascii', 'base64', 'hashlib', 'hmac', "
                        + "'ssl', 'select', 'errno', 'posixpath', 'ntpath', 'genericpath', 'stat', 'warnings', "
                        + "'atexit', 'signal', 'contextlib', 'abc', 'numbers', 'decimal', 'fractions', "
                        + "'calendar', 'locale', 'gettext', 'platform', 'tempfile', 'shutil', 'fnmatch', "
                        + "'glob', 'linecache', 'tokenize', 'token', 'keyword', 'pprint', 'difflib', 'textwrap', "
                        + "'unicodedata', 'stringprep', 'csv', 'pickle', 'cPickle', 'marshal', 'shelve', 'anydbm', "
                        + "'whichdb', 'dbm', 'gdbm', 'dbhash', 'bsddb', 'dumbdbm', 'sqlite3', 'zlib', 'gzip', "
                        + "'bz2', 'tarfile', 'zipfile', 'ConfigParser', 'robotparser', 'netrc', 'xdrlib', "
                        + "'plistlib', 'aifc', 'sunau', 'wave', 'chunk', 'colorsys', 'imghdr', 'sndhdr', "
                        + "'ossaudiodev', 'getopt', 'optparse', 'argparse', 'fileinput', 'cmd', 'shlex', "
                        + "'unittest', 'doctest', 'test', 'pdb', 'profile', 'cProfile', 'hotshot', 'timeit', "
                        + "'trace', 'compileall', 'py_compile', 'pyclbr', 'dis', 'compiler', 'symtable', 'code', "
                        + "'codeop', 'xmlrpclib', 'SimpleXMLRPCServer', 'DocXMLRPCServer', 'socket', 'SocketServer', "
                        + "'BaseHTTPServer', 'SimpleHTTPServer', 'CGIHTTPServer', 'urllib2', 'httplib', 'ftplib', "
                        + "'poplib', 'imaplib', 'nntplib', 'smtplib', 'smtpd', 'telnetlib', 'uuid', 'urlparse', "
                        + "'mimetools', 'MimeWriter', 'mimify', 'multifile', 'rfc822', 'mhlib', 'mimetypes', "
                        + "'quopri', 'uu', 'HTMLParser', 'sgmllib', 'htmllib', 'htmlentitydefs', 'formatter', "
                        + "'multiprocessing', 'subprocess', 'runpy', 'sched', 'asyncore', 'asynchat', 'future')\n"
                        + "_keys_to_remove = [k for k in list(sys.modules.keys()) if not any(k.startswith(p) or k == p.rstrip('.') for p in _system_prefixes)]\n"
                        + "for _k in _keys_to_remove:\n"
                        + "    try:\n"
                        + "        del sys.modules[_k]\n"
                        + "    except:\n"
                        + "        pass\n"
                        + "del _system_prefixes, _keys_to_remove, _k\n";

        try {
            scriptManager.runCode(clearScript, locals, "<flint-module-clear>");
        } catch (Exception e) {
            logger.debug("Could not clear project modules: {}", e.getMessage());
        }
    }

    /** Resets a specific script session. */
    public void resetSession(String sessionId) {
        if (sessionId != null) {
            sessionLocals.remove(sessionId);
            logger.debug("Perspective script session reset: {}", sessionId);
        }
    }

    /** Clears all script sessions. */
    public void clearAllSessions() {
        sessionLocals.clear();
        logger.debug("All Perspective script sessions cleared");
    }

    /** Shuts down the executor. */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        sessionLocals.clear();
    }
}
