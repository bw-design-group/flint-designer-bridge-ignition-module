package dev.bwdesigngroup.flint.common.protocol.methods.debug;

import java.io.Serializable;

/** Result for debug control methods. */
public class DebugControlResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private boolean success;
    private String error;

    public DebugControlResult() {}

    public static DebugControlResult success() {
        DebugControlResult result = new DebugControlResult();
        result.success = true;
        return result;
    }

    public static DebugControlResult failure(String error) {
        DebugControlResult result = new DebugControlResult();
        result.success = false;
        result.error = error;
        return result;
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
