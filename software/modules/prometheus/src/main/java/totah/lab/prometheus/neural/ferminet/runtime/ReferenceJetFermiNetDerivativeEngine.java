package totah.lab.prometheus.neural.ferminet.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import totah.lab.prometheus.variational.QuantumCoordinates;

/** Adapter over the validated scalar jet implementation. */
final class ReferenceJetFermiNetDerivativeEngine
        implements FermiNetDerivativeEngine {

    private final int sampleParallelism;

    ReferenceJetFermiNetDerivativeEngine(int sampleParallelism) {
        this.sampleParallelism = sampleParallelism;
    }

    @Override
    public FermiNetDerivativeEngineType type() {
        return FermiNetDerivativeEngineType.REFERENCE_JET;
    }

    @Override public int sampleParallelism() { return sampleParallelism; }

    @Override
    public FermiNetStateAccess.SpatialSnapshot spatial(
            FermiNetV1State state,
            QuantumCoordinates coordinates) {
        FermiNetV1State.SpatialEvaluation evaluation =
                Objects.requireNonNull(state, "state")
                        .spatialEvaluation(coordinates);
        return new FermiNetStateAccess.SpatialSnapshot(
                evaluation.sign(), evaluation.logAbsoluteWavefunction(),
                evaluation.logCoordinateGradient(),
                evaluation.laplacianOverWavefunction());
    }

    @Override
    public FermiNetStateAccess.NuclearSnapshot nuclear(
            FermiNetV1State state,
            QuantumCoordinates coordinates) {
        FermiNetV1State.NuclearEvaluation evaluation =
                Objects.requireNonNull(state, "state")
                        .nuclearEvaluation(coordinates);
        return new FermiNetStateAccess.NuclearSnapshot(
                evaluation.sign(), evaluation.logAbsoluteWavefunction(),
                evaluation.logNuclearGradient());
    }

    @Override
    public FermiNetStateAccess.DirectionalSnapshot directional(
            FermiNetV1State state,
            QuantumCoordinates coordinates,
            FermiNetStateAccess.NuclearDirection nuclearDirection,
            FermiNetStateAccess.ElectronDirection electronDirection) {
        Objects.requireNonNull(nuclearDirection, "nuclearDirection");
        Objects.requireNonNull(electronDirection, "electronDirection");
        FermiNetV1State.DirectionalEvaluation evaluation =
                Objects.requireNonNull(state, "state").directionalEvaluation(
                        coordinates, nuclearDirection.values(),
                        electronDirection.values());
        return new FermiNetStateAccess.DirectionalSnapshot(
                evaluation.sign(), evaluation.logAbsoluteWavefunction(),
                evaluation.directionalLogAbsoluteWavefunction(),
                evaluation.laplacianOverWavefunction(),
                evaluation.directionalLaplacianOverWavefunction());
    }

    @Override
    public FermiNetStateAccess.DirectionalBatchSnapshot directionalBatch(
            FermiNetV1State state,
            QuantumCoordinates coordinates,
            List<FermiNetStateAccess.NuclearDirection> nuclearDirections,
            List<FermiNetStateAccess.ElectronDirection> electronDirections) {
        Objects.requireNonNull(nuclearDirections, "nuclearDirections");
        Objects.requireNonNull(electronDirections, "electronDirections");
        if (nuclearDirections.size() != electronDirections.size()
                || nuclearDirections.isEmpty()) {
            throw new IllegalArgumentException("invalid derivative direction batch");
        }
        List<FermiNetStateAccess.DirectionalSnapshot> result =
                new ArrayList<>(nuclearDirections.size());
        for (int direction = 0; direction < nuclearDirections.size(); direction++) {
            result.add(directional(state, coordinates,
                    nuclearDirections.get(direction),
                    electronDirections.get(direction)));
        }
        return new FermiNetStateAccess.DirectionalBatchSnapshot(
                spatial(state, coordinates), result);
    }
}
