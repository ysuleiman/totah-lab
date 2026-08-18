package totah.lab.prometheus.neural.ferminet.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.LocalEnergyComponents;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/**
 * Canonical direct |Psi|^2 VMC sampler for derivative-complete FermiNet states.
 *
 * <p>The locked reference sampling mode is:
 *
 * <pre>
 * proposal              = all-electron Gaussian move
 * target                = |Psi|^2
 * proposal width        = fixed stepSizeBohr
 * acceptance            = min(1, exp(2 * delta log|Psi|))
 * sampling evaluation   = value-only FermiNet path
 * local energy          = evaluated only on retained configurations
 * </pre>
 *
 * <p>This class intentionally centralizes both sampling and local-energy
 * evaluation so qualification, SR and later workflows do not carry divergent
 * implementations of the same scientific calculation.
 */
final class FermiNetVmc {

    record Configuration(
            int walkers,
            int warmupSweeps,
            int retainedPerWalker,
            int sweepsBetweenRetained,
            double stepSizeBohr,
            long seed) {

        Configuration {

            if (walkers < 1
                    || warmupSweeps < 0
                    || retainedPerWalker < 1
                    || sweepsBetweenRetained < 1
                    || !(stepSizeBohr > 0.0)
                    || !Double.isFinite(stepSizeBohr)) {

                throw new IllegalArgumentException(
                        "invalid FermiNet RWM configuration");
            }
        }
    }

    record Result(
            List<QuantumCoordinates> samples,
            double acceptance,
            List<LocalEnergyComponents> localEnergies,
            String stateIdentity) {

        Result {

            Objects.requireNonNull(
                    samples,
                    "samples");

            Objects.requireNonNull(
                    localEnergies,
                    "localEnergies");

            Objects.requireNonNull(
                    stateIdentity,
                    "stateIdentity");

            samples =
                    List.copyOf(samples);

            localEnergies =
                    List.copyOf(localEnergies);

            if (!Double.isFinite(acceptance)
                    || acceptance < 0.0
                    || acceptance > 1.0) {

                throw new IllegalArgumentException(
                        "invalid VMC acceptance");
            }

            if (samples.size()
                    != localEnergies.size()) {

                throw new IllegalArgumentException(
                        "sample/local-energy count mismatch");
            }

            if (!stateIdentity.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("invalid FermiNet state identity");
            }
        }
    }

    Result sample(
            FermiNetV1State state,
            Configuration configuration) {

        return sample(
                state,
                configuration,
                List.of());
    }

