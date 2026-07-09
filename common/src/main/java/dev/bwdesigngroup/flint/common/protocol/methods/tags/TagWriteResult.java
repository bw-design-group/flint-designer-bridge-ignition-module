package dev.bwdesigngroup.flint.common.protocol.methods.tags;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Result for the tags.write method. Returns write status for each tag path. */
public class TagWriteResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<TagWriteStatus> results;

    public TagWriteResult() {
        this.results = new ArrayList<>();
    }

    public TagWriteResult(List<TagWriteStatus> results) {
        this.results = results != null ? results : new ArrayList<>();
    }

    public List<TagWriteStatus> getResults() {
        return results;
    }

    public void setResults(List<TagWriteStatus> results) {
        this.results = results;
    }

    /** Write status for a single tag. */
    public static class TagWriteStatus implements Serializable {
        private static final long serialVersionUID = 1L;
        private String path;
        private boolean success;
        private String quality;
        private String error;

        public TagWriteStatus() {}

        public TagWriteStatus(String path, boolean success, String quality, String error) {
            this.path = path;
            this.success = success;
            this.quality = quality;
            this.error = error;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getQuality() {
            return quality;
        }

        public void setQuality(String quality) {
            this.quality = quality;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }
}
