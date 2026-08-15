package totah.lab.prometheus.strategy;

import java.util.Set;

/** Safe integration skeleton for ForceBalance optimization. */
public final class ForceBalanceStrategy extends AbstractExternalMethodStrategy {

    public ForceBalanceStrategy() {
        super(new StrategyDescriptor(
                "forcebalance",
                "ForceBalance",
                "ForceBalance",
                Set.of(
                        ParameterizationCapability.FORCE_MATCHING,
                        ParameterizationCapability.BONDS,
                        ParameterizationCapability.ANGLES,
                        ParameterizationCapability.PROPER_TORSIONS,
                        ParameterizationCapability.IMPROPERS,
                        ParameterizationCapability.WHOLE_MOLECULE_PARAMETERIZATION),
                "skeleton-1"));
    }
}
