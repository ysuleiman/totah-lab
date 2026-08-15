package totah.lab.prometheus.planning;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.diagnosis.DiagnosisReport;
import totah.lab.prometheus.diagnosis.FunctionalFormClassification;

/** Applies existing model-form diagnoses and holdout/infrastructure gates to a strategy plan. */
public final class StrategyAssessor {

    public StrategyRecommendationResult assess(
            StrategyScientificScope scope,
            MissingEvidencePlan plan,
            DiagnosisReport diagnosis) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(diagnosis, "diagnosis");

        List<String> scientificReasons = new ArrayList<>();
        ScientificFeasibility scientific = ScientificFeasibility.SCIENTIFICALLY_FEASIBLE;
        boolean harmonicFailure = !diagnosis.byClassification(
                FunctionalFormClassification.HARMONIC_FORM_INSUFFICIENT).isEmpty();
        boolean coupledFailure = !diagnosis.byClassification(
                FunctionalFormClassification.COUPLED_COORDINATE_BEHAVIOR).isEmpty();
        boolean nonbondedFailure = !diagnosis.byClassification(
                FunctionalFormClassification.NONBONDED_FORM_INSUFFICIENT).isEmpty();
        if ((harmonicFailure || coupledFailure) && scope.claimsBondedSolution()
                && !scope.parameterizedTerms().contains(FunctionalFormRequirement.COUPLED_COORDINATES)) {
            scientific = ScientificFeasibility.FUNCTIONAL_FORM_INCOMPATIBLE;
            scientificReasons.add("existing diagnosis rejects an uncoupled harmonic bonded solution");
        }
        if (nonbondedFailure && scope.claimsNonbondedSolution()
                && !scope.parameterizedTerms().contains(FunctionalFormRequirement.POLARIZATION)) {
            scientific = ScientificFeasibility.FUNCTIONAL_FORM_INCOMPATIBLE;
            scientificReasons.add("existing diagnosis rejects the declared fixed-charge nonbonded form");
        }
        boolean hasHoldout = plan.resolutions().stream().anyMatch(
                r -> r.decision() == EvidenceReuseDecision.RESERVE_AS_HOLDOUT);
        if (!hasHoldout && scientific == ScientificFeasibility.SCIENTIFICALLY_FEASIBLE) {
            scientific = ScientificFeasibility.INSUFFICIENT_VALIDATION_PATH;
            scientificReasons.add("no independent accepted evidence is reserved as holdout");
        }
        if (scientificReasons.isEmpty()) scientificReasons.add("declared functional form is not contradicted by current diagnoses");

        List<String> infrastructureReasons = plan.resolutions().stream()
                .filter(r -> r.decision() == EvidenceReuseDecision.BLOCKED_BY_INFRASTRUCTURE)
                .map(StrategyRequirementResolution::infrastructureReason).toList();
        InfrastructureFeasibility infrastructure = infrastructureReasons.isEmpty()
                ? InfrastructureFeasibility.AVAILABLE : InfrastructureFeasibility.INFRASTRUCTURE_BLOCKED;
        StrategyFeasibilityAssessment feasibility = new StrategyFeasibilityAssessment(plan.strategyId(),
                scientific, infrastructure, scientificReasons, infrastructureReasons);
        StrategyCostEstimate cost = StrategyCostEstimate.from(plan);
        StrategyRecommendation recommendation = recommendation(feasibility, plan);
        return new StrategyRecommendationResult(plan.strategyId(), recommendation, feasibility, cost,
                List.of("recommendation follows scientific suitability before execution availability"));
    }

    private static StrategyRecommendation recommendation(StrategyFeasibilityAssessment feasibility,
            MissingEvidencePlan plan) {
        if (feasibility.scientific() == ScientificFeasibility.FUNCTIONAL_FORM_INCOMPATIBLE) {
            return StrategyRecommendation.FUNCTIONAL_FORM_INCOMPATIBLE;
        }
        if (feasibility.scientific() == ScientificFeasibility.INSUFFICIENT_VALIDATION_PATH) {
            return StrategyRecommendation.INSUFFICIENT_VALIDATION_PATH;
        }
        if (feasibility.scientific() != ScientificFeasibility.SCIENTIFICALLY_FEASIBLE) {
            return StrategyRecommendation.NOT_RECOMMENDED;
        }
        if (feasibility.infrastructure() == InfrastructureFeasibility.INFRASTRUCTURE_BLOCKED) {
            return StrategyRecommendation.INFRASTRUCTURE_BLOCKED_ONLY;
        }
        return plan.newCalculations().isEmpty()
                ? StrategyRecommendation.READY_WITH_EXISTING_EVIDENCE
                : StrategyRecommendation.READY_AFTER_MINIMAL_NEW_EVIDENCE;
    }
}
