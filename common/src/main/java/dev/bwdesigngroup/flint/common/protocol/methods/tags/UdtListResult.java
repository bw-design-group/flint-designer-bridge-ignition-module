package dev.bwdesigngroup.flint.common.protocol.methods.tags;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Result for the udt.getDefinitions method. Returns a list of UDT definitions found under the
 * _types_ folder.
 */
public class UdtListResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<UdtInfo> definitions;

    public UdtListResult() {
        this.definitions = new ArrayList<>();
    }

    public UdtListResult(List<UdtInfo> definitions) {
        this.definitions = definitions != null ? definitions : new ArrayList<>();
    }

    public List<UdtInfo> getDefinitions() {
        return definitions;
    }

    public void setDefinitions(List<UdtInfo> definitions) {
        this.definitions = definitions;
    }

    /** Information about a single UDT definition. */
    public static class UdtInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private String path;
        private boolean hasMembers;

        public UdtInfo() {}

        public UdtInfo(String name, String path, boolean hasMembers) {
            this.name = name;
            this.path = path;
            this.hasMembers = hasMembers;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public boolean isHasMembers() {
            return hasMembers;
        }

        public void setHasMembers(boolean hasMembers) {
            this.hasMembers = hasMembers;
        }
    }
}
