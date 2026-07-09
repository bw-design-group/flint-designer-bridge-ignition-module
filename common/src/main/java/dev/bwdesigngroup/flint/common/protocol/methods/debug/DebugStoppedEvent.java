package dev.bwdesigngroup.flint.common.protocol.methods.debug;

import java.io.Serializable;

/** Event sent when the debugger stops (breakpoint hit, step complete, etc.). */
public class DebugStoppedEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    private String reason; // "breakpoint", "step", "pause", "exception", "entry"
    private int threadId;
    private String description;
    private String text;
    private boolean allThreadsStopped;

    public DebugStoppedEvent() {}

    public DebugStoppedEvent(String reason, int threadId) {
        this.reason = reason;
        this.threadId = threadId;
        this.allThreadsStopped = true;
    }

    public static DebugStoppedEvent breakpoint(int threadId) {
        return new DebugStoppedEvent("breakpoint", threadId);
    }

    public static DebugStoppedEvent step(int threadId) {
        return new DebugStoppedEvent("step", threadId);
    }

    public static DebugStoppedEvent pause(int threadId) {
        return new DebugStoppedEvent("pause", threadId);
    }

    public static DebugStoppedEvent exception(int threadId, String description) {
        DebugStoppedEvent event = new DebugStoppedEvent("exception", threadId);
        event.setDescription(description);
        return event;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public int getThreadId() {
        return threadId;
    }

    public void setThreadId(int threadId) {
        this.threadId = threadId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isAllThreadsStopped() {
        return allThreadsStopped;
    }

    public void setAllThreadsStopped(boolean allThreadsStopped) {
        this.allThreadsStopped = allThreadsStopped;
    }
}
