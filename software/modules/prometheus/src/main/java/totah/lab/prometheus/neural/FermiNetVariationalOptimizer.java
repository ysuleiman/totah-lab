package totah.lab.prometheus.neural;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.molecular.LocalEnergyComponents;
import totah.lab.prometheus.variational.QuantumCoordinates;

/** Iterative orchestration of deterministic parallel VMC and production SR. */
public final class FermiNetVariationalOptimizer implements AutoCloseable {

    private final FermiNetVmcParallel vmc;

    public FermiNetVariationalOptimizer(int vmcParallelism) {
        if (vmcParallelism < 1) {
            throw new IllegalArgumentException("invalid VMC parallelism");
        }
        this.vmc = new FermiNetVmcParallel(vmcParallelism);
    }

    public IterationResult oneIteration(
            int iteration,
            FermiNetV1State state,
            List<QuantumCoordinates> walkers,
            SamplingConfiguration sampling,
            FermiNetMatrixFreeSrOptimizer.Configuration srConfiguration) {

        if (iteration < 0) {
            throw new IllegalArgumentException("iteration must be nonnegative");
        }
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(walkers, "walkers");
        Objects.requireNonNull(sampling, "sampling");
        Objects.requireNonNull(srConfiguration, "srConfiguration");
        if (walkers.size() != sampling.walkers()) {
            throw new IllegalArgumentException("initial walker count mismatch");
        }

        long started = System.nanoTime();
        long seed = Math.addExact(sampling.baseSeed(), (long) iteration);
        FermiNetVmc.Configuration vmcConfiguration =
                new FermiNetVmc.Configuration(
                        sampling.walkers(),
                        sampling.warmupSweeps(),
                        sampling.retainedPerWalker(),
                        sampling.sweepsBetweenRetained(),
                        sampling.stepSizeBohr(),
                        seed);

        long vmcStarted = System.nanoTime();
        FermiNetVmc.Result vmcResult =
                vmc.sample(state, vmcConfiguration, walkers);
        long vmcNanos = System.nanoTime() - vmcStarted;

        int expectedSamples = Math.multiplyExact(
                sampling.walkers(), sampling.retainedPerWalker());
        if (vmcResult.samples().size() != expectedSamples) {
            throw new IllegalStateException("unexpected retained VMC sample count");
        }

        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> srSamples =
                vmcResult.samples().stream()
                        .map(coordinates ->
                                new FermiNetMatrixFreeSrOptimizer.WeightedSample(
                                        1.0,
                                        coordinates))
                        .toList();

        long srStarted = System.nanoTime();
        FermiNetMatrixFreeSrOptimizer.Result srResult =
                new FermiNetMatrixFreeSrOptimizer().oneIteration(
                        state,
                        srSamples,
                        srConfiguration);
        long srNanos = System.nanoTime() - srStarted;

        int nextWalkerStart = vmcResult.samples().size() - sampling.walkers();
        List<QuantumCoordinates> nextWalkers = List.copyOf(
                vmcResult.samples().subList(
                        nextWalkerStart,
                        vmcResult.samples().size()));

        return new IterationResult(
                iteration,
                seed,
                state,
                srResult.state(),
                nextWalkers,
                vmcResult,
                srResult,
                energyStatistics(vmcResult.localEnergies()),
                vmcNanos,
                srNanos,
                System.nanoTime() - started);
    }

