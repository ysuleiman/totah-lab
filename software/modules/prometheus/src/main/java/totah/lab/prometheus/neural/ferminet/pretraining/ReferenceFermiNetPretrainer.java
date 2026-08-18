package totah.lab.prometheus.neural.ferminet.pretraining;

import totah.lab.prometheus.neural.ferminet.runtime.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/**
 * Java implementation of the occupied-orbital Hartree-Fock pretraining
 * workflow used by the locked DeepMind FermiNet reference.
 */
public final class ReferenceFermiNetPretrainer {

    public static final String REFERENCE_COMMIT =
            "c4312c315dda1c5728994ba89629744f71c6eb66";

    private static final double ADAM_BETA1 = 0.9;
    private static final double ADAM_BETA2 = 0.999;
    private static final double ADAM_EPSILON = 1.0e-8;

    /*
     * train.init_electrons() reference default.
     */
    private static final int INITIAL_SPIN_ASSIGNMENT_MAX_ITERATIONS =
            10_000;

    public record Configuration(
            int iterations,
            int walkers,
            double learningRate,
            double moveWidthBohr,
            double initialWidthBohr,
            double scfFraction,
            long seed) {

        public Configuration {

            if (iterations < 0
                    || walkers < 1
                    || !(learningRate > 0.0)
                    || !(moveWidthBohr > 0.0)
                    || !(initialWidthBohr > 0.0)
                    || !Double.isFinite(learningRate)
                    || !Double.isFinite(moveWidthBohr)
                    || !Double.isFinite(initialWidthBohr)
                    || !Double.isFinite(scfFraction)
                    || scfFraction < 0.0
                    || scfFraction > 1.0) {

                throw new IllegalArgumentException(
                        "invalid FermiNet pretraining configuration");
            }
        }

        public static Configuration referenceDefaults(
                int walkers,
                long seed) {

            return new Configuration(
                    1000,
                    walkers,
                    3.0e-4,
                    0.02,
                    1.0,
                    1.0,
                    seed);
        }
    }

    public record Result(
            FermiNetV1State state,
            List<QuantumCoordinates> walkers,
            List<Double> lossHistory,
            double acceptance,
            String algorithm,
            String referenceCommit) {

        public Result {

            Objects.requireNonNull(state);
            Objects.requireNonNull(walkers);
            Objects.requireNonNull(lossHistory);
            Objects.requireNonNull(algorithm);
            Objects.requireNonNull(referenceCommit);

            walkers =
                    List.copyOf(walkers);

            lossHistory =
                    List.copyOf(lossHistory);
        }
    }

    public Result train(
            FermiNetV1State initial,
            HartreeFockOrbitalTarget target,
            Configuration configuration) {

        return train(
                initial,
                target,
                configuration,
                (iteration, loss) -> {
                });
    }

