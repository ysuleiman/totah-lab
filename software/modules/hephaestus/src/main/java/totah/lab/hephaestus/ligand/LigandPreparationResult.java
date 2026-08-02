package totah.lab.hephaestus.ligand;

import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hephaestus.model.PreparationIssue;
import totah.lab.hephaestus.model.Severity;

import java.util.List;
import java.util.Objects;

public record LigandPreparationResult(
        PreparedLigand preparedLigand,
        List<PreparationIssue> issues) {

    public LigandPreparationResult {
        Objects.requireNonNull(preparedLigand, "preparedLigand");
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
