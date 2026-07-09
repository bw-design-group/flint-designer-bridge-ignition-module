package dev.bwdesigngroup.flint.designer.debug;

/** Represents the current state of a debug session. */
public enum DebugState {
    /** Debug session not started. */
    NOT_STARTED,

    /** Script is running (not paused). */
    RUNNING,

    /** Execution is paused at a breakpoint or after a step. */
    PAUSED,

    /** Script execution has completed. */
    COMPLETED,

    /** Script execution failed with an error. */
    ERROR,

    /** Debug session was terminated. */
    TERMINATED
}
