package totah.lab.prometheus.planning;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of planning: one resolution per requirement, the new
 * calculations that must be specified and (after explicit authorization)
 * executed, and the aggregate pre-authorization cost of those new
 * calculations.
 *
 * <p>A plan is a pure value: it carries no execution capability. No expensive
 * calculation may launch from a plan without explicit external authorization.
 */
public record EvidenceGenerationPlan(
        List<RequirementResolution> resolutions,
        List<CalculationSpecification> newCalculations,
        CostEstimate totalCost) {

    public EvidenceGenerationPlan {
        resolutions = List.copyOf(Objects.requireNonNull(resolutions, "resolutions"));
        newCalculations = List.copyOf(Objects.requireNonNull(newCalculations, "newCalculations"));
        Objects.requireNonNull(totalCost, "totalCost");
    }

    /** Builds a plan whose total cost is the aggregate of the new calculations' estimates. */
    public static EvidenceGenerationPlan of(
            List<RequirementResolution> resolutions,
            List<CalculationSpecification> newCalculations) {

        List<CostEstimate> costs = newCalculations.stream()
                .map(CalculationSpecification::estimatedCost)
                .toList();
        return new EvidenceGenerationPlan(resolutions, newCalculations, CostEstimate.aggregate(costs));
    }
}
