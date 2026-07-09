package dev.bwdesigngroup.flint.common.protocol.methods.perspective.recording;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** A batch of binding state transition events from a single poll cycle. */
public class RecordingEventBatch implements Serializable {
    private static final long serialVersionUID = 1L;

    private String recordingId;
    private List<RecordingBindingEvent> events;
    private int pendingCount;
    private int resolvedCount;
    private int errorCount;
    private int totalCount;
    private boolean isComplete;
    private String completionReason;

    public RecordingEventBatch() {
        this.events = new ArrayList<>();
    }

    public String getRecordingId() {
        return recordingId;
    }

    public void setRecordingId(String recordingId) {
        this.recordingId = recordingId;
    }

    public List<RecordingBindingEvent> getEvents() {
        return events;
    }

    public void setEvents(List<RecordingBindingEvent> events) {
        this.events = events;
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

    public boolean isComplete() {
        return isComplete;
    }

    public void setComplete(boolean complete) {
        isComplete = complete;
    }

    public String getCompletionReason() {
        return completionReason;
    }

    public void setCompletionReason(String completionReason) {
        this.completionReason = completionReason;
    }
}
