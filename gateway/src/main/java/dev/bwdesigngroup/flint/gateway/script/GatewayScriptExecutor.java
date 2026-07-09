package dev.bwdesigngroup.flint.gateway.script;

import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.gateway.model.GatewayContext;
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
 * Executes Python scripts in the Gateway's script context. Captures stdout/stderr output and
 * handles timeouts. Supports session persistence for REPL-style interaction.
 */
public class GatewayScriptExecutor {
    private static final Logger logger = LoggerFactory.getLogger("flint.Gateway.Script");

    private final GatewayContext context;
    private final ExecutorService executor;

    /**
     * Session storage: maps sessionId to the locals PyObject. This allows variables to persist
     * across multiple script executions.
     */
    private final Map<String, PyObject> sessionLocals = new ConcurrentHashMap<>();

    public GatewayScriptExecutor(GatewayContext context) {
        this.context = context;
        this.executor =
                Executors.newCachedThreadPool(
                        r -> {
                            Thread t = new Thread(r, "FlintGatewayScriptExecutor");
                            t.setDaemon(true);
                            return t;
                        });
    }

    /**
     * Executes Python code and returns the result. This overload creates a new locals map for each
     * execution (no persistence).
     *
     * @param code The Python code to execute
     * @param timeoutMs Maximum execution time in milliseconds
     * @return The execution result including stdout, stderr, and timing
     */
    public ExecuteScriptResult execute(String code, int timeoutMs) {
        return execute(code, timeoutMs, null, false);
    }

    /**
     * Executes Python code with optional session persistence.
     *
     * @param code The Python code to execute
     * @param timeoutMs Maximum execution time in milliseconds
     * @param sessionId Optional session ID for variable persistence. If null, creates fresh locals.
     * @param resetSession If true and sessionId is provided, clears the session before execution
     * @return The execution result including stdout, stderr, and timing
     */
    public ExecuteScriptResult execute(
            String code, int timeoutMs, String sessionId, boolean resetSession) {
        long startTime = System.currentTimeMillis();

        // Handle session reset
        if (sessionId != null && resetSession) {
            sessionLocals.remove(sessionId);
            logger.debug("Reset gateway session: {}", sessionId);
        }

        // Create output streams for capturing stdout/stderr
        ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrStream = new ByteArrayOutputStream();
        PrintStream stdoutPrint = new PrintStream(stdoutStream);
        PrintStream stderrPrint = new PrintStream(stderrStream);

        Callable<Void> scriptTask =
                () -> {
                    executeScript(code, stdoutPrint, stderrPrint, sessionId);
                    return null;
                };

        Future<Void> future = executor.submit(scriptTask);

        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS);

            long executionTime = System.currentTimeMillis() - startTime;
            String stdout = stdoutStream.toString();
            String stderr = stderrStream.toString();

            logger.debug(
                    "Gateway script executed successfully in {}ms (session={})",
                    executionTime,
                    sessionId);
            return ExecuteScriptResult.success(stdout, stderr, executionTime);

        } catch (TimeoutException e) {
            future.cancel(true);
            long executionTime = System.currentTimeMillis() - startTime;
            String stdout = stdoutStream.toString();
            String stderr = stderrStream.toString();

            logger.warn("Gateway script execution timed out after {}ms", timeoutMs);
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

            logger.error("Gateway script execution failed: {}", errorMessage, cause);
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

    /**
     * Executes the script using the Gateway's ScriptManager. If sessionId is provided,
     * reuses/creates persistent locals for that session.
     */
    private void executeScript(
            String code, PrintStream stdout, PrintStream stderr, String sessionId)
            throws Exception {
        ScriptManager scriptManager = context.getScriptManager();

        // Add output streams
        scriptManager.addStdOutStream(stdout);
        scriptManager.addStdErrStream(stderr);

        try {
            // Get or create locals for this session
            PyObject locals;
            if (sessionId != null) {
                locals =
                        sessionLocals.computeIfAbsent(
                                sessionId,
                                k -> {
                                    logger.debug("Creating new gateway session locals for: {}", k);
                                    return scriptManager.createLocalsMap();
                                });
            } else {
                // No session - create fresh locals
                locals = scriptManager.createLocalsMap();
            }

            // Clear project modules from sys.modules so imports get fresh versions
            // This ensures script library changes are picked up without needing a Reset
            clearProjectModules(scriptManager, locals);

            // Execute the code
            scriptManager.runCode(code, locals, "<flint-gateway-console>");

        } finally {
            // Always remove the streams
            scriptManager.removeStdOutStream(stdout);
            scriptManager.removeStdErrStream(stderr);
        }
    }

    /**
     * Clears project-specific modules from sys.modules so they get re-imported fresh. This ensures
     * that script library changes are reflected immediately. User-defined variables in session
     * locals are preserved.
     */
    private void clearProjectModules(ScriptManager scriptManager, PyObject locals) {
        // Python code to clear project modules from sys.modules
        // We preserve system modules (java.*, org.*, com.*, __*) and standard library
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
            // Log but don't fail - module clearing is a best-effort optimization
            logger.debug("Could not clear project modules: {}", e.getMessage());
        }
    }

    /**
     * Resets a specific session, clearing all variables.
     *
     * @param sessionId The session ID to reset
     */
    public void resetSession(String sessionId) {
        if (sessionId != null) {
            sessionLocals.remove(sessionId);
            logger.debug("Gateway session reset: {}", sessionId);
        }
    }

    /** Clears all sessions. */
    public void clearAllSessions() {
        sessionLocals.clear();
        logger.debug("All gateway sessions cleared");
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
