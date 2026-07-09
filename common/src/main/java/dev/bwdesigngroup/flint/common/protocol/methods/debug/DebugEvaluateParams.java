package dev.bwdesigngroup.flint.common.protocol.methods.debug;

import java.io.Serializable;

/** Parameters for the debug.evaluate method. */
public class DebugEvaluateParams implements Serializable {
    private static final long serialVersionUID = 1L;
    private String sessionId;
    private String expression;
    private Integer frameId;
    private String context; // "watch", "repl", "hover"

    public DebugEvaluateParams() {}

    public DebugEvaluateParams(String sessionId, String expression) {
        this.sessionId = sessionId;
        this.expression = expression;
    }

    public DebugEvaluateParams(
            String sessionId, String expression, Integer frameId, String context) {
        this.sessionId = sessionId;
        this.expression = expression;
        this.frameId = frameId;
        this.context = context;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public Integer getFrameId() {
        return frameId;
    }

    public void setFrameId(Integer frameId) {
        this.frameId = frameId;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }
}
