package totah.lab.hephaestus.model;

import java.util.Objects;

public record PreparationIssue(Severity severity, String code, String message) {
    public PreparationIssue {
        Objects.requireNonNull(severity, "severity");
        code = requireNonBlank(code, "code");
        message = requireNonBlank(message, "message");
    }

    public boolean fatal() {
        return severity == Severity.ERROR;
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return normalized;
    }
}
