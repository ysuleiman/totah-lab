package totah.lab.prometheus.strategy;

import java.util.Set;

/** Safe integration skeleton for an established RESP charge derivation. */
public final class RespStrategy extends AbstractExternalMethodStrategy {

    public RespStrategy() {
        super(new StrategyDescriptor(
                "resp",
                "RESP charges",
                "RESP",
                Set.of(ParameterizationCapability.ATOMIC_CHARGES),
                "skeleton-1"));
    }
}
