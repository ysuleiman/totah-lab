package totah.lab.prometheus.planning;

import java.util.List;
import java.util.Objects;

/** Deterministic recommendation for one assessed strategy. */
public record StrategyRecommendationResult(
        String strategyId,
        StrategyRecommendation recommendation,
        StrategyFeasibilityAssessment feasibility,
        StrategyCostEstimate cost,
        List<String> reasons) {
    public StrategyRecommendationResult {
        Objects.requireNonNull(strategyId, "strategyId");
        Objects.requireNonNull(recommendation, "recommendation");
        Objects.requireNonNull(feasibility, "feasibility");
        Objects.requireNonNull(cost, "cost");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
    }
}
