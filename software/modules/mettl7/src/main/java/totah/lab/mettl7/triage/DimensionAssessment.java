package totah.lab.mettl7.triage;

import java.util.List;
import java.util.Objects;

public record DimensionAssessment(
        AssessmentLevel level,
        List<String> reasons,
        List<EvidenceObservation> evidence) {
    public DimensionAssessment {
        Objects.requireNonNull(level, "level");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        if (reasons.isEmpty() || reasons.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("at least one non-blank reason is required");
        }
    }
}
