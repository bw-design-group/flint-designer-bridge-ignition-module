package dev.bwdesigngroup.flint.common.protocol.methods.debug;

import java.io.Serializable;

/** Parameters for debug control methods (continue, stepOver, stepInto, stepOut, pause). */
public class DebugControlParams implements Serializable {
    private static final long serialVersionUID = 1L;
    private String sessionId;
    private Integer threadId;

    public DebugControlParams() {}

    public DebugControlParams(String sessionId) {
        this.sessionId = sessionId;
    }

    public DebugControlParams(String sessionId, Integer threadId) {
        this.sessionId = sessionId;
        this.threadId = threadId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getThreadId() {
        return threadId;
    }

    public void setThreadId(Integer threadId) {
        this.threadId = threadId;
    }
}
