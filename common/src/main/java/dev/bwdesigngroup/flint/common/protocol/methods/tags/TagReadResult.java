package dev.bwdesigngroup.flint.common.protocol.methods.tags;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Result for the tags.read method. Returns values for one or more tag paths. */
public class TagReadResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<TagValueInfo> results;

    public TagReadResult() {
        this.results = new ArrayList<>();
    }

    public TagReadResult(List<TagValueInfo> results) {
        this.results = results != null ? results : new ArrayList<>();
    }

    public List<TagValueInfo> getResults() {
        return results;
    }

    public void setResults(List<TagValueInfo> results) {
        this.results = results;
    }

    /** Value information for a single tag. */
    public static class TagValueInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private String path;
        private Object value;
        private String dataType;
        private String quality;
        private String timestamp;

        public TagValueInfo() {}

        public TagValueInfo(
                String path, Object value, String dataType, String quality, String timestamp) {
            this.path = path;
            this.value = value;
            this.dataType = dataType;
            this.quality = quality;
            this.timestamp = timestamp;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public String getDataType() {
            return dataType;
        }

        public void setDataType(String dataType) {
            this.dataType = dataType;
        }

        public String getQuality() {
            return quality;
        }

        public void setQuality(String quality) {
            this.quality = quality;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
    }
}
