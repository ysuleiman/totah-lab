package totah.lab.prometheus.neural;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.molecular.LocalEnergyComponents;
import totah.lab.prometheus.variational.QuantumCoordinates;

/** Iterative orchestration of deterministic parallel VMC and production SR. */
public final class FermiNetVariationalOptimizer implements AutoCloseable {

    private final FermiNetVmcParallel vmc;
    private final FermiNetKfacOptimizer kfac;

    public FermiNetVariationalOptimizer(int vmcParallelism) {
        if (vmcParallelism < 1) {
            throw new IllegalArgumentException("invalid VMC parallelism");
        }
        this.vmc = new FermiNetVmcParallel(vmcParallelism);
        this.kfac = new FermiNetKfacOptimizer(vmcParallelism);
    }

    /** Runs one VMC/update iteration through the configured optimizer engine. */
    public OptimizationIterationResult oneIteration(
            int iteration,
            FermiNetV1State state,
            List<QuantumCoordinates> walkers,
            SamplingConfiguration sampling,
            OptimizationConfiguration configuration) {

        if (iteration < 0) {
            throw new IllegalArgumentException("iteration must be nonnegative");
        }
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(walkers, "walkers");
        Objects.requireNonNull(sampling, "sampling");
        Objects.requireNonNull(configuration, "configuration");
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

        return finishSelected(new IterationContext(
                iteration, state, vmcResult, started, vmcNanos,
                System.nanoTime()), seed, sampling, configuration).result();
    }

    /** Runs the shared persistent-VMC loop with the configured update engine. */
    public List<OptimizationIterationResult> optimize(
            FermiNetV1State initialState,
            List<QuantumCoordinates> initialWalkers,
            SamplingConfiguration sampling,
            OptimizationConfiguration configuration,
            int iterations) {
        Objects.requireNonNull(configuration, "configuration");
        return optimizeLoop(
                initialState, initialWalkers, sampling, iterations,
                context -> finishSelected(
                        context, sampling.baseSeed(), sampling, configuration));
    }

    private Step<OptimizationIterationResult> finishSelected(
            IterationContext context,
            long seed,
            SamplingConfiguration sampling,
        OptimizationConfiguration configuration) {
        if (configuration.optimizerType() == FermiNetOptimizerType.EXACT_SR) {
            OptimizationIterationResult exact = finishExactIteration(
                    context.iteration(), seed, context.state(), context.vmcResult(),
                    sampling, configuration.exactSrConfiguration(),
                    context.started(), context.vmcNanos(), context.srStarted());
            return new Step<>(exact.updatedState(), exact);
        }

        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples =
                context.vmcResult().samples().stream()
                        .map(coordinates -> new FermiNetMatrixFreeSrOptimizer
                                .WeightedSample(1.0, coordinates))
                        .toList();
        FermiNetKnownLocalEnergies energies = FermiNetKnownLocalEnergies.from(
                context.state(), context.vmcResult());
        FermiNetKfacOptimizer.Result update = kfac.oneIteration(
                context.state(), samples, energies,
                configuration.kfacConfiguration());
        long updateNanos = System.nanoTime() - context.srStarted();
        int nextStart = context.vmcResult().samples().size() - sampling.walkers();
        OptimizationIterationResult result = new OptimizationIterationResult(
                context.iteration(), seed, FermiNetOptimizerType.KFAC,
                context.state(), update.state(),
                List.copyOf(context.vmcResult().samples().subList(
                        nextStart, context.vmcResult().samples().size())),
                context.vmcResult(), energyStatistics(context.vmcResult().localEnergies()),
                null, update, context.vmcNanos(), updateNanos,
                System.nanoTime() - context.started());
        return new Step<>(update.state(), result);
    }

    private <T> List<T> optimizeLoop(
            FermiNetV1State initialState,
            List<QuantumCoordinates> initialWalkers,
            SamplingConfiguration sampling,
            int iterations,
            IterationFinisher<T> finisher) {
        Objects.requireNonNull(initialState, "initialState");
        Objects.requireNonNull(initialWalkers, "initialWalkers");
        Objects.requireNonNull(sampling, "sampling");
        if (iterations < 1) {
            throw new IllegalArgumentException("iterations must be positive");
        }
        if (initialWalkers.size() != sampling.walkers()) {
            throw new IllegalArgumentException("initial walker count mismatch");
        }

        FermiNetV1State state = initialState;
        List<T> results = new ArrayList<>(iterations);
        FermiNetVmc.Configuration initialConfiguration =
                new FermiNetVmc.Configuration(
                        sampling.walkers(), sampling.warmupSweeps(),
                        sampling.retainedPerWalker(),
                        sampling.sweepsBetweenRetained(),
                        sampling.stepSizeBohr(), sampling.baseSeed());
        FermiNetVmcParallel.SamplingSession session =
                vmc.beginSession(state, initialConfiguration, initialWalkers);
        for (int iteration = 0; iteration < iterations; iteration++) {
            long started = System.nanoTime();
            long vmcStarted = System.nanoTime();
            FermiNetVmc.Result vmcResult = session.sample(
                    state,
                    iteration == 0 ? sampling.warmupSweeps() : 0,
                    sampling.retainedPerWalker(),
                    sampling.sweepsBetweenRetained()).result();
            long vmcFinished = System.nanoTime();
            Step<T> step = finisher.finish(new IterationContext(
                    iteration, state, vmcResult, started,
                    vmcFinished - vmcStarted, vmcFinished));
            results.add(step.result());
            state = step.state();
        }
        return List.copyOf(results);
    }

