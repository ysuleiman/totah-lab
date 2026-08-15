package totah.lab.prometheus.strategy;

import java.util.List;
import java.util.Objects;

/**
 * A strategy's non-numeric assessment of an evidence plan. Quantum and
 * classical supporting hashes remain separate and retain their provenance.
 */
public record StrategyPlanAssessment(
        String strategyId,
        StrategyReadiness readiness,
        List<String> quantumEvidenceHashes,
        List<String> classicalEvidenceHashes,
        List<String> reasons) {

    public StrategyPlanAssessment {
        strategyId = requireNonBlank(strategyId, "strategyId");
        Objects.requireNonNull(readiness, "readiness");
        quantumEvidenceHashes = List.copyOf(
                Objects.requireNonNull(quantumEvidenceHashes, "quantumEvidenceHashes"));
        classicalEvidenceHashes = List.copyOf(
                Objects.requireNonNull(classicalEvidenceHashes, "classicalEvidenceHashes"));
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        if (reasons.isEmpty() || reasons.stream().anyMatch(reason -> reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("at least one non-blank reason is required");
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
        return value;
    }
}
