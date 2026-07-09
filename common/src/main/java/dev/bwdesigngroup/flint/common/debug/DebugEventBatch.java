package dev.bwdesigngroup.flint.common.debug;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Batch of debug events returned from Gateway RPC polling. Events are accumulated while the
 * Designer polls, then sent as a batch.
 */
public class DebugEventBatch implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<DebugEvent> events;
    private final boolean sessionActive;

    public DebugEventBatch() {
        this.events = new ArrayList<>();
        this.sessionActive = false;
    }

    public DebugEventBatch(List<DebugEvent> events, boolean sessionActive) {
        this.events = events != null ? new ArrayList<>(events) : new ArrayList<>();
        this.sessionActive = sessionActive;
    }

    public List<DebugEvent> getEvents() {
        return events;
    }

    public boolean isSessionActive() {
        return sessionActive;
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }

    public int size() {
        return events.size();
    }

    @Override
    public String toString() {
        return "DebugEventBatch{events=" + events.size() + ", sessionActive=" + sessionActive + "}";
    }
}
