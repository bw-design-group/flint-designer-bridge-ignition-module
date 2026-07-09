package dev.bwdesigngroup.flint.common.protocol.methods;

/** Parameters for the executeScript method. */
public class ExecuteScriptParams {
    private String code;
    private Integer timeoutMs;
    private String sessionId;
    private Boolean resetSession;
    private String scope; // "designer", "gateway", or "perspective"

    // Perspective-specific context fields
    private String perspectiveSessionId;
    private String perspectivePageId;
    private String perspectiveViewInstanceId;
    private String perspectiveComponentPath;

    public ExecuteScriptParams() {}

    public ExecuteScriptParams(String code, Integer timeoutMs) {
        this.code = code;
        this.timeoutMs = timeoutMs;
    }

    public ExecuteScriptParams(
            String code, Integer timeoutMs, String sessionId, Boolean resetSession) {
        this.code = code;
        this.timeoutMs = timeoutMs;
        this.sessionId = sessionId;
        this.resetSession = resetSession;
    }

    public ExecuteScriptParams(
            String code, Integer timeoutMs, String sessionId, Boolean resetSession, String scope) {
        this.code = code;
        this.timeoutMs = timeoutMs;
        this.sessionId = sessionId;
        this.resetSession = resetSession;
        this.scope = scope;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Boolean getResetSession() {
        return resetSession;
    }

    public void setResetSession(Boolean resetSession) {
        this.resetSession = resetSession;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    /** Returns true if this request should be executed in gateway scope. */
    public boolean isGatewayScope() {
        return "gateway".equalsIgnoreCase(scope);
    }

    /** Returns true if this request should be executed in Perspective scope. */
    public boolean isPerspectiveScope() {
        return "perspective".equalsIgnoreCase(scope);
    }

    // Perspective context getters and setters

    public String getPerspectiveSessionId() {
        return perspectiveSessionId;
    }

    public void setPerspectiveSessionId(String perspectiveSessionId) {
        this.perspectiveSessionId = perspectiveSessionId;
    }

    public String getPerspectivePageId() {
        return perspectivePageId;
    }

    public void setPerspectivePageId(String perspectivePageId) {
        this.perspectivePageId = perspectivePageId;
    }

    public String getPerspectiveViewInstanceId() {
        return perspectiveViewInstanceId;
    }

    public void setPerspectiveViewInstanceId(String perspectiveViewInstanceId) {
        this.perspectiveViewInstanceId = perspectiveViewInstanceId;
    }

    public String getPerspectiveComponentPath() {
        return perspectiveComponentPath;
    }

    public void setPerspectiveComponentPath(String perspectiveComponentPath) {
        this.perspectiveComponentPath = perspectiveComponentPath;
    }
}
