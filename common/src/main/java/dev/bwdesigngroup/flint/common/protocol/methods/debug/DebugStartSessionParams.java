package dev.bwdesigngroup.flint.common.protocol.methods.debug;

import java.io.Serializable;

/**
 * Parameters for the debug.startSession method. Supports Designer, Gateway, and Perspective scopes.
 */
public class DebugStartSessionParams implements Serializable {
    private static final long serialVersionUID = 1L;
    private String code;
    private String filePath;
    private String modulePath;

    /** Execution scope: "designer" (default), "gateway", or "perspective" */
    private String scope;

    // Perspective context (only used when scope is "perspective")
    private String perspectiveSessionId;
    private String perspectivePageId;
    private String perspectiveViewInstanceId;
    private String perspectiveComponentPath;

    public DebugStartSessionParams() {}

    public DebugStartSessionParams(String code, String filePath, String modulePath) {
        this.code = code;
        this.filePath = filePath;
        this.modulePath = modulePath;
    }

    public DebugStartSessionParams(String code, String filePath, String modulePath, String scope) {
        this.code = code;
        this.filePath = filePath;
        this.modulePath = modulePath;
        this.scope = scope;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getModulePath() {
        return modulePath;
    }

    public void setModulePath(String modulePath) {
        this.modulePath = modulePath;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    /** Returns the effective scope (defaults to "designer" if not specified). */
    public String getEffectiveScope() {
        return scope != null && !scope.isEmpty() ? scope : "designer";
    }

    /** Returns true if this is a remote scope (gateway or perspective). */
    public boolean isRemoteScope() {
        String effectiveScope = getEffectiveScope();
        return "gateway".equals(effectiveScope) || "perspective".equals(effectiveScope);
    }

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
