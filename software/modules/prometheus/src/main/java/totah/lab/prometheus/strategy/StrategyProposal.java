package totah.lab.prometheus.strategy;

import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.planning.EvidenceRequirement;

/** A non-executing, provenance-bearing proposal made by a strategy. */
public record StrategyProposal(
        String strategyId,
        StrategyReadiness readiness,
        List<EvidenceRequirement> evidenceRequirements,
        List<String> reasons) {

    public StrategyProposal {
        strategyId = requireNonBlank(strategyId, "strategyId");
        Objects.requireNonNull(readiness, "readiness");
        evidenceRequirements = List.copyOf(
                Objects.requireNonNull(evidenceRequirements, "evidenceRequirements"));
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
