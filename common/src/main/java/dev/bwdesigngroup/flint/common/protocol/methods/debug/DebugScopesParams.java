package dev.bwdesigngroup.flint.common.protocol.methods.debug;

import java.io.Serializable;

/** Parameters for the debug.getScopes method. */
public class DebugScopesParams implements Serializable {
    private static final long serialVersionUID = 1L;
    private String sessionId;
    private int frameId;

    public DebugScopesParams() {}

    public DebugScopesParams(String sessionId, int frameId) {
        this.sessionId = sessionId;
        this.frameId = frameId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public int getFrameId() {
        return frameId;
    }

    public void setFrameId(int frameId) {
        this.frameId = frameId;
    }
}