    public Result train(
            FermiNetV1State initial,
            HartreeFockOrbitalTarget target,
            Configuration configuration,
            BiConsumer<Integer, Double> progress) {

        Objects.requireNonNull(
                initial,
                "initial");

        Objects.requireNonNull(
                target,
                "target");

        Objects.requireNonNull(
                configuration,
                "configuration");

        Objects.requireNonNull(
                progress,
                "progress");

        Random random =
                new Random(
                        configuration.seed());

        List<QuantumCoordinates> walkers =
                initialize(
                        initial,
                        configuration,
                        random);

        FermiNetV1State state =
                initial;

        double[] firstMoment =
                new double[
                        state.parameterCount()];

        double[] secondMoment =
                new double[
                        state.parameterCount()];

        List<Double> history =
                new ArrayList<>(
                        configuration.iterations());

        long proposed =
                0L;

        long accepted =
                0L;

        for (int iteration = 1;
             iteration <= configuration.iterations();
             iteration++) {

            /*
             * ---------------------------------------------------------
             * Reference pretraining loss over the walker batch.
             * ---------------------------------------------------------
             */
            double[] gradient =
                    new double[
                            state.parameterCount()];

            double loss =
                    0.0;

            FermiNetV1State iterationState =
                    state;

            FermiNetV1State.PretrainingEvaluation[] evaluations =
                    new FermiNetV1State.PretrainingEvaluation[
                            walkers.size()];

            IntStream.range(
                            0,
                            walkers.size())
                    .parallel()
                    .forEach(index -> {

                        var orbitals =
                                target.evaluate(
                                        walkers.get(index));

                        evaluations[index] =
                                iterationState.pretrainingEvaluation(
                                        walkers.get(index),
                                        orbitals.alpha(),
                                        orbitals.beta());
                    });

            for (FermiNetV1State.PretrainingEvaluation evaluation :
                    evaluations) {

                loss +=
                        evaluation.loss();

                double[] sampleGradient =
                        evaluation.gradient();

                for (int parameter = 0;
                     parameter < gradient.length;
                     parameter++) {

                    gradient[parameter] +=
                            sampleGradient[parameter]
                                    / walkers.size();
                }
            }

            loss /=
                    walkers.size();

            if (!Double.isFinite(loss)) {

                throw new IllegalStateException(
                        "non-finite FermiNet pretraining loss at iteration "
                                + iteration);
            }

            for (double value : gradient) {

                if (!Double.isFinite(value)) {

                    throw new IllegalStateException(
                            "non-finite FermiNet pretraining gradient at iteration "
                                    + iteration);
                }
            }

            history.add(
                    loss);

            progress.accept(
                    iteration,
                    loss);

            /*
             * ---------------------------------------------------------
             * Adam update.
             *
             * Same standard Adam parameters as the reference pretraining
             * configuration:
             *
             * beta1 = 0.9
             * beta2 = 0.999
             * eps   = 1e-8
             * ---------------------------------------------------------
             */
            double[] values =
                    FermiNetStateAccess.parameterSnapshot(state);

            double beta1Correction =
                    1.0
                            - Math.pow(
                            ADAM_BETA1,
                            iteration);

            double beta2Correction =
                    1.0
                            - Math.pow(
                            ADAM_BETA2,
                            iteration);

            for (int parameter = 0;
                 parameter < values.length;
                 parameter++) {

                double g =
                        gradient[parameter];

                firstMoment[parameter] =
                        ADAM_BETA1
                                * firstMoment[parameter]
                                + (1.0 - ADAM_BETA1)
                                * g;

                secondMoment[parameter] =
                        ADAM_BETA2
                                * secondMoment[parameter]
                                + (1.0 - ADAM_BETA2)
                                * g
                                * g;

                double correctedFirst =
                        firstMoment[parameter]
                                / beta1Correction;

                double correctedSecond =
                        secondMoment[parameter]
                                / beta2Correction;

                values[parameter] -=
                        configuration.learningRate()
                                * correctedFirst
                                / (Math.sqrt(
                                correctedSecond)
                                + ADAM_EPSILON);
            }

            state =
                    FermiNetStateAccess.replaceParameters(state,
                            values);

            /*
             * ---------------------------------------------------------
             * One all-electron RWM proposal per walker after the parameter
             * update.
             *
             * This matches pretrain.py calling make_mcmc_step(... steps=1).
             * ---------------------------------------------------------
             */
            for (int walker = 0;
                 walker < walkers.size();
                 walker++) {

                QuantumCoordinates current =
                        walkers.get(
                                walker);

                QuantumCoordinates proposal =
                        moveAll(
                                current,
                                configuration.moveWidthBohr(),
                                random);

                double oldLog =
                        samplingLog(
                                state,
                                target,
                                current,
                                configuration.scfFraction());

                double newLog =
                        samplingLog(
                                state,
                                target,
                                proposal,
                                configuration.scfFraction());

                proposed++;

                if (acceptProposal(
                        oldLog,
                        newLog,
                        random)) {

                    walkers.set(
                            walker,
                            proposal);

                    accepted++;
                }
            }
        }

        return new Result(
                state,
                walkers,
                history,
                proposed == 0
                        ? Double.NaN
                        : (double) accepted
                        / proposed,
                "FERMINET_OCCUPIED_ORBITAL_MSE_ADAM_WITH_PER_STEP_RWM",
                REFERENCE_COMMIT);
    }

