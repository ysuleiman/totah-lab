package totah.lab.prometheus.neural.ferminet.runtime;

import java.util.List;
import totah.lab.prometheus.variational.QuantumCoordinates;

/**
 * Canonical derivative execution boundary for FermiNet runtime consumers.
 *
 * <p>Implementations must preserve canonical electron-major and
 * nucleus-major Cartesian ordering. Engine selection is an engineering
 * choice only; it must not change the represented wavefunction or derivative
 * equations.
 */
public interface FermiNetDerivativeEngine {

    FermiNetDerivativeEngineType type();

    int sampleParallelism();

    FermiNetStateAccess.SpatialSnapshot spatial(
            FermiNetV1State state,
            QuantumCoordinates coordinates);

    FermiNetStateAccess.NuclearSnapshot nuclear(
            FermiNetV1State state,
            QuantumCoordinates coordinates);

    FermiNetStateAccess.DirectionalSnapshot directional(
            FermiNetV1State state,
            QuantumCoordinates coordinates,
            FermiNetStateAccess.NuclearDirection nuclearDirection,
            FermiNetStateAccess.ElectronDirection electronDirection);

    /**
     * Evaluates every paired nuclear/electron direction in canonical input
     * order. A production batched engine shares one primal evaluation across
     * the complete list.
     */
    FermiNetStateAccess.DirectionalBatchSnapshot directionalBatch(
            FermiNetV1State state,
            QuantumCoordinates coordinates,
            List<FermiNetStateAccess.NuclearDirection> nuclearDirections,
            List<FermiNetStateAccess.ElectronDirection> electronDirections);
}
