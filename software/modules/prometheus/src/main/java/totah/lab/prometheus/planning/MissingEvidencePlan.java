package totah.lab.prometheus.planning;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Minimal, non-executing result of matching a strategy to an evidence store. */
public record MissingEvidencePlan(
        String strategyId,
        List<StrategyRequirementResolution> resolutions,
        List<CalculationSpecification> newCalculations) {
    public MissingEvidencePlan {
        Objects.requireNonNull(strategyId, "strategyId");
        resolutions = List.copyOf(Objects.requireNonNull(resolutions, "resolutions"));
        newCalculations = List.copyOf(Objects.requireNonNull(newCalculations, "newCalculations"));
        Set<String> checksums = new HashSet<>();
        for (CalculationSpecification calculation : newCalculations) {
            if (!checksums.add(calculation.checksum())) {
                throw new IllegalArgumentException("new calculations must be deduplicated");
            }
        }
        Set<String> development = new HashSet<>();
        Set<String> holdout = new HashSet<>();
        for (StrategyRequirementResolution resolution : resolutions) {
            if (resolution.requirement().scientific().requirement().role() == DatasetRole.HOLDOUT) {
                holdout.addAll(resolution.evidenceHashes());
            } else if (resolution.requirement().scientific().requirement().role() == DatasetRole.DEVELOPMENT) {
                development.addAll(resolution.evidenceHashes());
            }
        }
        development.retainAll(holdout);
        if (!development.isEmpty()) {
            throw new IllegalArgumentException("holdout evidence cannot also be assigned to development: " + development);
        }
    }
}
