package totah.lab.hephaestus.validation;

import java.util.Objects;

public final class ValidationException extends IllegalStateException {
    private final ValidationReport report;
    public ValidationException(ValidationReport report) {
        super("Validation failed with " + Objects.requireNonNull(report, "report").issues().size() + " issue(s).");
        this.report = report;
    }
    public ValidationReport report() { return report; }
}
