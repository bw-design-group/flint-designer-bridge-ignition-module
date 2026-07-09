package dev.bwdesigngroup.flint.common.protocol.methods;

/** Parameters for the showMessage method. */
public class ShowMessageParams {
    private String message;
    private String title;

    public ShowMessageParams() {}

    public ShowMessageParams(String message, String title) {
        this.message = message;
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