    private static OptimizationIterationResult finishExactIteration(
            int iteration,
            long seed,
            FermiNetV1State state,
            FermiNetVmc.Result vmcResult,
            SamplingConfiguration sampling,
            FermiNetMatrixFreeSrOptimizer.Configuration srConfiguration,
            long started,
            long vmcNanos,
            long srStarted) {

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
        FermiNetKnownLocalEnergies knownLocalEnergies =
                FermiNetKnownLocalEnergies.from(state, vmcResult);
        FermiNetMatrixFreeSrOptimizer.Result srResult =
                new FermiNetMatrixFreeSrOptimizer().oneIteration(
                        state,
                        srSamples,
                        knownLocalEnergies,
                        srConfiguration);
        long srNanos = System.nanoTime() - srStarted;

        int nextWalkerStart = vmcResult.samples().size() - sampling.walkers();
        List<QuantumCoordinates> nextWalkers = List.copyOf(
                vmcResult.samples().subList(
                        nextWalkerStart,
                        vmcResult.samples().size()));

        return new OptimizationIterationResult(
                iteration,
                seed,
                FermiNetOptimizerType.EXACT_SR,
                state,
                srResult.state(),
                nextWalkers,
                vmcResult,
                energyStatistics(vmcResult.localEnergies()),
                srResult,
                null,
                vmcNanos,
                srNanos,
                System.nanoTime() - started);
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

    /** Selects the update engine while keeping VMC and iteration orchestration common. */
    public record OptimizationConfiguration(
            FermiNetOptimizerType optimizerType,
            FermiNetMatrixFreeSrOptimizer.Configuration exactSrConfiguration,
            FermiNetKfacOptimizer.Configuration kfacConfiguration) {

        public OptimizationConfiguration {
            Objects.requireNonNull(optimizerType, "optimizerType");
            if (optimizerType == FermiNetOptimizerType.EXACT_SR) {
                Objects.requireNonNull(exactSrConfiguration, "exactSrConfiguration");
                if (kfacConfiguration != null) {
                    throw new IllegalArgumentException(
                            "KFAC configuration supplied for exact SR");
                }
            } else {
                Objects.requireNonNull(kfacConfiguration, "kfacConfiguration");
                if (exactSrConfiguration != null) {
                    throw new IllegalArgumentException(
                            "exact-SR configuration supplied for KFAC");
                }
            }
        }

        public static OptimizationConfiguration exactSr(
                FermiNetMatrixFreeSrOptimizer.Configuration configuration) {
            return new OptimizationConfiguration(
                    FermiNetOptimizerType.EXACT_SR,
                    Objects.requireNonNull(configuration, "configuration"),
                    null);
        }

        public static OptimizationConfiguration kfac(
                FermiNetKfacOptimizer.Configuration configuration) {
            return new OptimizationConfiguration(
                    FermiNetOptimizerType.KFAC,
                    null,
                    Objects.requireNonNull(configuration, "configuration"));
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

    public record OptimizationIterationResult(
            int iteration,
            long seed,
            FermiNetOptimizerType optimizerType,
            FermiNetV1State inputState,
            FermiNetV1State updatedState,
            List<QuantumCoordinates> nextWalkers,
            FermiNetVmc.Result vmcResult,
            EnergyStatistics energyStatistics,
            FermiNetMatrixFreeSrOptimizer.Result exactSrResult,
            FermiNetKfacOptimizer.Result kfacResult,
            long vmcNanos,
            long updateNanos,
        long totalNanos) {
        public OptimizationIterationResult {
            if (iteration < 0 || vmcNanos < 0 || updateNanos < 0 || totalNanos < 0) {
                throw new IllegalArgumentException("invalid iteration result");
            }
            Objects.requireNonNull(optimizerType, "optimizerType");
            Objects.requireNonNull(inputState, "inputState");
            Objects.requireNonNull(updatedState, "updatedState");
            nextWalkers = List.copyOf(nextWalkers);
            Objects.requireNonNull(vmcResult, "vmcResult");
            Objects.requireNonNull(energyStatistics, "energyStatistics");
            if ((optimizerType == FermiNetOptimizerType.EXACT_SR)
                    != (exactSrResult != null)
                    || (optimizerType == FermiNetOptimizerType.KFAC)
                    != (kfacResult != null)) {
                throw new IllegalArgumentException("optimizer result/type mismatch");
            }
        }

        int reusedLocalEnergyCount() {
            return vmcResult.samples().size();
        }
    }

    @FunctionalInterface
    private interface IterationFinisher<T> {
        Step<T> finish(IterationContext context);
    }

    private record IterationContext(
            int iteration,
            FermiNetV1State state,
            FermiNetVmc.Result vmcResult,
            long started,
            long vmcNanos,
            long srStarted) {}

    private record Step<T>(FermiNetV1State state, T result) {}
}
