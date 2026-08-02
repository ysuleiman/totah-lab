package totah.lab.hephaestus.preparation;

import totah.lab.hephaestus.model.PreparationIssue;
import totah.lab.hephaestus.model.Severity;

import java.util.List;
import java.util.Objects;

public record OperationResult<T>(
        T value,
        List<PreparationIssue> issues) {

    public OperationResult {
        Objects.requireNonNull(value, "value");
        issues = issues == null ? List.of() : List.copyOf(issues);

        if (issues.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "issues must not contain null elements.");
        }
    }

    public static <T> OperationResult<T> success(T value) {
        return new OperationResult<>(value, List.of());
    }

    public static <T> OperationResult<T> withIssue(
            T value,
            PreparationIssue issue) {
        return new OperationResult<>(
                value,
                List.of(Objects.requireNonNull(issue, "issue")));
    }

    public boolean hasFatalIssue() {
        return issues.stream().anyMatch(PreparationIssue::fatal);
    }
}
