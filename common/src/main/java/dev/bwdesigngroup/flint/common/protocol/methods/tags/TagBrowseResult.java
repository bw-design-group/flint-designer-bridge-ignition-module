package dev.bwdesigngroup.flint.common.protocol.methods.tags;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Result for the tags.browse method. Returns a list of tag nodes at a specific level in the tag
 * tree.
 */
public class TagBrowseResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<TagNodeInfo> results;

    public TagBrowseResult() {
        this.results = new ArrayList<>();
    }

    public TagBrowseResult(List<TagNodeInfo> results) {
        this.results = results != null ? results : new ArrayList<>();
    }

    public List<TagNodeInfo> getResults() {
        return results;
    }

    public void setResults(List<TagNodeInfo> results) {
        this.results = results;
    }

    /** Information about a single tag node in the browse tree. */
    public static class TagNodeInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private String fullPath;
        private String tagType;
        private String dataType;
        private boolean hasChildren;
        private String valueSource;

        public TagNodeInfo() {}

        public TagNodeInfo(
                String name,
                String fullPath,
                String tagType,
                String dataType,
                boolean hasChildren,
                String valueSource) {
            this.name = name;
            this.fullPath = fullPath;
            this.tagType = tagType;
            this.dataType = dataType;
            this.hasChildren = hasChildren;
            this.valueSource = valueSource;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFullPath() {
            return fullPath;
        }

        public void setFullPath(String fullPath) {
            this.fullPath = fullPath;
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

        public boolean isHasChildren() {
            return hasChildren;
        }

        public void setHasChildren(boolean hasChildren) {
            this.hasChildren = hasChildren;
        }

        public String getValueSource() {
            return valueSource;
        }

        public void setValueSource(String valueSource) {
            this.valueSource = valueSource;
        }
    }
}
