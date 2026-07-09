package dev.bwdesigngroup.flint.common.protocol.methods.tags;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Result for the tags.delete method. Returns deletion status for each tag path. */
public class TagDeleteResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<TagDeleteStatus> results;

    public TagDeleteResult() {
        this.results = new ArrayList<>();
    }

    public TagDeleteResult(List<TagDeleteStatus> results) {
        this.results = results != null ? results : new ArrayList<>();
    }

    public List<TagDeleteStatus> getResults() {
        return results;
    }

    public void setResults(List<TagDeleteStatus> results) {
        this.results = results;
    }

    /** Deletion status for a single tag path. */
    public static class TagDeleteStatus implements Serializable {
        private static final long serialVersionUID = 1L;
        private String path;
        private boolean success;
        private String error;

        public TagDeleteStatus() {}

        public TagDeleteStatus(String path, boolean success, String error) {
            this.path = path;
            this.success = success;
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

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }
}
