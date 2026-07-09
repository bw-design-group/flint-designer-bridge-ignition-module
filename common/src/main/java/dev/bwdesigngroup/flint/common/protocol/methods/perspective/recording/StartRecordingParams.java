package dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording;

import java.io.Serializable;

/** Parameters for starting a binding recording session. */
public class StartRecordingParams implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String pageId;
    private String viewInstanceId;
    private int pollIntervalMs = 50;
    private int maxDurationMs = 120000;
    private boolean autoStopOnAllResolved = false;
    private int autoStopDelayMs = 2000;

    public StartRecordingParams() {}

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getPageId() {
        return pageId;
    }

    public void setPageId(String pageId) {
        this.pageId = pageId;
    }

    public String getViewInstanceId() {
        return viewInstanceId;
    }

    public void setViewInstanceId(String viewInstanceId) {
        this.viewInstanceId = viewInstanceId;
    }

    public int getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(int pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public int getMaxDurationMs() {
        return maxDurationMs;
    }

    public void setMaxDurationMs(int maxDurationMs) {
        this.maxDurationMs = maxDurationMs;
    }

    public boolean isAutoStopOnAllResolved() {
        return autoStopOnAllResolved;
    }

    public void setAutoStopOnAllResolved(boolean autoStopOnAllResolved) {
        this.autoStopOnAllResolved = autoStopOnAllResolved;
    }

    public int getAutoStopDelayMs() {
        return autoStopDelayMs;
    }

    public void setAutoStopDelayMs(int autoStopDelayMs) {
        this.autoStopDelayMs = autoStopDelayMs;
    }
}
