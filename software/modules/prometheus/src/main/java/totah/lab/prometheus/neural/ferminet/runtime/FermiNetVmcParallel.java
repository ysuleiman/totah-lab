package totah.lab.prometheus.neural.ferminet.runtime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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

    @FunctionalInterface
    interface TransitionObserver {
        void observe(
                int walker,
                QuantumCoordinates proposal,
                double logAcceptanceUniform,
                double currentLogAbsolute,
                double proposalLogAbsolute,
                boolean accepted);
    }

    record ContinuationResult(
            FermiNetVmc.Result result,
            long proposed,
            long accepted) {
    }

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

        return beginSession(state, configuration, initialWalkers)
                .sample(
                        state,
                        configuration.warmupSweeps(),
                        configuration.retainedPerWalker(),
                        configuration.sweepsBetweenRetained())
                .result();
    }

    SamplingSession beginSession(
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

        int walkerCount = configuration.walkers();
        Walker[] walkers = new Walker[walkerCount];

        double[] initialLogs = parallelSamplingLogs(state, initialWalkers);

        for (int i = 0; i < walkerCount; i++) {
            validateFiniteLog(initialLogs[i], "initial walker", i);
            walkers[i] = new Walker(initialWalkers.get(i), initialLogs[i]);
        }

        return new SamplingSession(
                walkers,
                new Random(configuration.seed()),
                configuration.stepSizeBohr(),
                FermiNetStateIdentity.of(state));
    }

    SamplingSession restoreSession(
            FermiNetV1State state,
            FermiNetVmc.Configuration configuration,
            List<QuantumCoordinates> walkers,
            byte[] serializedRandom) {
        Objects.requireNonNull(serializedRandom, "serializedRandom");
        if (walkers.size() != configuration.walkers()) {
            throw new IllegalArgumentException("checkpoint walker count mismatch");
        }
        double[] logs = parallelSamplingLogs(state, walkers);
        Walker[] restored = new Walker[walkers.size()];
        for (int i = 0; i < walkers.size(); i++) {
            validateFiniteLog(logs[i], "checkpoint walker", i);
            restored[i] = new Walker(walkers.get(i), logs[i]);
        }
        return new SamplingSession(restored, deserializeRandom(serializedRandom),
                configuration.stepSizeBohr(), FermiNetStateIdentity.of(state));
    }

    private static Random deserializeRandom(byte[] serialized) {
        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(serialized))) {
            Object value = input.readObject();
            if (!(value instanceof Random random) || input.read() != -1) {
                throw new IllegalArgumentException("invalid checkpoint RNG payload");
            }
            return random;
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalArgumentException("invalid checkpoint RNG state", exception);
        }
    }

    private static byte[] serializeRandom(Random random) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
                output.writeObject(random);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("cannot serialize checkpoint RNG state", exception);
        }
    }

    /**
     * Stateful deterministic sampling continuation.
     *
     * <p>An exact future checkpoint must preserve both the internal 48-bit
     * {@link Random} state and the cached value/state used by
     * {@link Random#nextGaussian()}; recording only the original seed cannot
     * restart an arbitrary point in this stream exactly.
     */
    final class SamplingSession {

        private final Walker[] walkers;
        private final Random random;
        private final double stepSizeBohr;
        private String stateIdentity;

        private SamplingSession(
                Walker[] walkers,
                Random random,
                double stepSizeBohr,
                String stateIdentity) {

            this.walkers = walkers;
            this.random = random;
            this.stepSizeBohr = stepSizeBohr;
            this.stateIdentity = stateIdentity;
        }

        byte[] serializedRandomState() {
            return serializeRandom(random);
        }

        ContinuationResult sample(
                FermiNetV1State state,
                int warmupSweeps,
                int retainedPerWalker,
                int sweepsBetweenRetained) {

            return sample(
                    state,
                    warmupSweeps,
                    retainedPerWalker,
                    sweepsBetweenRetained,
                    (walker, proposal, logUniform, currentLog, proposalLog, accepted) -> {
                    });
        }

        ContinuationResult sample(
                FermiNetV1State state,
                int warmupSweeps,
                int retainedPerWalker,
                int sweepsBetweenRetained,
                TransitionObserver observer) {

            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(observer, "observer");

            if (warmupSweeps < 0
                    || retainedPerWalker < 1
                    || sweepsBetweenRetained < 1) {
                throw new IllegalArgumentException(
                        "invalid FermiNet continuation configuration");
            }

            refreshState(state);

            long proposed = 0L;
            long accepted = 0L;

            List<QuantumCoordinates> retained =
                    new ArrayList<>(
                            Math.multiplyExact(
                                    walkers.length,
                                    retainedPerWalker));

            int measurementSweeps =
                    Math.multiplyExact(
                            retainedPerWalker,
                            sweepsBetweenRetained);

            int totalSweeps =
                    Math.addExact(
                            warmupSweeps,
                            measurementSweeps);

            QuantumCoordinates[] candidates =
                    new QuantumCoordinates[walkers.length];

            double[] logAcceptanceUniform =
                    new double[walkers.length];

            for (int sweep = 1; sweep <= totalSweeps; sweep++) {

                for (int walker = 0; walker < walkers.length; walker++) {
                    candidates[walker] =
                            moveAllElectrons(
                                    walkers[walker].coordinates,
                                    stepSizeBohr,
                                    random);

                    logAcceptanceUniform[walker] =
                            Math.log(random.nextDouble());
                }

            double[] nextLogs =
                    parallelSamplingLogs(
                            state,
                            List.of(candidates));

            for (int walker = 0; walker < walkers.length; walker++) {
                double nextLog = nextLogs[walker];

                validateFiniteLog(nextLog, "candidate", walker);

                proposed++;

                double logAcceptance =
                        Math.min(
                                0.0,
                                2.0
                                        * (nextLog
                                        - walkers[walker].logAbsolute));

                double currentLog = walkers[walker].logAbsolute;

                if (logAcceptanceUniform[walker] < logAcceptance) {
                    walkers[walker].coordinates = candidates[walker];
                    walkers[walker].logAbsolute = nextLog;
                    accepted++;
                    observer.observe(
                            walker,
                            candidates[walker],
                            logAcceptanceUniform[walker],
                            currentLog,
                            nextLog,
                            true);
                } else {
                    observer.observe(
                            walker,
                            candidates[walker],
                            logAcceptanceUniform[walker],
                            currentLog,
                            nextLog,
                            false);
                }
            }

            int measuredSweep =
                    sweep
                            - warmupSweeps;

            if (measuredSweep > 0
                    && measuredSweep
                    % sweepsBetweenRetained
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

            return new ContinuationResult(
                    new FermiNetVmc.Result(
                            retained,
                            acceptance,
                            List.of(energies),
                            stateIdentity),
                    proposed,
                    accepted);
        }

        List<QuantumCoordinates> currentWalkers() {
            return java.util.Arrays.stream(walkers)
                    .map(walker -> walker.coordinates)
                    .toList();
        }

        double[] currentWalkerLogs() {
            return java.util.Arrays.stream(walkers)
                    .mapToDouble(walker -> walker.logAbsolute)
                    .toArray();
        }

        void refreshState(FermiNetV1State state) {
            Objects.requireNonNull(state, "state");
            String nextStateIdentity = FermiNetStateIdentity.of(state);
            if (nextStateIdentity.equals(stateIdentity)) {
                return;
            }

            double[] refreshedLogs = parallelSamplingLogs(
                    state,
                    currentWalkers());
            for (int walker = 0; walker < walkers.length; walker++) {
                validateFiniteLog(refreshedLogs[walker], "continued walker", walker);
                walkers[walker].logAbsolute = refreshedLogs[walker];
            }
            stateIdentity = nextStateIdentity;
        }
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
