package dev.bwdesigngroup.flint.common.protocol.methods;

import java.io.Serializable;

/**
 * Result for the executeScript method. Implements Serializable for Ignition RPC communication
 * between scopes.
 */
public class ExecuteScriptResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private boolean success;
    private String stdout;
    private String stderr;
    private String error;
    private long executionTimeMs;

    public ExecuteScriptResult() {}

    public ExecuteScriptResult(
            boolean success, String stdout, String stderr, long executionTimeMs) {
        this.success = success;
        this.stdout = stdout;
        this.stderr = stderr;
        this.executionTimeMs = executionTimeMs;
    }

    public static ExecuteScriptResult success(String stdout, String stderr, long executionTimeMs) {
        return new ExecuteScriptResult(true, stdout, stderr, executionTimeMs);
    }

    public static ExecuteScriptResult failure(
            String error, String stdout, String stderr, long executionTimeMs) {
        ExecuteScriptResult result =
                new ExecuteScriptResult(false, stdout, stderr, executionTimeMs);
        result.setError(error);
        return result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getStdout() {
        return stdout;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public void setStderr(String stderr) {
        this.stderr = stderr;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }
}
