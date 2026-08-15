package totah.lab.prometheus.strategy;

import totah.lab.prometheus.planning.EvidenceGenerationPlan;

/**
 * Service-provider interface for molecule-independent parameterization methods.
 * Implementations may propose evidence and assess an already-built plan, but
 * this interface provides no calculation executor and no holdout access.
 */
public interface ParameterizationStrategy {

    StrategyDescriptor descriptor();

    StrategyProposal propose(StrategyContext context);

    StrategyPlanAssessment assessPlan(StrategyContext context, EvidenceGenerationPlan plan);
}
