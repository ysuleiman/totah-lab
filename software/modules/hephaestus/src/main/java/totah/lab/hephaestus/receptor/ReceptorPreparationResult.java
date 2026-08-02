package totah.lab.hephaestus.receptor;

import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.model.PreparationIssue;
import totah.lab.hephaestus.model.Severity;

import java.util.List;
import java.util.Objects;

public record ReceptorPreparationResult(
        PreparedProtein preparedProtein,
        List<PreparationIssue> issues) {

    public ReceptorPreparationResult {
        Objects.requireNonNull(preparedProtein, "preparedProtein");
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean successful() {
        return issues.stream().noneMatch(PreparationIssue::fatal);
    }

    public boolean hasWarnings() {
        return issues.stream()
                .anyMatch(issue -> issue.severity() == Severity.WARNING);
    }

    public boolean hasErrors() {
        return issues.stream()
                .anyMatch(issue -> issue.severity() == Severity.ERROR);
    }
}
