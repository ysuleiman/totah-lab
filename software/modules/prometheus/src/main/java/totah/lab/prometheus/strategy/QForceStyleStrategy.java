package totah.lab.prometheus.strategy;

import java.util.Set;

/** Safe integration skeleton for a Q-Force-style workflow. */
public final class QForceStyleStrategy extends AbstractExternalMethodStrategy {

    public QForceStyleStrategy() {
        super(new StrategyDescriptor(
                "qforce-style",
                "Q-Force-style workflow",
                "Q-Force",
                Set.of(
                        ParameterizationCapability.BONDS,
                        ParameterizationCapability.ANGLES,
                        ParameterizationCapability.PROPER_TORSIONS,
                        ParameterizationCapability.IMPROPERS,
                        ParameterizationCapability.WHOLE_MOLECULE_PARAMETERIZATION),
                "skeleton-1"));
    }
}
