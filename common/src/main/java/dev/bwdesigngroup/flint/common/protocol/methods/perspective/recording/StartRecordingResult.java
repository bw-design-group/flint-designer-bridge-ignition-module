package dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording;

import java.io.Serializable;

/** Result of starting a binding recording session. */
public class StartRecordingResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean success;
    private String recordingId;
    private String error;
    private String viewPath;
    private int pendingCount;
    private int resolvedCount;
    private int errorCount;
    private int totalCount;

    public StartRecordingResult() {}

    public static StartRecordingResult success(
            String recordingId,
            String viewPath,
            int pendingCount,
            int resolvedCount,
            int errorCount,
            int totalCount) {
        StartRecordingResult result = new StartRecordingResult();
        result.success = true;
        result.recordingId = recordingId;
        result.viewPath = viewPath;
        result.pendingCount = pendingCount;
        result.resolvedCount = resolvedCount;
        result.errorCount = errorCount;
        result.totalCount = totalCount;
        return result;
    }

    public static StartRecordingResult failure(String error) {
        StartRecordingResult result = new StartRecordingResult();
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

    public String getRecordingId() {
        return recordingId;
    }

    public void setRecordingId(String recordingId) {
        this.recordingId = recordingId;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getViewPath() {
        return viewPath;
    }

    public void setViewPath(String viewPath) {
        this.viewPath = viewPath;
    }

    public int getPendingCount() {
        return pendingCount;
    }

    public void setPendingCount(int pendingCount) {
        this.pendingCount = pendingCount;
    }

    public int getResolvedCount() {
        return resolvedCount;
    }

    public void setResolvedCount(int resolvedCount) {
        this.resolvedCount = resolvedCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
