package dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording;

import java.io.Serializable;

/** Result of stopping a binding recording session. */
public class StopRecordingResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean success;
    private String error;
    private long durationMs;
    private int totalEventsRecorded;
    private int totalPollCount;

    public StopRecordingResult() {}

    public static StopRecordingResult success(
            long durationMs, int totalEventsRecorded, int totalPollCount) {
        StopRecordingResult result = new StopRecordingResult();
        result.success = true;
        result.durationMs = durationMs;
        result.totalEventsRecorded = totalEventsRecorded;
        result.totalPollCount = totalPollCount;
        return result;
    }

    public static StopRecordingResult failure(String error) {
        StopRecordingResult result = new StopRecordingResult();
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

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public int getTotalEventsRecorded() {
        return totalEventsRecorded;
    }

    public void setTotalEventsRecorded(int totalEventsRecorded) {
        this.totalEventsRecorded = totalEventsRecorded;
    }

    public int getTotalPollCount() {
        return totalPollCount;
    }

    public void setTotalPollCount(int totalPollCount) {
        this.totalPollCount = totalPollCount;
    }
}
