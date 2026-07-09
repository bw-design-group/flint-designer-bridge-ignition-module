package dev.bwdesigngroup.flint.common.debug;

import java.io.Serializable;

/**
 * Represents a debug control command. Used for communication between Designer and Gateway during
 * remote debug sessions.
 */
public class DebugCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Type type;

    public enum Type {
        CONTINUE,
        STEP_OVER,
        STEP_INTO,
        STEP_OUT,
        PAUSE,
        TERMINATE
    }

    public DebugCommand(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public static DebugCommand continueExecution() {
        return new DebugCommand(Type.CONTINUE);
    }

    public static DebugCommand stepOver() {
        return new DebugCommand(Type.STEP_OVER);
    }

    public static DebugCommand stepInto() {
        return new DebugCommand(Type.STEP_INTO);
    }

    public static DebugCommand stepOut() {
        return new DebugCommand(Type.STEP_OUT);
    }

    public static DebugCommand pause() {
        return new DebugCommand(Type.PAUSE);
    }

    public static DebugCommand terminate() {
        return new DebugCommand(Type.TERMINATE);
    }

    @Override
    public String toString() {
        return "DebugCommand{type=" + type + "}";
    }
}
