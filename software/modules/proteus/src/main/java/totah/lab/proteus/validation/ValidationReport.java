package totah.lab.proteus.validation;

import java.util.List;

public record ValidationReport(List<ValidationIssue> issues) {
    public ValidationReport { issues = issues == null ? List.of() : List.copyOf(issues); }
    public boolean valid() { return !hasErrors(); }
    public boolean hasErrors() { return issues.stream().anyMatch(i -> i.severity() == ValidationSeverity.ERROR); }
    public boolean hasWarnings() { return issues.stream().anyMatch(i -> i.severity() == ValidationSeverity.WARNING); }
    public static ValidationReport validReport() { return new ValidationReport(List.of()); }
}
