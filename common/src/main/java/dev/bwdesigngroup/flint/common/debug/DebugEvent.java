package dev.bwdesigngroup.flint.common.debug;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a debug event that can be sent from Gateway to Designer. Used for streaming debug
 * state changes during remote debug sessions.
 */
public class DebugEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Event types that match DAP event types. */
    public enum Type {
        STOPPED, // Execution stopped at breakpoint or step
        CONTINUED, // Execution continued
        TERMINATED, // Debug session ended
        OUTPUT, // Console output
        THREAD // Thread state change
    }

    private final Type type;
    private final Map<String, Object> body;

    public DebugEvent(Type type) {
        this.type = type;
        this.body = new HashMap<>();
    }

    public DebugEvent(Type type, Map<String, Object> body) {
        this.type = type;
        this.body = body != null ? new HashMap<>(body) : new HashMap<>();
    }

    public Type getType() {
        return type;
    }

    public Map<String, Object> getBody() {
        return body;
    }

    public void put(String key, Object value) {
        body.put(key, value);
    }

    public Object get(String key) {
        return body.get(key);
    }

    // Factory methods for common event types

    public static DebugEvent stopped(String reason, int threadId) {
        DebugEvent event = new DebugEvent(Type.STOPPED);
        event.put("reason", reason);
        event.put("threadId", threadId);
        return event;
    }

    public static DebugEvent continued(int threadId) {
        DebugEvent event = new DebugEvent(Type.CONTINUED);
        event.put("threadId", threadId);
        return event;
    }

    public static DebugEvent terminated() {
        return new DebugEvent(Type.TERMINATED);
    }

    public static DebugEvent output(String category, String output) {
        DebugEvent event = new DebugEvent(Type.OUTPUT);
        event.put("category", category);
        event.put("output", output);
        return event;
    }

    @Override
    public String toString() {
        return "DebugEvent{type=" + type + ", body=" + body + "}";
    }
}
