package totah.lab.prometheus.neural.ferminet.runtime;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import totah.lab.prometheus.variational.QuantumCoordinates;

/** Shared-primal multi-direction forward derivative backend. */
final class BatchedForwardFermiNetDerivativeEngine
        implements FermiNetDerivativeEngine {

    private final Map<WorkspaceShape, Deque<FermiNetBatchedJetWorkspace>> workspaces =
            new LinkedHashMap<>(16, 0.75f, true);
    private final int sampleParallelism;

    BatchedForwardFermiNetDerivativeEngine(int sampleParallelism) {
        this.sampleParallelism = sampleParallelism;
    }

    @Override
    public FermiNetDerivativeEngineType type() {
        return FermiNetDerivativeEngineType.BATCHED_FORWARD;
    }

    @Override public int sampleParallelism() { return sampleParallelism; }

    @Override
    public FermiNetStateAccess.SpatialSnapshot spatial(
            FermiNetV1State state,
            QuantumCoordinates coordinates) {
        Objects.requireNonNull(state, "state");
        int nuclearDimensions = 3 * state.molecule().nuclei().size();
        int electronDimensions = 3 * state.molecule().electrons().value();
        FermiNetV1State.BatchedDirectionalEvaluation evaluation = evaluate(
                state, coordinates,
                new double[][] {new double[nuclearDimensions]},
                new double[][] {new double[electronDimensions]});
        return new FermiNetStateAccess.SpatialSnapshot(
                evaluation.sign(), evaluation.logAbsoluteWavefunction(),
                evaluation.logCoordinateGradient(),
                evaluation.laplacianOverWavefunction());
    }

    @Override
    public FermiNetStateAccess.NuclearSnapshot nuclear(
            FermiNetV1State state,
            QuantumCoordinates coordinates) {
        Objects.requireNonNull(state, "state");
        int nuclearDimensions = 3 * state.molecule().nuclei().size();
        int electronDimensions = 3 * state.molecule().electrons().value();
        double[][] nuclear = new double[nuclearDimensions][nuclearDimensions];
        double[][] electron = new double[nuclearDimensions][electronDimensions];
        for (int direction = 0; direction < nuclearDimensions; direction++) {
            nuclear[direction][direction] = 1.0;
        }
        FermiNetV1State.BatchedDirectionalEvaluation evaluation = evaluate(
                state, coordinates, nuclear, electron);
        return new FermiNetStateAccess.NuclearSnapshot(
                evaluation.sign(), evaluation.logAbsoluteWavefunction(),
                evaluation.directionalLogAbsoluteWavefunction());
    }

    @Override
    public FermiNetStateAccess.DirectionalSnapshot directional(
            FermiNetV1State state,
            QuantumCoordinates coordinates,
            FermiNetStateAccess.NuclearDirection nuclearDirection,
            FermiNetStateAccess.ElectronDirection electronDirection) {
        return directionalBatch(state, coordinates,
                List.of(nuclearDirection), List.of(electronDirection))
                .directions().get(0);
    }

    @Override
    public FermiNetStateAccess.DirectionalBatchSnapshot directionalBatch(
            FermiNetV1State state,
            QuantumCoordinates coordinates,
            List<FermiNetStateAccess.NuclearDirection> nuclearDirections,
            List<FermiNetStateAccess.ElectronDirection> electronDirections) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(nuclearDirections, "nuclearDirections");
        Objects.requireNonNull(electronDirections, "electronDirections");
        if (nuclearDirections.size() != electronDirections.size()
                || nuclearDirections.isEmpty()) {
            throw new IllegalArgumentException("invalid derivative direction batch");
        }
        double[][] nuclear = new double[nuclearDirections.size()][];
        double[][] electron = new double[electronDirections.size()][];
        for (int direction = 0; direction < nuclear.length; direction++) {
            nuclear[direction] = nuclearDirections.get(direction).values();
            electron[direction] = electronDirections.get(direction).values();
        }
        FermiNetV1State.BatchedDirectionalEvaluation evaluation = evaluate(
                state, coordinates, nuclear, electron);
        double[] log = evaluation.directionalLogAbsoluteWavefunction();
        double[] laplacian =
                evaluation.directionalLaplacianOverWavefunction();
        List<FermiNetStateAccess.DirectionalSnapshot> result =
                new ArrayList<>(log.length);
        for (int direction = 0; direction < log.length; direction++) {
            result.add(new FermiNetStateAccess.DirectionalSnapshot(
                    evaluation.sign(), evaluation.logAbsoluteWavefunction(),
                    log[direction], evaluation.laplacianOverWavefunction(),
                    laplacian[direction]));
        }
        return new FermiNetStateAccess.DirectionalBatchSnapshot(
                new FermiNetStateAccess.SpatialSnapshot(
                        evaluation.sign(), evaluation.logAbsoluteWavefunction(),
                        evaluation.logCoordinateGradient(),
                        evaluation.laplacianOverWavefunction()),
                result);
    }

    private FermiNetV1State.BatchedDirectionalEvaluation evaluate(
            FermiNetV1State state,
            QuantumCoordinates coordinates,
            double[][] nuclear,
            double[][] electron) {
        WorkspaceShape shape = new WorkspaceShape(
                3 * state.molecule().electrons().value(), nuclear.length);
        FermiNetBatchedJetWorkspace workspace = acquireWorkspace(shape);
        try {
            return state.batchedDirectionalEvaluation(
                    coordinates, nuclear, electron, workspace);
        } finally {
            workspace.reset();
            releaseWorkspace(shape, workspace);
        }
    }

    private FermiNetBatchedJetWorkspace acquireWorkspace(WorkspaceShape shape) {
        synchronized (workspaces) {
            Deque<FermiNetBatchedJetWorkspace> available =
                    workspaces.computeIfAbsent(shape, ignored -> new ArrayDeque<>());
            FermiNetBatchedJetWorkspace workspace = available.pollFirst();
            return workspace == null
                    ? new FermiNetBatchedJetWorkspace() : workspace;
        }
    }

    private void releaseWorkspace(
            WorkspaceShape shape, FermiNetBatchedJetWorkspace workspace) {
        synchronized (workspaces) {
            workspaces.computeIfAbsent(shape, ignored -> new ArrayDeque<>())
                    .addFirst(workspace);
            trimIdleWorkspaces();
        }
    }

    private void trimIdleWorkspaces() {
        int retained = retainedWorkspaceCountLocked();
        Iterator<Map.Entry<WorkspaceShape, Deque<FermiNetBatchedJetWorkspace>>>
                shapes = workspaces.entrySet().iterator();
        while (retained > sampleParallelism && shapes.hasNext()) {
            Map.Entry<WorkspaceShape, Deque<FermiNetBatchedJetWorkspace>> entry =
                    shapes.next();
            Deque<FermiNetBatchedJetWorkspace> idle = entry.getValue();
            while (retained > sampleParallelism && !idle.isEmpty()) {
                idle.removeLast();
                retained--;
            }
            if (idle.isEmpty()) shapes.remove();
        }
    }

    long retainedPrimitiveBytes() {
        synchronized (workspaces) {
            long bytes = 0L;
            for (Deque<FermiNetBatchedJetWorkspace> idle : workspaces.values()) {
                for (FermiNetBatchedJetWorkspace workspace : idle) {
                    bytes = Math.addExact(bytes, workspace.retainedPrimitiveBytes());
                }
            }
            return bytes;
        }
    }

    int retainedWorkspaceCount() {
        synchronized (workspaces) {
            return retainedWorkspaceCountLocked();
        }
    }

    int retainedShapeCount() {
        synchronized (workspaces) {
            return workspaces.size();
        }
    }

    int maximumRetainedChunkCount() {
        synchronized (workspaces) {
            int maximum = 0;
            for (Deque<FermiNetBatchedJetWorkspace> idle : workspaces.values()) {
                for (FermiNetBatchedJetWorkspace workspace : idle) {
                    maximum = Math.max(maximum, workspace.retainedChunkCount());
                }
            }
            return maximum;
        }
    }

    private int retainedWorkspaceCountLocked() {
        int retained = 0;
        for (Deque<FermiNetBatchedJetWorkspace> idle : workspaces.values()) {
            retained += idle.size();
        }
        return retained;
    }

    private record WorkspaceShape(int dimensions, int directions) {}
}
