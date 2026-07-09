package dev.bwdesigngroup.flint.common.protocol.methods.tags;

import java.io.Serializable;

/** Result for the tags.edit method. Returns whether the edit was successful. */
public class TagEditResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private boolean success;
    private String error;

    public TagEditResult() {}

    public TagEditResult(boolean success, String error) {
        this.success = success;
        this.error = error;
    }

    public static TagEditResult success() {
        return new TagEditResult(true, null);
    }

    public static TagEditResult failure(String error) {
        return new TagEditResult(false, error);
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
