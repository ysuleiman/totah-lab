package totah.lab.prometheus.planning;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, deterministically deduplicated requirements declared by a strategy. */
public record EvidenceRequirementSet(String strategyId, List<StrategyEvidenceRequirement> requirements) {
    public EvidenceRequirementSet {
        Objects.requireNonNull(strategyId, "strategyId");
        if (strategyId.isBlank()) throw new IllegalArgumentException("strategyId must be non-blank");
        requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
        Map<String, StrategyEvidenceRequirement> byKey = new LinkedHashMap<>();
        for (StrategyEvidenceRequirement requirement : requirements) {
            StrategyEvidenceRequirement prior = byKey.putIfAbsent(requirement.scientificKey(), requirement);
            if (prior != null) {
                throw new IllegalArgumentException("duplicate scientific requirement: " + requirement.scientificKey());
            }
        }
    }

    public static EvidenceRequirementSet deduplicated(String strategyId,
            List<StrategyEvidenceRequirement> requirements) {
        Map<String, StrategyEvidenceRequirement> unique = new LinkedHashMap<>();
        for (StrategyEvidenceRequirement requirement : requirements) {
            unique.putIfAbsent(requirement.scientificKey(), requirement);
        }
        return new EvidenceRequirementSet(strategyId, List.copyOf(unique.values()));
    }
}
