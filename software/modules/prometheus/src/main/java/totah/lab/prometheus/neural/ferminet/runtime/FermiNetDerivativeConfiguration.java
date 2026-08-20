package totah.lab.prometheus.neural.ferminet.runtime;

import java.util.Objects;

/** Immutable canonical derivative-runtime configuration. */
public record FermiNetDerivativeConfiguration(
        FermiNetDerivativeEngineType engineType,
        int sampleParallelism) {

    public FermiNetDerivativeConfiguration {
        Objects.requireNonNull(engineType, "engineType");
        if (sampleParallelism < 1) {
            throw new IllegalArgumentException("invalid derivative sample parallelism");
        }
    }

    public FermiNetDerivativeConfiguration(
            FermiNetDerivativeEngineType engineType) {
        this(engineType, 1);
    }

    public static FermiNetDerivativeConfiguration referenceJet() {
        return new FermiNetDerivativeConfiguration(
                FermiNetDerivativeEngineType.REFERENCE_JET);
    }

    public static FermiNetDerivativeConfiguration batchedForward() {
        return new FermiNetDerivativeConfiguration(
                FermiNetDerivativeEngineType.BATCHED_FORWARD);
    }

    public static FermiNetDerivativeConfiguration batchedForward(
            int sampleParallelism) {
        return new FermiNetDerivativeConfiguration(
                FermiNetDerivativeEngineType.BATCHED_FORWARD,
                sampleParallelism);
    }
}
