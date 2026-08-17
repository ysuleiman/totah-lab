package totah.lab.prometheus.neural;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import totah.lab.prometheus.molecular.LocalEnergyComponents;
import totah.lab.prometheus.variational.QuantumCoordinates;

/**
 * Experimental deterministic-parity parallel VMC sampler.
 *
 * <p>This implementation preserves the reference java.util.Random draw order:
 * for each sweep and walker it generates the all-electron Gaussian proposal
 * followed immediately by that walker's acceptance uniform. Only the expensive
 * neural evaluation of the already-generated proposals is parallelized.
 *
 * <p>For strict parity, proposals must have finite sampling log amplitudes.
 * If a proposal is singular/invalid this experimental path fails closed rather
 * than consuming a different RNG sequence than the canonical sampler.
 *
 * <p>Retained local energies are also evaluated in parallel, while result
 * ordering remains the canonical retained-sample order.
 */
final class FermiNetVmcParallel implements AutoCloseable {

    private final ForkJoinPool pool;

    FermiNetVmcParallel(int parallelism) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be positive");
        }
        this.pool = new ForkJoinPool(parallelism);
    }

    FermiNetVmc.Result sample(
            FermiNetV1State state,
            FermiNetVmc.Configuration configuration,
            List<QuantumCoordinates> initialWalkers) {

        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(initialWalkers, "initialWalkers");

        if (initialWalkers.isEmpty()) {
            throw new IllegalArgumentException(
                    "deterministic parallel VMC requires explicit initial walkers");
        }

        if (initialWalkers.size() != configuration.walkers()) {
            throw new IllegalArgumentException("initial walker count mismatch");
        }

        Random random = new Random(configuration.seed());

        int walkerCount = configuration.walkers();
        Walker[] walkers = new Walker[walkerCount];

        double[] initialLogs = parallelSamplingLogs(state, initialWalkers);

        for (int i = 0; i < walkerCount; i++) {
            validateFiniteLog(initialLogs[i], "initial walker", i);
            walkers[i] = new Walker(initialWalkers.get(i), initialLogs[i]);
        }

        long proposed = 0L;
        long accepted = 0L;

        List<QuantumCoordinates> retained =
                new ArrayList<>(
                        Math.multiplyExact(
                                configuration.walkers(),
                                configuration.retainedPerWalker()));

        int measurementSweeps =
                Math.multiplyExact(
                        configuration.retainedPerWalker(),
                        configuration.sweepsBetweenRetained());

        int totalSweeps =
                Math.addExact(
                        configuration.warmupSweeps(),
                        measurementSweeps);

        QuantumCoordinates[] candidates =
                new QuantumCoordinates[walkerCount];

        double[] logAcceptanceUniform =
                new double[walkerCount];

        for (int sweep = 1; sweep <= totalSweeps; sweep++) {

            for (int walker = 0; walker < walkerCount; walker++) {
                candidates[walker] =
                        moveAllElectrons(
                                walkers[walker].coordinates,
                                configuration.stepSizeBohr(),
                                random);

                logAcceptanceUniform[walker] =
                        Math.log(random.nextDouble());
            }

            double[] nextLogs =
                    parallelSamplingLogs(
                            state,
                            List.of(candidates));

            for (int walker = 0; walker < walkerCount; walker++) {
                double nextLog = nextLogs[walker];

                validateFiniteLog(nextLog, "candidate", walker);

                proposed++;

                double logAcceptance =
                        Math.min(
                                0.0,
                                2.0
                                        * (nextLog
                                        - walkers[walker].logAbsolute));

                if (logAcceptanceUniform[walker] < logAcceptance) {
                    walkers[walker].coordinates = candidates[walker];
                    walkers[walker].logAbsolute = nextLog;
                    accepted++;
                }
            }

            int measuredSweep =
                    sweep
                            - configuration.warmupSweeps();

            if (measuredSweep > 0
                    && measuredSweep
                    % configuration.sweepsBetweenRetained()
                    == 0) {

                for (Walker walker : walkers) {
                    retained.add(walker.coordinates);
                }
            }
        }

        if (proposed == 0L) {
            throw new IllegalStateException("VMC made no proposals");
        }

        double acceptance =
                (double) accepted
                        / proposed;

        LocalEnergyComponents[] energies =
                parallelLocalEnergies(
                        state,
                        retained);

        return new FermiNetVmc.Result(
                retained,
                acceptance,
                List.of(energies),
                FermiNetStateIdentity.of(state));
    }

    private double[] parallelSamplingLogs(
            FermiNetV1State state,
            List<QuantumCoordinates> coordinates) {

        double[] result = new double[coordinates.size()];
        AtomicReference<RuntimeException> failure = new AtomicReference<>();

        pool.submit(
                        () ->
                                IntStream.range(0, coordinates.size())
                                        .parallel()
                                        .forEach(
                                                index -> {
                                                    if (failure.get() != null) {
                                                        return;
                                                    }

                                                    try {
                                                        result[index] =
                                                                state.samplingEvaluation(
                                                                                coordinates.get(index))
                                                                        .logAbsoluteWavefunction();
                                                    } catch (RuntimeException exception) {
                                                        failure.compareAndSet(
                                                                null,
                                                                exception);
                                                    }
                                                }))
                .join();

        RuntimeException exception = failure.get();
        if (exception != null) {
            throw exception;
        }

        return result;
    }

    private LocalEnergyComponents[] parallelLocalEnergies(
            FermiNetV1State state,
            List<QuantumCoordinates> retained) {

        LocalEnergyComponents[] result =
                new LocalEnergyComponents[retained.size()];

        AtomicReference<RuntimeException> failure =
                new AtomicReference<>();

        pool.submit(
                        () ->
                                IntStream.range(0, retained.size())
                                        .parallel()
                                        .forEach(
                                                index -> {
                                                    if (failure.get() != null) {
                                                        return;
                                                    }

                                                    try {
                                                        result[index] =
                                                                FermiNetVmc.localEnergy(
                                                                        state,
                                                                        retained.get(index));
                                                    } catch (RuntimeException exception) {
                                                        failure.compareAndSet(
                                                                null,
                                                                exception);
                                                    }
                                                }))
                .join();

        RuntimeException exception = failure.get();
        if (exception != null) {
            throw exception;
        }

        return result;
    }

    private static QuantumCoordinates moveAllElectrons(
            QuantumCoordinates coordinates,
            double step,
            Random random) {

        List<QuantumCoordinates.ParticleCoordinate> particles =
                new ArrayList<>(
                        coordinates.particles().size());

        for (var electron : coordinates.particles()) {
            particles.add(
                    new QuantumCoordinates.ParticleCoordinate(
                            electron.particleIndex(),
                            electron.xBohr()
                                    + step
                                    * random.nextGaussian(),
                            electron.yBohr()
                                    + step
                                    * random.nextGaussian(),
                            electron.zBohr()
                                    + step
                                    * random.nextGaussian(),
                            electron.spin()));
        }

        return new QuantumCoordinates(particles);
    }

    private static void validateFiniteLog(
            double value,
            String stage,
            int walker) {

        if (!Double.isFinite(value)) {
            throw new IllegalStateException(
                    "parallel deterministic VMC encountered non-finite "
                            + stage
                            + " log|Psi| at walker "
                            + walker
                            + "; refusing to change canonical RNG semantics");
        }
    }

    @Override
    public void close() {
        pool.shutdown();
    }

    private static final class Walker {
        private QuantumCoordinates coordinates;
        private double logAbsolute;

        private Walker(
                QuantumCoordinates coordinates,
                double logAbsolute) {

            this.coordinates = coordinates;
            this.logAbsolute = logAbsolute;
        }
    }
}
