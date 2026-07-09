package dev.bwdesigngroup.flint.common.protocol.methods.debug;

import java.io.Serializable;
import java.util.List;

/** Result for the debug.getStackTrace method. */
public class DebugStackTraceResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<StackFrame> stackFrames;
    private int totalFrames;

    public DebugStackTraceResult() {}

    public DebugStackTraceResult(List<StackFrame> stackFrames, int totalFrames) {
        this.stackFrames = stackFrames;
        this.totalFrames = totalFrames;
    }

    public List<StackFrame> getStackFrames() {
        return stackFrames;
    }

    public void setStackFrames(List<StackFrame> stackFrames) {
        this.stackFrames = stackFrames;
    }

    public int getTotalFrames() {
        return totalFrames;
    }

    public void setTotalFrames(int totalFrames) {
        this.totalFrames = totalFrames;
    }

    /** A single stack frame. */
    public static class StackFrame implements Serializable {
        private static final long serialVersionUID = 1L;
        private int id;
        private String name;
        private String filePath;
        private int line;
        private int column;
        private String modulePath;

        public StackFrame() {}

        public StackFrame(int id, String name, String filePath, int line) {
            this.id = id;
            this.name = name;
            this.filePath = filePath;
            this.line = line;
            this.column = 0;
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public int getLine() {
            return line;
        }

        public void setLine(int line) {
            this.line = line;
        }

        public int getColumn() {
            return column;
        }

        public void setColumn(int column) {
            this.column = column;
        }

        public String getModulePath() {
            return modulePath;
        }

        public void setModulePath(String modulePath) {
            this.modulePath = modulePath;
        }
    }
}
