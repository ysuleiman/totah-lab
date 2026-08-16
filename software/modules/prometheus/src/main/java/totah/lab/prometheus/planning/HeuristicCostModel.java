package totah.lab.prometheus.planning;

import java.util.Map;
import java.util.Objects;

import totah.lab.prometheus.evidence.CalculationType;

/**
 * Simple heuristic cost model: one job per requirement, with per-calculation-type
 * base CPU hours supplied at construction.
 *
 * <p>Derived quantities (coarse pre-authorization estimates, not measurements):
 * <ul>
 *   <li>{@code jobCount} = 1</li>
 *   <li>{@code expectedWallHours} = cpuHours / 32 (assume a 32-core remote node)</li>
 *   <li>{@code expectedLocalRuntimeHours} = cpuHours / 8 (assume 8 local cores)</li>
 *   <li>{@code estimatedRemoteCostUsd} = cpuHours * pricePerCpuHour</li>
 * </ul>
 *
 * <p>A calculation type with no configured base estimate resolves to 0 CPU hours —
 * the absence of a heuristic must never fabricate a cost.
 */
public final class HeuristicCostModel implements CostModel {

    private static final int ASSUMED_REMOTE_CORES = 32;
    private static final int ASSUMED_LOCAL_CORES = 8;

    private final Map<CalculationType, Double> baseCpuHoursByType;
    private final double pricePerCpuHourUsd;

    public HeuristicCostModel(
            Map<CalculationType, Double> baseCpuHoursByType,
            double pricePerCpuHourUsd) {

        Objects.requireNonNull(baseCpuHoursByType, "baseCpuHoursByType");
        baseCpuHoursByType.forEach((type, hours) -> {
            Objects.requireNonNull(type, "calculation type key");
            Objects.requireNonNull(hours, "base CPU hours for " + type);
            if (!Double.isFinite(hours) || hours < 0) {
                throw new IllegalArgumentException("base CPU hours must be >= 0 for " + type);
            }
        });
        this.baseCpuHoursByType = Map.copyOf(baseCpuHoursByType);
        if (!Double.isFinite(pricePerCpuHourUsd) || pricePerCpuHourUsd < 0) {
            throw new IllegalArgumentException("pricePerCpuHourUsd must be >= 0");
        }
        this.pricePerCpuHourUsd = pricePerCpuHourUsd;
    }

    @Override
    public CostEstimate estimate(EvidenceRequirement requirement) {
        Objects.requireNonNull(requirement, "requirement");
        double cpuHours = baseCpuHoursByType.getOrDefault(requirement.calculationType(), 0.0);
        return new CostEstimate(
                1,
                cpuHours,
                cpuHours / ASSUMED_REMOTE_CORES,
                cpuHours / ASSUMED_LOCAL_CORES,
                cpuHours * pricePerCpuHourUsd);
    }
}