    /**
     * Sampling log-amplitude.
     *
     * <p>The reference pretrainer interpolates the log amplitudes of the
     * neural and SCF states, then the Metropolis step multiplies by two when
     * converting log amplitude to log probability.
     */
    private static double samplingLog(
            FermiNetV1State state,
            HartreeFockOrbitalTarget target,
            QuantumCoordinates coordinates,
            double fraction) {

        if (fraction == 1.0) {

            var orbitals =
                    target.evaluate(
                            coordinates);

            return logAbsDet(
                    orbitals.alpha())
                    + logAbsDet(
                    orbitals.beta());
        }

        double neural =
                safeNeuralLog(
                        state,
                        coordinates);

        if (fraction == 0.0) {

            return neural;
        }

        var orbitals =
                target.evaluate(
                        coordinates);

        double hf =
                logAbsDet(
                        orbitals.alpha())
                        + logAbsDet(
                        orbitals.beta());

        /*
         * Reference semantics:
         *
         * (1-f) log Psi_NN + f log Psi_HF
         */
        return (1.0 - fraction)
                * neural
                + fraction
                * hf;
    }

    /**
     * A singular neural proposal has zero probability; it must reject rather
     * than terminate the pretraining campaign.
     */
    private static double safeNeuralLog(
            FermiNetV1State state,
            QuantumCoordinates coordinates) {

        try {

            double value =
                    FermiNetStateAccess.spatial(state,
                                    coordinates)
                            .logAbsoluteWavefunction();

            return Double.isFinite(value)
                    ? value
                    : Double.NEGATIVE_INFINITY;

        } catch (IllegalStateException exception) {

            return Double.NEGATIVE_INFINITY;
        }
    }

    private static boolean acceptProposal(
            double oldLog,
            double newLog,
            Random random) {

        /*
         * Both states invalid: retain current walker.
         */
        if (oldLog == Double.NEGATIVE_INFINITY
                && newLog == Double.NEGATIVE_INFINITY) {

            return false;
        }

        /*
         * Escape an invalid state whenever a finite proposal is found.
         */
        if (oldLog == Double.NEGATIVE_INFINITY
                && Double.isFinite(newLog)) {

            return true;
        }

        /*
         * Never enter an invalid proposal.
         */
        if (!Double.isFinite(newLog)) {

            return false;
        }

        if (!Double.isFinite(oldLog)) {

            throw new IllegalStateException(
                    "invalid current pretraining walker log probability");
        }

        double logAcceptance =
                Math.min(
                        0.0,
                        2.0
                                * (newLog
                                - oldLog));

        return Math.log(
                random.nextDouble())
                < logAcceptance;
    }

    private static double logAbsDet(
            double[][] input) {

        int n =
                input.length;

        if (n == 0) {
            return 0.0;
        }

        double[][] matrix =
                new double[n][n];

        for (int row = 0;
             row < n;
             row++) {

            if (input[row].length != n) {

                throw new IllegalArgumentException(
                        "HF orbital matrix must be square");
            }

            matrix[row] =
                    input[row].clone();
        }

        double log =
                0.0;

        for (int column = 0;
             column < n;
             column++) {

            int pivot =
                    column;

            for (int row = column + 1;
                 row < n;
                 row++) {

                if (Math.abs(
                        matrix[row][column])
                        > Math.abs(
                        matrix[pivot][column])) {

                    pivot =
                            row;
                }
            }

            if (Math.abs(
                    matrix[pivot][column])
                    < 1.0e-300) {

                return Double.NEGATIVE_INFINITY;
            }

            double[] swap =
                    matrix[column];

            matrix[column] =
                    matrix[pivot];

            matrix[pivot] =
                    swap;

            double value =
                    matrix[column][column];

            log +=
                    Math.log(
                            Math.abs(
                                    value));

            for (int row = column + 1;
                 row < n;
                 row++) {

                double factor =
                        matrix[row][column]
                                / value;

                for (int j = column + 1;
                     j < n;
                     j++) {

                    matrix[row][j] -=
                            factor
                                    * matrix[column][j];
                }
            }
        }

        return log;
    }

