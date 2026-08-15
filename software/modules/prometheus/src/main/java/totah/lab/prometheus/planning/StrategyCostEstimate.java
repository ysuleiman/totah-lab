package totah.lab.prometheus.planning;

import java.util.Objects;

/** Cost summary that may only be constructed from a deduplicated missing-evidence plan. */
public record StrategyCostEstimate(String strategyId, int newQmJobs, int newMmJobs, CostEstimate total) {
    public StrategyCostEstimate {
        Objects.requireNonNull(strategyId, "strategyId");
        if (newQmJobs < 0 || newMmJobs < 0) throw new IllegalArgumentException("job counts must be >= 0");
        Objects.requireNonNull(total, "total");
        if (total.jobCount() != newQmJobs + newMmJobs) {
            throw new IllegalArgumentException("cost job count must equal QM + MM jobs");
        }
    }

    public static StrategyCostEstimate from(MissingEvidencePlan plan) {
        Objects.requireNonNull(plan, "plan");
        int qm = 0;
        int mm = 0;
        CostEstimate total = CostEstimate.zero();
        for (CalculationSpecification specification : plan.newCalculations()) {
            switch (specification.calculationType()) {
                case CLASSICAL_FIXED_GEOMETRY_ENERGY, ENERGY_DECOMPOSITION -> mm++;
                default -> qm++;
            }
            total = total.plus(specification.estimatedCost());
        }
        return new StrategyCostEstimate(plan.strategyId(), qm, mm, total);
    }
}
