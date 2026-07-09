package dev.bwdesigngroup.flint.common.protocol.methods.perspective;

import java.io.Serializable;

/** A performance warning from profiling. */
public class ProfileWarning implements Serializable {
    private static final long serialVersionUID = 1L;

    private String severity; // high, medium, low
    private String category; // binding, transform, structure, data
    private String message;
    private String componentPath;
    private String recommendation;

    public ProfileWarning() {}

    public ProfileWarning(
            String severity,
            String category,
            String message,
            String componentPath,
            String recommendation) {
        this.severity = severity;
        this.category = category;
        this.message = message;
        this.componentPath = componentPath;
        this.recommendation = recommendation;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getComponentPath() {
        return componentPath;
    }

    public void setComponentPath(String componentPath) {
        this.componentPath = componentPath;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }
}
