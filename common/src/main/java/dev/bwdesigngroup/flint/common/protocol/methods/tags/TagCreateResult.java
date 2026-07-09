package dev.bwdesigngroup.flint.common.protocol.methods.tags;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Result for the tags.create method. Returns creation status for each tag. */
public class TagCreateResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<TagCreateStatus> results;

    public TagCreateResult() {
        this.results = new ArrayList<>();
    }

    public TagCreateResult(List<TagCreateStatus> results) {
        this.results = results != null ? results : new ArrayList<>();
    }

    public List<TagCreateStatus> getResults() {
        return results;
    }

    public void setResults(List<TagCreateStatus> results) {
        this.results = results;
    }

    /** Creation status for a single tag. */
    public static class TagCreateStatus implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private boolean success;
        private String error;

        public TagCreateStatus() {}

        public TagCreateStatus(String name, boolean success, String error) {
            this.name = name;
            this.success = success;
            this.error = error;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
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
