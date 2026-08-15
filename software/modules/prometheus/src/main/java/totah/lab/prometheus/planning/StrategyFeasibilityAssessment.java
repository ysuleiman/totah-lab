package totah.lab.prometheus.planning;

import java.util.List;
import java.util.Objects;

/** Scientific suitability and execution availability are deliberately independent axes. */
public record StrategyFeasibilityAssessment(
        String strategyId,
        ScientificFeasibility scientific,
        InfrastructureFeasibility infrastructure,
        List<String> scientificReasons,
        List<String> infrastructureReasons) {
    public StrategyFeasibilityAssessment {
        Objects.requireNonNull(strategyId, "strategyId");
        Objects.requireNonNull(scientific, "scientific");
        Objects.requireNonNull(infrastructure, "infrastructure");
        scientificReasons = List.copyOf(Objects.requireNonNull(scientificReasons, "scientificReasons"));
        infrastructureReasons = List.copyOf(Objects.requireNonNull(infrastructureReasons, "infrastructureReasons"));
    }
}
