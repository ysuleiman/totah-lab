package totah.lab.prometheus.strategy;

import java.util.Set;

/** Safe integration skeleton for a QUBEKit/QUBE whole-molecule workflow. */
public final class QubeKitStrategy extends AbstractExternalMethodStrategy {

    public QubeKitStrategy() {
        super(new StrategyDescriptor(
                "qubekit",
                "QUBEKit/QUBE",
                "QUBEKit",
                Set.of(
                        ParameterizationCapability.ATOMIC_CHARGES,
                        ParameterizationCapability.BONDS,
                        ParameterizationCapability.ANGLES,
                        ParameterizationCapability.PROPER_TORSIONS,
                        ParameterizationCapability.IMPROPERS,
                        ParameterizationCapability.VAN_DER_WAALS,
                        ParameterizationCapability.WHOLE_MOLECULE_PARAMETERIZATION),
                "skeleton-1"));
    }
}
