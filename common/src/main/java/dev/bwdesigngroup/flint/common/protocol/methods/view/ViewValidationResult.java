package dev.bwdesigngroup.flint.common.protocol.methods.view;

import java.util.ArrayList;
import java.util.List;

/** Result container for view validation operations. */
public class ViewValidationResult {
    private boolean valid;
    private List<ValidationIssue> errors;
    private List<ValidationIssue> warnings;

    public ViewValidationResult() {
        this.valid = true;
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    public void addError(String path, String code, String message) {
        this.errors.add(new ValidationIssue(path, code, message, "error"));
        this.valid = false;
    }

    public void addWarning(String path, String code, String message) {
        this.warnings.add(new ValidationIssue(path, code, message, "warning"));
    }

    public boolean isValid() {
        return valid;
    }

    public List<ValidationIssue> getErrors() {
        return errors;
    }

    public List<ValidationIssue> getWarnings() {
        return warnings;
    }

    /** A single validation issue found in the view JSON. */
    public static class ValidationIssue {
        private final String path;
        private final String code;
        private final String message;
        private final String severity;

        public ValidationIssue(String path, String code, String message, String severity) {
            this.path = path;
            this.code = code;
            this.message = message;
            this.severity = severity;
        }

        public String getPath() {
            return path;
        }

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public String getSeverity() {
            return severity;
        }
    }
}
