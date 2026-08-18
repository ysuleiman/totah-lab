package totah.lab.prometheus.neural.ferminet.runtime;

import java.util.List;
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

    /** Read-only orbital/determinant view used by HF pretraining qualification. */
    public static OrbitalSnapshot orbitals(
            FermiNetV1State state,
            QuantumCoordinates coordinates) {
        FermiNetV1State.ReferenceSnapshot snapshot =
                Objects.requireNonNull(state, "state")
                        .referenceSnapshot(coordinates);
        List<DeterminantSnapshot> determinants = snapshot.determinants().stream()
                .map(value -> new DeterminantSnapshot(
                        value.determinant(), value.orbitalMatrix(),
                        value.sign(), value.logMagnitude()))
                .toList();
        return new OrbitalSnapshot(
                determinants, snapshot.sign(), snapshot.logAbsoluteWavefunction());
    }

    public record ValueSnapshot(int sign, double logAbsoluteWavefunction) {}

    public record OrbitalSnapshot(
            List<DeterminantSnapshot> determinants,
            int sign,
            double logAbsoluteWavefunction) {
        public OrbitalSnapshot {
            determinants = List.copyOf(determinants);
        }
    }

    public record DeterminantSnapshot(
            int determinant,
            double[][] orbitalMatrix,
            int sign,
            double logMagnitude) {
        public DeterminantSnapshot {
            orbitalMatrix = copy(orbitalMatrix);
        }
        @Override public double[][] orbitalMatrix() { return copy(orbitalMatrix); }
        private static double[][] copy(double[][] values) {
            double[][] result = new double[values.length][];
            for (int i = 0; i < values.length; i++) result[i] = values[i].clone();
            return result;
        }
    }

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
