package totah.lab.prometheus.neural.ferminet.runtime;

import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.molecular.LocalEnergyComponents;
import totah.lab.prometheus.variational.QuantumCoordinates;

/** Narrow public sampling bridge for FermiNet drivers and diagnostics. */
public final class FermiNetRuntimeSampling {

    private FermiNetRuntimeSampling() {}

    public static Result sampleParallel(
            FermiNetV1State state,
            Request request,
            List<QuantumCoordinates> initialWalkers,
            int parallelism) {
        try (FermiNetVmcParallel sampler = new FermiNetVmcParallel(parallelism)) {
            return snapshot(sampler.sample(state, configuration(request), initialWalkers));
        }
    }

    public static Result sampleSerial(
            FermiNetV1State state,
            Request request,
            List<QuantumCoordinates> initialWalkers) {
        return snapshot(new FermiNetVmc().sample(
                state, configuration(request), initialWalkers));
    }

    public static Result sampleSerial(FermiNetV1State state, Request request) {
        return snapshot(new FermiNetVmc().sample(state, configuration(request)));
    }

    public static Session beginSession(
            FermiNetV1State state,
            Request request,
            List<QuantumCoordinates> initialWalkers,
            int parallelism) {
        FermiNetVmcParallel sampler = new FermiNetVmcParallel(parallelism);
        try {
            return new Session(sampler,
                    sampler.beginSession(state, configuration(request), initialWalkers));
        } catch (RuntimeException exception) {
            sampler.close();
            throw exception;
        }
    }

    /** Restores the exact persistent sampler stream captured by a checkpoint. */
    public static Session resumeSession(
            FermiNetV1State state,
            Request request,
            FermiNetOptimizationCheckpoint checkpoint,
            int parallelism) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(checkpoint, "checkpoint");
        String parameters = FermiNetOptimizationCheckpoint.parameterChecksum(
                FermiNetStateAccess.parameterSnapshot(state));
        if (!checkpoint.parameterChecksum().equals(parameters)) {
            throw new IllegalArgumentException("checkpoint parameter checksum mismatch");
        }
        if (!checkpoint.walkerChecksum().equals(
                FermiNetOptimizationCheckpoint.walkerChecksum(checkpoint.walkers()))) {
            throw new IllegalArgumentException("checkpoint walker checksum mismatch");
        }
        var sampling = new FermiNetVariationalOptimizer.SamplingConfiguration(
                request.walkers(), request.warmupSweeps(), request.retainedPerWalker(),
                request.sweepsBetweenRetained(), request.stepSizeBohr(), request.seed());
        if (!checkpoint.samplingConfigurationIdentity().equals(
                FermiNetOptimizationCheckpoint.samplingIdentity(sampling))) {
            throw new IllegalArgumentException("checkpoint sampling configuration mismatch");
        }
        FermiNetVmcParallel sampler = new FermiNetVmcParallel(parallelism);
        try {
            return new Session(sampler, sampler.restoreSession(
                    state, configuration(request), checkpoint.walkers(),
                    checkpoint.serializedRandomState()));
        } catch (RuntimeException exception) {
            sampler.close();
            throw exception;
        }
    }

    public static LocalEnergyComponents localEnergy(
            FermiNetV1State state,
            QuantumCoordinates coordinates) {
        return FermiNetVmc.localEnergy(state, coordinates);
    }

    /** Computes the sampling value and authoritative local energy from one spatial pass. */
    public static LocalEnergySnapshot localEnergyWithLog(
            FermiNetV1State state,
            QuantumCoordinates coordinates) {
        Objects.requireNonNull(state, "state");
        FermiNetV1State.SpatialEvaluation evaluation = state.spatialEvaluation(coordinates);
        return new LocalEnergySnapshot(
                evaluation.sign(), evaluation.logAbsoluteWavefunction(),
                FermiNetVmc.localEnergy(state, coordinates, evaluation));
    }

    public static LocalEnergyComponents localEnergy(
            FermiNetV1State state,
            QuantumCoordinates coordinates,
            FermiNetV1State.Evaluation evaluation) {
        return FermiNetVmc.localEnergy(state, coordinates, evaluation);
    }

    private static FermiNetVmc.Configuration configuration(Request request) {
        Objects.requireNonNull(request, "request");
        return new FermiNetVmc.Configuration(
                request.walkers(), request.warmupSweeps(),
                request.retainedPerWalker(), request.sweepsBetweenRetained(),
                request.stepSizeBohr(), request.seed());
    }

    static Result snapshot(FermiNetVmc.Result result) {
        return new Result(result.acceptance(), result.samples(), result.localEnergies());
    }

    public record Request(
            int walkers,
            int warmupSweeps,
            int retainedPerWalker,
            int sweepsBetweenRetained,
            double stepSizeBohr,
            long seed) {
        public Request {
            if (walkers < 1 || warmupSweeps < 0 || retainedPerWalker < 1
                    || sweepsBetweenRetained < 1 || !(stepSizeBohr > 0.0)
                    || !Double.isFinite(stepSizeBohr)) {
                throw new IllegalArgumentException("invalid FermiNet sampling request");
            }
        }
    }

    public record Result(
            double acceptance,
            List<QuantumCoordinates> samples,
            List<LocalEnergyComponents> localEnergies) {
        public Result {
            if (!Double.isFinite(acceptance)) {
                throw new IllegalArgumentException("non-finite acceptance");
            }
            samples = List.copyOf(samples);
            localEnergies = List.copyOf(localEnergies);
        }
    }

    public record Continuation(Result result, long proposed, long accepted) {}

    public record LocalEnergySnapshot(
            int sign,
            double logAbsoluteWavefunction,
            LocalEnergyComponents localEnergy) {
        public LocalEnergySnapshot {
            Objects.requireNonNull(localEnergy, "localEnergy");
        }
    }

    public static final class Session implements AutoCloseable {
        private final FermiNetVmcParallel sampler;
        private final FermiNetVmcParallel.SamplingSession session;

        private Session(
                FermiNetVmcParallel sampler,
                FermiNetVmcParallel.SamplingSession session) {
            this.sampler = sampler;
            this.session = session;
        }

        public Continuation sample(
                FermiNetV1State state,
                int warmupSweeps,
                int retainedPerWalker,
                int sweepsBetweenRetained) {
            FermiNetVmcParallel.ContinuationResult continuation = session.sample(
                    state, warmupSweeps, retainedPerWalker, sweepsBetweenRetained);
            return new Continuation(
                    snapshot(continuation.result()),
                    continuation.proposed(), continuation.accepted());
        }

        @Override
        public void close() {
            sampler.close();
        }
    }
}
