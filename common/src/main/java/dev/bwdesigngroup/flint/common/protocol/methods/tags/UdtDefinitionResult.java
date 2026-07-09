package dev.bwdesigngroup.flint.common.protocol.methods.tags;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Result for the udt.getDefinition method. Returns a UDT definition with its member structure. */
public class UdtDefinitionResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private String path;
    private List<UdtMemberInfo> members;

    public UdtDefinitionResult() {
        this.members = new ArrayList<>();
    }

    public UdtDefinitionResult(String name, String path, List<UdtMemberInfo> members) {
        this.name = name;
        this.path = path;
        this.members = members != null ? members : new ArrayList<>();
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

    public List<UdtMemberInfo> getMembers() {
        return members;
    }

    public void setMembers(List<UdtMemberInfo> members) {
        this.members = members;
    }

    /** Information about a single member tag within a UDT definition. */
    public static class UdtMemberInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private String tagType;
        private String dataType;
        private Object value;
        private String valueSource;
        private String tooltip;

        public UdtMemberInfo() {}

        public UdtMemberInfo(
                String name,
                String tagType,
                String dataType,
                Object value,
                String valueSource,
                String tooltip) {
            this.name = name;
            this.tagType = tagType;
            this.dataType = dataType;
            this.value = value;
            this.valueSource = valueSource;
            this.tooltip = tooltip;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getTagType() {
            return tagType;
        }

        public void setTagType(String tagType) {
            this.tagType = tagType;
        }

        public String getDataType() {
            return dataType;
        }

        public void setDataType(String dataType) {
            this.dataType = dataType;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public String getValueSource() {
            return valueSource;
        }

        public void setValueSource(String valueSource) {
            this.valueSource = valueSource;
        }

        public String getTooltip() {
            return tooltip;
        }

        public void setTooltip(String tooltip) {
            this.tooltip = tooltip;
        }
    }
}
