package totah.lab.proteus.validation;

import java.util.Objects;

public record ValidationIssue(
        ValidationSeverity severity,
        ValidationCode code,
        String message,
        String location) {
    public ValidationIssue {
        Objects.requireNonNull(severity, "severity"); Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message");
        location = location == null ? "" : location;
    }
}
