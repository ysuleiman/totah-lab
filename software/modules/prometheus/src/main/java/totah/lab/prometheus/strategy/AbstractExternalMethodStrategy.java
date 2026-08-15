package totah.lab.prometheus.strategy;

import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.planning.EvidenceGenerationPlan;
import totah.lab.prometheus.planning.PlanDecision;

/** Fail-closed base for established external methods that are not integrated yet. */
abstract class AbstractExternalMethodStrategy implements ParameterizationStrategy {

    private final StrategyDescriptor descriptor;

    protected AbstractExternalMethodStrategy(StrategyDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
    }

    @Override
    public final StrategyDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public StrategyProposal propose(StrategyContext context) {
        requireContext(context);
        return new StrategyProposal(
                descriptor.strategyId(),
                StrategyReadiness.EXTERNAL_METHOD_NOT_INTEGRATED,
                List.of(),
                List.of("external scientific method is declared but not integrated; "
                        + "no evidence requirements or parameter values were invented"));
    }

    @Override
    public StrategyPlanAssessment assessPlan(StrategyContext context, EvidenceGenerationPlan plan) {
        requireContext(context);
        Objects.requireNonNull(plan, "plan");
        boolean blocked = plan.resolutions().stream().anyMatch(resolution ->
                resolution.decision() == PlanDecision.BLOCKED
                        || resolution.decision() == PlanDecision.INCOMPATIBLE_EXISTING);
        StrategyReadiness readiness = blocked
                ? StrategyReadiness.BLOCKED
                : StrategyReadiness.EXTERNAL_METHOD_NOT_INTEGRATED;
        String reason = blocked
                ? "evidence plan contains blocked or incompatible requirements"
                : "plan can be inspected, but the external scientific method is not integrated";
        return new StrategyPlanAssessment(
                descriptor.strategyId(),
                readiness,
                context.evidence().quantumEvidenceHashes(),
                context.evidence().classicalEvidenceHashes(),
                List.of(reason));
    }

    private void requireContext(StrategyContext context) {
        Objects.requireNonNull(context, "context");
    }
}
