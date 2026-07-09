package dev.bwdesigngroup.flint.common.protocol.methods.debug;

import java.io.Serializable;

/** Result for the debug.startSession method. */
public class DebugStartSessionResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private String sessionId;
    private boolean success;
    private String error;

    public DebugStartSessionResult() {}

    public static DebugStartSessionResult success(String sessionId) {
        DebugStartSessionResult result = new DebugStartSessionResult();
        result.sessionId = sessionId;
        result.success = true;
        return result;
    }

    public static DebugStartSessionResult failure(String error) {
        DebugStartSessionResult result = new DebugStartSessionResult();
        result.success = false;
        result.error = error;
        return result;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