    Result sample(
            FermiNetV1State state,
            Configuration configuration,
            List<QuantumCoordinates> initialWalkers) {

        Objects.requireNonNull(
                state,
                "state");

        Objects.requireNonNull(
                configuration,
                "configuration");

        Objects.requireNonNull(
                initialWalkers,
                "initialWalkers");

        if (!initialWalkers.isEmpty()
                && initialWalkers.size()
                != configuration.walkers()) {

            throw new IllegalArgumentException(
                    "initial walker count mismatch");
        }

        Random random =
                new Random(
                        configuration.seed());

        List<Walker> walkers =
                initializeWalkers(
                        state,
                        configuration,
                        initialWalkers,
                        random);

        long proposed =
                0L;

        long accepted =
                0L;

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

        /*
         * ------------------------------------------------------------
         * Reference-mode RWM.
         *
         * One proposal moves all electron coordinates simultaneously.
         * ------------------------------------------------------------
         */
        for (int sweep = 1;
             sweep <= totalSweeps;
             sweep++) {

            for (Walker walker :
                    walkers) {

                QuantumCoordinates candidate =
                        moveAllElectrons(
                                walker.coordinates,
                                configuration.stepSizeBohr(),
                                random);

                double nextLog =
                        safeSamplingLog(
                                state,
                                candidate);

                proposed++;

                if (acceptProposal(
                        walker.logAbsolute,
                        nextLog,
                        random)) {

                    walker.coordinates =
                            candidate;

                    walker.logAbsolute =
                            nextLog;

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

                for (Walker walker :
                        walkers) {

                    retained.add(
                            walker.coordinates);
                }
            }
        }

        if (proposed == 0L) {

            throw new IllegalStateException(
                    "VMC made no proposals");
        }

        double acceptance =
                (double) accepted
                        / proposed;

        List<LocalEnergyComponents> energies =
                retained.stream()
                        .map(
                                coordinates ->
                                        localEnergy(
                                                state,
                                                coordinates))
                        .toList();

        return new Result(
                retained,
                acceptance,
                energies,
                FermiNetStateIdentity.of(state));
    }

    /**
     * Canonical local-energy evaluation in atomic units.
     *
     * <p>
     *
     * E_L =
     * -1/2 (nabla^2 Psi / Psi)
     * - sum_iA Z_A/r_iA
     * + sum_i<j 1/r_ij
     * + sum_A<B Z_A Z_B/R_AB
     */
    static LocalEnergyComponents localEnergy(
            FermiNetV1State state,
            QuantumCoordinates electrons) {

        Objects.requireNonNull(
                state,
                "state");

        Objects.requireNonNull(
                electrons,
                "electrons");

        validateElectronConfiguration(
                state.molecule(),
                electrons);

        var spatial =
                state.spatialEvaluation(
                        electrons);

        return localEnergyFromLaplacian(
                state,
                electrons,
                spatial.laplacianOverWavefunction());
    }

    /**
     * Local-energy evaluation reusing a derivative-complete FermiNet evaluation.
     *
     * <p>The supplied evaluation must have been produced by {@code state.evaluate(electrons)}
     * for the same state and electron coordinates. This overload exists so SR can reuse the
     * spatial Laplacian already computed while obtaining parameter derivatives, instead of
     * performing a second FermiNet spatial traversal.
     */
    static LocalEnergyComponents localEnergy(
            FermiNetV1State state,
            QuantumCoordinates electrons,
            FermiNetV1State.Evaluation evaluation) {

        Objects.requireNonNull(
                state,
                "state");

        Objects.requireNonNull(
                electrons,
                "electrons");

        Objects.requireNonNull(
                evaluation,
                "evaluation");

        validateElectronConfiguration(
                state.molecule(),
                electrons);

        return localEnergyFromLaplacian(
                state,
                electrons,
                evaluation.laplacianOverWavefunction());
    }

    private static LocalEnergyComponents localEnergyFromLaplacian(
            FermiNetV1State state,
            QuantumCoordinates electrons,
            double laplacianOverWavefunction) {

        double kinetic =
                -0.5
                        * laplacianOverWavefunction;

        Molecule molecule =
                state.molecule();

        double electronNuclear =
                0.0;

        double electronElectron =
                0.0;

        double nuclearNuclear =
                0.0;

        /*
         * Electron-nuclear Coulomb attraction.
         */
        for (var electron :
                electrons.particles()) {

            for (var nucleus :
                    molecule.nuclei()) {

                CartesianPosition position =
                        nucleus.position()
                                .inBohr();

                double r =
                        distance(
                                electron.xBohr(),
                                electron.yBohr(),
                                electron.zBohr(),
                                position.x(),
                                position.y(),
                                position.z());

                electronNuclear -=
                        nucleus.charge()
                                .atomicNumber()
                                / r;
            }
        }

        /*
         * Electron-electron Coulomb repulsion.
         */
        for (int i = 0;
             i < electrons.particles().size();
             i++) {

            var left =
                    electrons.particles()
                            .get(i);

            for (int j = i + 1;
                 j < electrons.particles().size();
                 j++) {

                var right =
                        electrons.particles()
                                .get(j);

                double r =
                        distance(
                                left.xBohr(),
                                left.yBohr(),
                                left.zBohr(),
                                right.xBohr(),
                                right.yBohr(),
                                right.zBohr());

                electronElectron +=
                        1.0
                                / r;
            }
        }

        /*
         * Nuclear-nuclear Coulomb repulsion.
         */
        for (int i = 0;
             i < molecule.nuclei().size();
             i++) {

            var left =
                    molecule.nuclei()
                            .get(i);

            CartesianPosition leftPosition =
                    left.position()
                            .inBohr();

            for (int j = i + 1;
                 j < molecule.nuclei().size();
                 j++) {

                var right =
                        molecule.nuclei()
                                .get(j);

                CartesianPosition rightPosition =
                        right.position()
                                .inBohr();

                double r =
                        distance(
                                leftPosition.x(),
                                leftPosition.y(),
                                leftPosition.z(),
                                rightPosition.x(),
                                rightPosition.y(),
                                rightPosition.z());

                nuclearNuclear +=
                        left.charge()
                                .atomicNumber()
                                * right.charge()
                                .atomicNumber()
                                / r;
            }
        }

        if (!Double.isFinite(kinetic)
                || !Double.isFinite(electronNuclear)
                || !Double.isFinite(electronElectron)
                || !Double.isFinite(nuclearNuclear)) {

            throw new IllegalStateException(
                    "non-finite local-energy component");
        }

        return new LocalEnergyComponents(
                kinetic,
                electronNuclear,
                electronElectron,
                nuclearNuclear);
    }

    private static List<Walker> initializeWalkers(
            FermiNetV1State state,
            Configuration configuration,
            List<QuantumCoordinates> initialWalkers,
            Random random) {

        List<Walker> walkers =
                new ArrayList<>(
                        configuration.walkers());

        for (int index = 0;
             index < configuration.walkers();
             index++) {

            QuantumCoordinates coordinates =
                    initialWalkers.isEmpty()
                            ? referenceStyleInitialCoordinates(
                            state.molecule(),
                            random)
                            : initialWalkers.get(index);

            validateElectronConfiguration(
                    state.molecule(),
                    coordinates);

            double logAbsolute =
                    safeSamplingLog(
                            state,
                            coordinates);

            if (!Double.isFinite(logAbsolute)) {

                throw new IllegalStateException(
                        "initial walker has zero/invalid neural probability");
            }

            walkers.add(
                    new Walker(
                            coordinates,
                            logAbsolute));
        }

        return walkers;
    }

    /**
     * Minimal reference-style neutral-molecule fallback initialization.
     *
     * <p>Production H2O qualification and SR should normally pass the frozen
     * pretrained walkers explicitly. This method exists only for direct VMC
     * use when no walkers are supplied.
     */
    private static QuantumCoordinates referenceStyleInitialCoordinates(
            Molecule molecule,
            Random random) {

        int electrons =
                molecule.electrons()
                        .value();

        int alpha =
                molecule.spin()
                        .alphaElectrons();

        /*
         * Use nearest available nuclear centers in round-robin order, but keep
         * the spin contract explicit. This is only a fallback; the scientific
         * production path uses supplied pretrained walkers.
         */
        List<QuantumCoordinates.ParticleCoordinate> particles =
                new ArrayList<>(
                        electrons);

        double scale =
                1.0;

        for (int electron = 0;
             electron < electrons;
             electron++) {

            CartesianPosition center =
                    molecule.nuclei()
                            .get(
                                    electron
                                            % molecule.nuclei()
                                            .size())
                            .position()
                            .inBohr();

            SpinProjection spin =
                    electron < alpha
                            ? SpinProjection.ALPHA
                            : SpinProjection.BETA;

            particles.add(
                    new QuantumCoordinates.ParticleCoordinate(
                            electron,
                            center.x()
                                    + scale
                                    * random.nextGaussian(),
                            center.y()
                                    + scale
                                    * random.nextGaussian(),
                            center.z()
                                    + scale
                                    * random.nextGaussian(),
                            spin));
        }

        return new QuantumCoordinates(
                particles);
    }

    /**
     * Value-only neural log amplitude for MH sampling.
     *
     * <p>Singular/invalid proposals are represented as zero probability.
     */
    private static double safeSamplingLog(
            FermiNetV1State state,
            QuantumCoordinates coordinates) {

        try {

            double value =
                    state.samplingEvaluation(
                                    coordinates)
                            .logAbsoluteWavefunction();

            return Double.isFinite(value)
                    ? value
                    : Double.NEGATIVE_INFINITY;

        } catch (IllegalStateException
                 | IllegalArgumentException exception) {

            return Double.NEGATIVE_INFINITY;
        }
    }

    private static boolean acceptProposal(
            double currentLog,
            double nextLog,
            Random random) {

        if (currentLog == Double.NEGATIVE_INFINITY
                && nextLog == Double.NEGATIVE_INFINITY) {

            return false;
        }

        if (currentLog == Double.NEGATIVE_INFINITY
                && Double.isFinite(nextLog)) {

            return true;
        }

        if (!Double.isFinite(nextLog)) {

            return false;
        }

        if (!Double.isFinite(currentLog)) {

            throw new IllegalStateException(
                    "invalid current walker probability");
        }

        double logAcceptance =
                Math.min(
                        0.0,
                        2.0
                                * (nextLog
                                - currentLog));

        return Math.log(
                random.nextDouble())
                < logAcceptance;
    }

    /**
     * Symmetric all-electron Gaussian proposal:
     *
     * r'_i = r_i + step * Normal(0,1)
     *
     * for every electron coordinate simultaneously.
     */
    private static QuantumCoordinates moveAllElectrons(
            QuantumCoordinates coordinates,
            double step,
            Random random) {

        List<QuantumCoordinates.ParticleCoordinate> particles =
                new ArrayList<>(
                        coordinates.particles().size());

        for (var electron :
                coordinates.particles()) {

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

        return new QuantumCoordinates(
                particles);
    }

    private static void validateElectronConfiguration(
            Molecule molecule,
            QuantumCoordinates coordinates) {

        if (coordinates.particles().size()
                != molecule.electrons().value()) {

            throw new IllegalArgumentException(
                    "electron count mismatch");
        }

        for (int i = 0;
             i < coordinates.particles().size();
             i++) {

            var particle =
                    coordinates.particles()
                            .get(i);

            if (particle.particleIndex()
                    != i) {

                throw new IllegalArgumentException(
                        "electron indices must be canonical");
            }

            SpinProjection expected =
                    i
                            < molecule.spin()
                            .alphaElectrons()
                            ? SpinProjection.ALPHA
                            : SpinProjection.BETA;

            if (particle.spin()
                    != expected) {

                throw new IllegalArgumentException(
                        "electrons must be ordered alpha then beta");
            }

            if (!Double.isFinite(
                    particle.xBohr())
                    || !Double.isFinite(
                    particle.yBohr())
                    || !Double.isFinite(
                    particle.zBohr())) {

                throw new IllegalArgumentException(
                        "non-finite electron coordinate");
            }
        }
    }

    private static double distance(
            double ax,
            double ay,
            double az,
            double bx,
            double by,
            double bz) {

        double dx =
                ax - bx;

        double dy =
                ay - by;

        double dz =
                az - bz;

        double distance =
                Math.sqrt(
                        dx * dx
                                + dy * dy
                                + dz * dz);

        if (!(distance > 1.0e-12)
                || !Double.isFinite(distance)) {

            throw new IllegalArgumentException(
                    "Coulomb singularity");
        }

        return distance;
    }

    private static final class Walker {

        private QuantumCoordinates coordinates;
        private double logAbsolute;

        private Walker(
                QuantumCoordinates coordinates,
                double logAbsolute) {

            this.coordinates =
                    coordinates;

            this.logAbsolute =
                    logAbsolute;
        }
    }
}
