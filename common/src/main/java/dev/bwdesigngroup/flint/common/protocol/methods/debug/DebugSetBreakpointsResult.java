package dev.bwdesigngroup.flint.common.protocol.methods.debug;

import java.io.Serializable;
import java.util.List;

/** Result for the debug.setBreakpoints method. */
public class DebugSetBreakpointsResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<VerifiedBreakpoint> breakpoints;

    public DebugSetBreakpointsResult() {}

    public DebugSetBreakpointsResult(List<VerifiedBreakpoint> breakpoints) {
        this.breakpoints = breakpoints;
    }

    public List<VerifiedBreakpoint> getBreakpoints() {
        return breakpoints;
    }

    public void setBreakpoints(List<VerifiedBreakpoint> breakpoints) {
        this.breakpoints = breakpoints;
    }

    /** A verified breakpoint with its actual line number. */
    public static class VerifiedBreakpoint implements Serializable {
        private static final long serialVersionUID = 1L;
        private int id;
        private boolean verified;
        private int line;
        private String message;

        public VerifiedBreakpoint() {}

        public VerifiedBreakpoint(int id, boolean verified, int line) {
            this.id = id;
            this.verified = verified;
            this.line = line;
        }

        public VerifiedBreakpoint(int id, boolean verified, int line, String message) {
            this.id = id;
            this.verified = verified;
            this.line = line;
            this.message = message;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public boolean isVerified() {
            return verified;
        }

        public void setVerified(boolean verified) {
            this.verified = verified;
        }

        public int getLine() {
            return line;
        }

        public void setLine(int line) {
            this.line = line;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