    public List<IterationResult> optimize(
            FermiNetV1State initialState,
            List<QuantumCoordinates> initialWalkers,
            SamplingConfiguration sampling,
            FermiNetMatrixFreeSrOptimizer.Configuration srConfiguration,
            int iterations) {

        Objects.requireNonNull(initialState, "initialState");
        Objects.requireNonNull(initialWalkers, "initialWalkers");
        Objects.requireNonNull(sampling, "sampling");
        Objects.requireNonNull(srConfiguration, "srConfiguration");
        if (iterations < 1) {
            throw new IllegalArgumentException("iterations must be positive");
        }

        FermiNetV1State state = initialState;
        List<QuantumCoordinates> walkers = List.copyOf(initialWalkers);
        List<IterationResult> results = new ArrayList<>(iterations);

        for (int iteration = 0; iteration < iterations; iteration++) {
            IterationResult result = oneIteration(
                    iteration,
                    state,
                    walkers,
                    sampling,
                    srConfiguration);
            results.add(result);
            state = result.updatedState();
            walkers = result.nextWalkers();
        }

        return List.copyOf(results);
    }

    private static EnergyStatistics energyStatistics(
            List<LocalEnergyComponents> localEnergies) {
        Objects.requireNonNull(localEnergies, "localEnergies");
        if (localEnergies.size() < 2) {
            throw new IllegalArgumentException("at least two energy samples are required");
        }
        double sum = 0.0;
        for (LocalEnergyComponents energy : localEnergies) {
            Objects.requireNonNull(energy, "local energy");
            if (!Double.isFinite(energy.totalHartree())) {
                throw new IllegalArgumentException("non-finite local energy");
            }
            sum += energy.totalHartree();
        }
        double mean = sum / localEnergies.size();
        double squaredDeviation = 0.0;
        for (LocalEnergyComponents energy : localEnergies) {
            double difference = energy.totalHartree() - mean;
            squaredDeviation += difference * difference;
        }
        double standardDeviation = Math.sqrt(
                squaredDeviation / (localEnergies.size() - 1));
        return new EnergyStatistics(
                localEnergies.size(),
                mean,
                standardDeviation,
                standardDeviation / Math.sqrt(localEnergies.size()));
    }

    @Override
    public void close() {
        vmc.close();
    }

    public record SamplingConfiguration(
            int walkers,
            int warmupSweeps,
            int retainedPerWalker,
            int sweepsBetweenRetained,
            double stepSizeBohr,
            long baseSeed) {

        public SamplingConfiguration {
            if (walkers < 1
                    || warmupSweeps < 0
                    || retainedPerWalker < 1
                    || sweepsBetweenRetained < 1
                    || !(stepSizeBohr > 0.0)
                    || !Double.isFinite(stepSizeBohr)) {
                throw new IllegalArgumentException("invalid FermiNet RWM configuration");
            }
        }
    }

    public record IterationResult(
            int iteration,
            long seed,
            FermiNetV1State inputState,
            FermiNetV1State updatedState,
            List<QuantumCoordinates> nextWalkers,
            FermiNetVmc.Result vmcResult,
            FermiNetMatrixFreeSrOptimizer.Result srResult,
            EnergyStatistics energyStatistics,
            long vmcNanos,
            long srNanos,
            long totalNanos) {

        public IterationResult {
            if (iteration < 0 || vmcNanos < 0 || srNanos < 0 || totalNanos < 0) {
                throw new IllegalArgumentException("invalid iteration result");
            }
            Objects.requireNonNull(inputState, "inputState");
            Objects.requireNonNull(updatedState, "updatedState");
            nextWalkers = List.copyOf(nextWalkers);
            Objects.requireNonNull(vmcResult, "vmcResult");
            Objects.requireNonNull(srResult, "srResult");
            Objects.requireNonNull(energyStatistics, "energyStatistics");
        }
    }

    public record EnergyStatistics(
            int count,
            double meanHartree,
            double standardDeviationHartree,
            double standardErrorHartree) {

        public EnergyStatistics {
            if (count < 2
                    || !Double.isFinite(meanHartree)
                    || !Double.isFinite(standardDeviationHartree)
                    || !Double.isFinite(standardErrorHartree)
                    || standardDeviationHartree < 0.0
                    || standardErrorHartree < 0.0) {
                throw new IllegalArgumentException("invalid energy statistics");
            }
        }
    }
}