    /**
     * Reference-style initial walker generation.
     */
    private static List<QuantumCoordinates> initialize(
            FermiNetV1State state,
            Configuration configuration,
            Random random) {

        List<CartesianPosition> centers =
                initialElectronCenters(
                        state,
                        random);

        List<QuantumCoordinates> result =
                new ArrayList<>();

        int alpha =
                state.molecule()
                        .spin()
                        .alphaElectrons();

        for (int walker = 0;
             walker < configuration.walkers();
             walker++) {

            List<QuantumCoordinates.ParticleCoordinate> particles =
                    new ArrayList<>();

            for (int electron = 0;
                 electron < centers.size();
                 electron++) {

                CartesianPosition center =
                        centers.get(
                                        electron)
                                .inBohr();

                particles.add(
                        new QuantumCoordinates.ParticleCoordinate(
                                electron,
                                center.x()
                                        + configuration.initialWidthBohr()
                                        * random.nextGaussian(),
                                center.y()
                                        + configuration.initialWidthBohr()
                                        * random.nextGaussian(),
                                center.z()
                                        + configuration.initialWidthBohr()
                                        * random.nextGaussian(),
                                electron < alpha
                                        ? SpinProjection.ALPHA
                                        : SpinProjection.BETA));
            }

            result.add(
                    new QuantumCoordinates(
                            particles));
        }

        return result;
    }

    /**
     * Mirrors the semantics of train.init_electrons():
     *
     * <ul>
     *   <li>neutral molecules begin with isolated-atom spin counts;</li>
     *   <li>atom alpha/beta assignments are randomly swapped;</li>
     *   <li>up to 10,000 swaps are attempted;</li>
     *   <li>a charged single atom directly receives the requested spin
     *       population;</li>
     *   <li>if a neutral multi-atom assignment cannot be found, all centers
     *       fall back to the origin.</li>
     * </ul>
     */
    private static List<CartesianPosition> initialElectronCenters(
            FermiNetV1State state,
            Random random) {

        var molecule =
                state.molecule();

        int atomCount =
                molecule.nuclei()
                        .size();

        int targetAlpha =
                molecule.spin()
                        .alphaElectrons();

        int targetBeta =
                molecule.spin()
                        .betaElectrons();

        int nuclearElectrons =
                molecule.nuclei()
                        .stream()
                        .mapToInt(
                                nucleus ->
                                        nucleus.charge()
                                                .atomicNumber())
                        .sum();

        int requestedElectrons =
                molecule.electrons()
                        .value();

        int[][] atomicSpin =
                new int[atomCount][2];

        /*
         * Official reference special-case:
         * charged single atoms are initialized directly with requested spin
         * populations.
         */
        if (nuclearElectrons != requestedElectrons) {

            if (atomCount != 1) {

                throw new IllegalArgumentException(
                        "reference FermiNet has no initialization policy "
                                + "for charged multi-atom molecules");
            }

            atomicSpin[0][0] =
                    targetAlpha;

            atomicSpin[0][1] =
                    targetBeta;

        } else {

            for (int atom = 0;
                 atom < atomCount;
                 atom++) {

                atomicSpin[atom] =
                        isolatedAtomSpin(
                                molecule.nuclei()
                                        .get(atom)
                                        .charge()
                                        .atomicNumber());
            }

            int iteration =
                    0;

            while (!matchesSpinTotals(
                    atomicSpin,
                    targetAlpha,
                    targetBeta)
                    && iteration
                    < INITIAL_SPIN_ASSIGNMENT_MAX_ITERATIONS) {

                int atom =
                        random.nextInt(
                                atomCount);

                int alpha =
                        atomicSpin[atom][0];

                atomicSpin[atom][0] =
                        atomicSpin[atom][1];

                atomicSpin[atom][1] =
                        alpha;

                iteration++;
            }

            if (!matchesSpinTotals(
                    atomicSpin,
                    targetAlpha,
                    targetBeta)) {

                /*
                 * DeepMind fallback: all electrons are centered on the origin.
                 */
                List<CartesianPosition> origin =
                        new ArrayList<>(
                                requestedElectrons);

                for (int electron = 0;
                     electron < requestedElectrons;
                     electron++) {

                    origin.add(
                            new CartesianPosition(
                                    0.0,
                                    0.0,
                                    0.0,
                                    state.molecule()
                                            .nuclei()
                                            .get(0)
                                            .position()
                                            .unit()));
                }

                return origin;
            }
        }

        List<CartesianPosition> centers =
                new ArrayList<>(
                        requestedElectrons);

        /*
         * Reference ordering:
         *
         * all alpha electrons by atom,
         * then all beta electrons by atom.
         */
        for (int spin = 0;
             spin < 2;
             spin++) {

            for (int atom = 0;
                 atom < atomCount;
                 atom++) {

                for (int assigned = 0;
                     assigned
                             < atomicSpin[atom][spin];
                     assigned++) {

                    centers.add(
                            molecule.nuclei()
                                    .get(atom)
                                    .position());
                }
            }
        }

        if (centers.size()
                != requestedElectrons) {

            throw new IllegalStateException(
                    "initial electron center count mismatch");
        }

        return centers;
    }

