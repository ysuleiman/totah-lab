package totah.lab.prometheus.strategy;

import totah.lab.prometheus.planning.EvidenceRequirementSet;
import totah.lab.prometheus.planning.StrategyScientificScope;

/**
 * Scientific strategy contract independent of any particular executable.
 *
 * <p>The requirement set says what scientific evidence the methodology needs;
 * the scope says which model terms and production forms it can actually solve.
 * External executables remain dependencies of individual requirements, not the
 * identity of the methodology itself.
 */
public interface EvidenceDrivenParameterizationStrategy extends ParameterizationStrategy {

    EvidenceRequirementSet evidenceRequirements(StrategyContext context);

    StrategyScientificScope scientificScope();
}
