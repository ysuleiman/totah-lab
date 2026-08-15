package totah.lab.prometheus.strategy;

import java.util.Set;

/** Safe integration skeleton for an established torsion-fit workflow. */
public final class TorsionFitStrategy extends AbstractExternalMethodStrategy {

    public TorsionFitStrategy() {
        super(new StrategyDescriptor(
                "torsion-fit",
                "Torsion fitting",
                "torsion fitting",
                Set.of(ParameterizationCapability.PROPER_TORSIONS),
                "skeleton-1"));
    }
}