    private static boolean matchesSpinTotals(
            int[][] configuration,
            int alpha,
            int beta) {

        int observedAlpha =
                0;

        int observedBeta =
                0;

        for (int[] atom :
                configuration) {

            observedAlpha +=
                    atom[0];

            observedBeta +=
                    atom[1];
        }

        return observedAlpha == alpha
                && observedBeta == beta;
    }

    /**
     * Ground-state isolated-atom Aufbau/Hund population.
     *
     * <p>This is sufficient for the elements currently used by Prometheus.
     * A future general element table should be parity-tested separately against
     * ferminet.utils.elements.
     */
    private static int[] isolatedAtomSpin(
            int atomicNumber) {

        int remaining =
                atomicNumber;

        int alpha =
                0;

        int beta =
                0;

        /*
         * Orbital degeneracies in Aufbau order:
         *
         * 1s, 2s, 2p, 3s, 3p, 4s, 3d, 4p, 5s
         */
        int[] orbitalCounts = {
                1,
                1,
                3,
                1,
                3,
                1,
                5,
                3,
                1
        };

        for (int orbitals :
                orbitalCounts) {

            int occupied =
                    Math.min(
                            remaining,
                            2 * orbitals);

            alpha +=
                    Math.min(
                            occupied,
                            orbitals);

            beta +=
                    Math.max(
                            0,
                            occupied - orbitals);

            remaining -=
                    occupied;

            if (remaining == 0) {

                return new int[] {
                        alpha,
                        beta
                };
            }
        }

        throw new IllegalArgumentException(
                "unsupported isolated-atom configuration for Z="
                        + atomicNumber);
    }

    /**
     * Exact reference pretraining proposal family:
     *
     * x' = x + width * Normal(0,1)
     *
     * for every Cartesian electron coordinate simultaneously.
     */
    private static QuantumCoordinates moveAll(
            QuantumCoordinates coordinates,
            double width,
            Random random) {

        List<QuantumCoordinates.ParticleCoordinate> moved =
                new ArrayList<>();

        for (var electron :
                coordinates.particles()) {

            moved.add(
                    new QuantumCoordinates.ParticleCoordinate(
                            electron.particleIndex(),
                            electron.xBohr()
                                    + width
                                    * random.nextGaussian(),
                            electron.yBohr()
                                    + width
                                    * random.nextGaussian(),
                            electron.zBohr()
                                    + width
                                    * random.nextGaussian(),
                            electron.spin()));
        }

        return new QuantumCoordinates(
                moved);
    }
}
