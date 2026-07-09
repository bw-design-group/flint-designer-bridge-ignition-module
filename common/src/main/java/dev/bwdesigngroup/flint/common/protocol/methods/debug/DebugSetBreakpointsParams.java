package dev.bwdesigngroup.flint.common.protocol.methods.debug;

import java.io.Serializable;
import java.util.List;

/** Parameters for the debug.setBreakpoints method. */
public class DebugSetBreakpointsParams implements Serializable {
    private static final long serialVersionUID = 1L;
    private String sessionId;
    private String filePath;
    private List<BreakpointInfo> breakpoints;

    public DebugSetBreakpointsParams() {}

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public List<BreakpointInfo> getBreakpoints() {
        return breakpoints;
    }

    public void setBreakpoints(List<BreakpointInfo> breakpoints) {
        this.breakpoints = breakpoints;
    }

    /** Information about a single breakpoint. */
    public static class BreakpointInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private int line;
        private String condition;
        private Integer hitCount;

        public BreakpointInfo() {}

        public BreakpointInfo(int line) {
            this.line = line;
        }

        public BreakpointInfo(int line, String condition, Integer hitCount) {
            this.line = line;
            this.condition = condition;
            this.hitCount = hitCount;
        }

        public int getLine() {
            return line;
        }

        public void setLine(int line) {
            this.line = line;
        }

        public String getCondition() {
            return condition;
        }

        public void setCondition(String condition) {
            this.condition = condition;
        }

        public Integer getHitCount() {
            return hitCount;
        }

        public void setHitCount(Integer hitCount) {
            this.hitCount = hitCount;
        }
    }
}
