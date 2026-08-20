package totah.lab.prometheus.neural.ferminet.runtime;

import java.util.Objects;

/** Single derivative-engine construction and selection boundary. */
public final class FermiNetDerivativeEngines {

    private FermiNetDerivativeEngines() {}

    public static FermiNetDerivativeEngine create(
            FermiNetDerivativeConfiguration configuration) {
        return switch (Objects.requireNonNull(configuration, "configuration")
                .engineType()) {
            case REFERENCE_JET -> new ReferenceJetFermiNetDerivativeEngine(
                    configuration.sampleParallelism());
            case BATCHED_FORWARD -> new BatchedForwardFermiNetDerivativeEngine(
                    configuration.sampleParallelism());
        };
    }
}
