package dev.bwdesigngroup.flint.common.protocol.methods.debug;

import java.io.Serializable;

/** Parameters for the debug.getVariables method. */
public class DebugVariablesParams implements Serializable {
    private static final long serialVersionUID = 1L;
    private String sessionId;
    private int variablesReference;
    private Integer start;
    private Integer count;

    public DebugVariablesParams() {}

    public DebugVariablesParams(String sessionId, int variablesReference) {
        this.sessionId = sessionId;
        this.variablesReference = variablesReference;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public int getVariablesReference() {
        return variablesReference;
    }

    public void setVariablesReference(int variablesReference) {
        this.variablesReference = variablesReference;
    }

    public Integer getStart() {
        return start;
    }

    public void setStart(Integer start) {
        this.start = start;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }
}
