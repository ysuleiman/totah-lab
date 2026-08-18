package totah.lab.prometheus.neural.ferminet.runtime;

import java.util.Objects;

import totah.lab.prometheus.variational.QuantumCoordinates;

/** Narrow immutable bridge for persistence, pretraining, and diagnostics. */
public final class FermiNetStateAccess {

    private FermiNetStateAccess() {}

    public static FermiNetV1State replaceParameters(
            FermiNetV1State state,
            double[] parameters) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(parameters, "parameters");
        return state.withParameters(parameters.clone());
    }

    public static double[] parameterSnapshot(FermiNetV1State state) {
        return Objects.requireNonNull(state, "state").parameterArray();
    }

    public static SpatialSnapshot spatial(
            FermiNetV1State state,
            QuantumCoordinates coordinates) {
        FermiNetV1State.SpatialEvaluation evaluation =
                Objects.requireNonNull(state, "state").spatialEvaluation(coordinates);
        return new SpatialSnapshot(
                evaluation.sign(), evaluation.logAbsoluteWavefunction(),
                evaluation.logCoordinateGradient(),
                evaluation.laplacianOverWavefunction());
    }

    public static ValueSnapshot sampling(
            FermiNetV1State state,
            QuantumCoordinates coordinates) {
        FermiNetV1State.SamplingEvaluation evaluation =
                Objects.requireNonNull(state, "state").samplingEvaluation(coordinates);
        return new ValueSnapshot(
                evaluation.sign(), evaluation.logAbsoluteWavefunction());
    }

    public record ValueSnapshot(int sign, double logAbsoluteWavefunction) {}

    public record SpatialSnapshot(
            int sign,
            double logAbsoluteWavefunction,
            double[] logCoordinateGradient,
            double laplacianOverWavefunction) {
        public SpatialSnapshot {
            logCoordinateGradient = logCoordinateGradient.clone();
        }

        @Override
        public double[] logCoordinateGradient() {
            return logCoordinateGradient.clone();
        }
    }
}
