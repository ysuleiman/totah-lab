package totah.lab.prometheus.strategy;

import java.util.Set;

/** Safe integration skeleton for an AmberTools/GAFF2 baseline assignment. */
public final class Gaff2BaselineStrategy extends AbstractExternalMethodStrategy {

    public Gaff2BaselineStrategy() {
        super(new StrategyDescriptor(
                "gaff2-baseline",
                "GAFF2 baseline",
                "AmberTools/GAFF2",
                Set.of(
                        ParameterizationCapability.BASELINE_ASSIGNMENT,
                        ParameterizationCapability.ATOMIC_CHARGES,
                        ParameterizationCapability.BONDS,
                        ParameterizationCapability.ANGLES,
                        ParameterizationCapability.PROPER_TORSIONS,
                        ParameterizationCapability.IMPROPERS,
                        ParameterizationCapability.VAN_DER_WAALS),
                "skeleton-1"));
    }
}
