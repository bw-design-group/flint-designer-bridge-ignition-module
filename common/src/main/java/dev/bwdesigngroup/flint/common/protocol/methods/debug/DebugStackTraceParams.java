package dev.bwdesigngroup.flint.common.protocol.methods.debug;

import java.io.Serializable;

/** Parameters for the debug.getStackTrace method. */
public class DebugStackTraceParams implements Serializable {
    private static final long serialVersionUID = 1L;
    private String sessionId;
    private Integer threadId;
    private Integer startFrame;
    private Integer levels;

    public DebugStackTraceParams() {}

    public DebugStackTraceParams(String sessionId) {
        this.sessionId = sessionId;
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

    public Integer getStartFrame() {
        return startFrame;
    }

    public void setStartFrame(Integer startFrame) {
        this.startFrame = startFrame;
    }

    public Integer getLevels() {
        return levels;
    }

    public void setLevels(Integer levels) {
        this.levels = levels;
    }
}
