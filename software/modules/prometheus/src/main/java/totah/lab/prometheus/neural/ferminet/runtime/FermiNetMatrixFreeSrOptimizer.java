package totah.lab.prometheus.neural.ferminet.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.variational.QuantumCoordinates;

/**
 * Sample-space stochastic reconfiguration for derivative-complete FermiNet states.
 *
 * <p>Each non-zero-weight sample is evaluated exactly once. Compact generic
 * sufficient statistics are written to an ephemeral spool; the full
 * sample-by-parameter Jacobian is never materialized or persisted.
 * The SR solve is then performed in sample space:
 *
 * <pre>
 * delta = -B^T (B B^T + damping I)^-1 q
 * </pre>
 *
 * where B = sqrt(W) C and C is the centered sample-by-parameter derivative matrix.
 *
 * <p>This removes high-dimensional PCG from the production path while preserving
 * the same damped SR equations.
 */
public final class FermiNetMatrixFreeSrOptimizer {

    public Result oneIteration(
            FermiNetV1State state,
            List<WeightedSample> samples,
            Configuration configuration) {

        return oneIteration(state, samples, null, configuration);
    }

    Result oneIteration(
            FermiNetV1State state,
            List<WeightedSample> samples,
            FermiNetKnownLocalEnergies knownLocalEnergies,
            Configuration configuration) {

        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(configuration, "configuration");

        if (knownLocalEnergies != null) {
            knownLocalEnergies.validate(state, samples);
        }

        if (samples.isEmpty()) {
            throw new IllegalArgumentException("empty FermiNet SR sample set");
        }

        long totalIterationStarted =
                System.nanoTime();

        long observationStarted =
                System.nanoTime();

        System.out.println("""
                FERMINET_SR_IMPLEMENTATION
                  SR implementation       : structured Jacobian-free sample-space SR
                  implementation class    : FermiNetStructuredSampleSpaceSrSolver
                  temporary storage       : compact sufficient-statistics spool
                  full N×P Jacobian       : no
                  derivative file         : no
                """);

        try (FermiNetStructuredSrObservationFile observations =
                     knownLocalEnergies == null
                             ? FermiNetStructuredSrObservationFile.buildParallel(
                                     state,
                                     samples,
                                     configuration.observationParallelism())
                             : FermiNetStructuredSrObservationFile.buildParallel(
                                     state,
                                     samples,
                                     knownLocalEnergies,
                                     configuration.observationParallelism())) {

            long observationConstructionNanos =
                    System.nanoTime() - observationStarted;

            observations.printTiming(
                    observationConstructionNanos,
                    configuration.observationParallelism());

            long sampleSpaceSolveStarted =
                    System.nanoTime();

            FermiNetStructuredSampleSpaceSrSolver.Result solve =
                    new FermiNetStructuredSampleSpaceSrSolver()
                            .solve(
                                    observations,
                                    configuration.damping());

            long sampleSpaceSolveNanos =
                    System.nanoTime() - sampleSpaceSolveStarted;

            double[] energyGradient =
                    solve.energyGradient();

            double gradientNorm =
                    norm(
                            energyGradient);

            requireFinite(
                    gradientNorm,
                    "gradient norm");

            long updateRescalingStarted =
                    System.nanoTime();

            double[] delta =
                    solve.delta();

            requireFinite(
                    delta,
                    "SR solution");

            double[] update =
                    new double[delta.length];

            for (int i = 0; i < delta.length; i++) {
                update[i] =
                        configuration.learningRate()
                                * delta[i];
            }

            double rawUpdateNorm =
                    norm(
                            update);

            requireFinite(
                    rawUpdateNorm,
                    "raw update norm");

            boolean rescaled =
                    rawUpdateNorm
                            > configuration.maxUpdateNorm();

            if (rescaled) {
                double scale =
                        configuration.maxUpdateNorm()
                                / rawUpdateNorm;

                for (int i = 0; i < update.length; i++) {
                    update[i] *=
                            scale;
                }
            }

            double appliedUpdateNorm =
                    norm(
                            update);

            requireFinite(
                    appliedUpdateNorm,
                    "applied update norm");

            long updateRescalingNanos =
                    System.nanoTime() - updateRescalingStarted;

            long newStateConstructionStarted =
                    System.nanoTime();

            double[] next =
                    state.parameterArray();

            for (int i = 0; i < next.length; i++) {
                next[i] +=
                        update[i];

                requireFinite(
                        next[i],
                        "updated parameter");
            }

            FermiNetV1State nextState =
                    state.withParameters(next);

            long newStateConstructionNanos =
                    System.nanoTime() - newStateConstructionStarted;

            long totalIterationNanos =
                    System.nanoTime() - totalIterationStarted;

            Timing timing =
                    new Timing(
                            configuration.observationParallelism(),
                            observationConstructionNanos,
                            sampleSpaceSolveNanos,
                            updateRescalingNanos,
                            newStateConstructionNanos,
                            totalIterationNanos);

            timing.print();

            return new Result(
                    nextState,
                    solve.meanEnergyHartree(),
                    gradientNorm,
                    rawUpdateNorm,
                    appliedUpdateNorm,
                    solve.absoluteSampleSpaceResidual(),
                    solve.relativeSampleSpaceResidual(),
                    solve.linearSolveCount(),
                    0,
                    observations.neuralEvaluations(),
                    rescaled,
                    energyGradient,
                    List.of(
                            solve.relativeSampleSpaceResidual()),
                    timing);
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "FermiNet SR observation I/O failed",
                    exception);
        }
    }

    /**
     * Package-private reference seam retained for tests. This computes the same
     * covariance action directly from fresh FermiNet evaluations and is not used
     * by production sample-space SR.
     */
    double[] explicitJacobianCovarianceActionReference(
            FermiNetV1State state,
            List<WeightedSample> samples,
            double damping,
            double[] vector) {

        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(vector, "vector");

        if (samples.isEmpty()) {
            throw new IllegalArgumentException(
                    "empty FermiNet SR sample set");
        }

        int parameters =
                state.parameterCount();

        if (vector.length != parameters) {
            throw new IllegalArgumentException(
                    "vector dimension mismatch");
        }

        double weightSum =
                0.0;

        double[] mean =
                new double[parameters];

        for (WeightedSample sample : samples) {
            if (sample.weight() == 0.0) {
                continue;
            }

            double[] derivatives =
                    state.evaluate(
                                    sample.coordinates())
                            .parameterLogDerivatives();

            weightSum +=
                    sample.weight();

            for (int i = 0; i < parameters; i++) {
                mean[i] +=
                        sample.weight()
                                * derivatives[i];
            }
        }

        if (!(weightSum > 0.0)
                || !Double.isFinite(weightSum)) {
            throw new IllegalArgumentException(
                    "non-positive FermiNet SR total weight");
        }

        for (int i = 0; i < parameters; i++) {
            mean[i] /=
                    weightSum;
        }

        double[] result =
                new double[parameters];

        for (WeightedSample sample : samples) {
            if (sample.weight() == 0.0) {
                continue;
            }

            double[] derivatives =
                    state.evaluate(
                                    sample.coordinates())
                            .parameterLogDerivatives();

            double projection =
                    0.0;

            for (int i = 0; i < parameters; i++) {
                projection +=
                        (derivatives[i] - mean[i])
                                * vector[i];
            }

            double scale =
                    sample.weight()
                            / weightSum
                            * projection;

            for (int i = 0; i < parameters; i++) {
                result[i] +=
                        scale
                                * (derivatives[i] - mean[i]);
            }
        }

        for (int i = 0; i < parameters; i++) {
            result[i] +=
                    damping
                            * vector[i];
        }

        requireFinite(
                result,
                "covariance-operator result");

        return result;
    }

    private static double norm(
            double[] values) {

        double scale =
                0.0;

        double sum =
                1.0;

        for (double value : values) {
            double absolute =
                    Math.abs(value);

            if (absolute != 0.0) {
                if (scale < absolute) {
                    double ratio =
                            scale
                                    / absolute;

                    sum =
                            1.0
                                    + sum
                                    * ratio
                                    * ratio;

                    scale =
                            absolute;
                } else {
                    double ratio =
                            absolute
                                    / scale;

                    sum +=
                            ratio
                                    * ratio;
                }
            }
        }

        return scale == 0.0
                ? 0.0
                : scale * Math.sqrt(sum);
    }

    private static void requireFinite(
            double value,
            String label) {

        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "non-finite "
                            + label);
        }
    }

    private static void requireFinite(
            double[] values,
            String label) {

        for (double value : values) {
            requireFinite(
                    value,
                    label);
        }
    }

    public record WeightedSample(
            double weight,
            QuantumCoordinates coordinates) {

        public WeightedSample {
            Objects.requireNonNull(
                    coordinates,
                    "coordinates");

            if (!Double.isFinite(weight)
                    || weight < 0.0) {
                throw new IllegalArgumentException(
                        "invalid FermiNet SR sample weight");
            }
        }
    }

    public record Configuration(
            double learningRate,
            double damping,
            double maxUpdateNorm,
            int observationParallelism) {

        /** Compatibility constructor for archived, non-production diagnostics. */
        @Deprecated(forRemoval = true)
        public Configuration(
                double learningRate,
                double damping,
                double maxUpdateNorm,
                int observationParallelism,
                int ignoredBlockSize,
                int ignoredMaxSolverIterations,
                double ignoredRelativeTolerance,
                double ignoredAbsoluteTolerance) {
            this(learningRate, damping, maxUpdateNorm, observationParallelism);
        }

        public Configuration {
            if (observationParallelism < 1) {
                throw new IllegalArgumentException(
                        "invalid SR observation parallelism");
            }

            if (!(learningRate > 0.0)
                    || !Double.isFinite(learningRate)
                    || !(damping > 0.0)
                    || !Double.isFinite(damping)
                    || !(maxUpdateNorm > 0.0)
                    || !Double.isFinite(maxUpdateNorm)) {
                throw new IllegalArgumentException(
                        "invalid FermiNet SR configuration");
            }
        }
    }

    public record Result(
            FermiNetV1State state,
            double initialEnergyHartree,
            double gradientNorm,
            double rawUpdateNorm,
            double appliedUpdateNorm,
            double absoluteTrueResidual,
            double relativeTrueResidual,
            int solverIterations,
            long streamedOperatorPasses,
            long sampleEvaluations,
            boolean updateRescaled,
            double[] energyGradient,
            List<Double> trueResidualHistory,
            Timing timing) {

        /**
         * {@code solverIterations} is the number of direct sample-space linear
         * solves. {@code streamedOperatorPasses} is a legacy compatibility field
         * and is always zero for structured SR because no streamed covariance
         * operator or derivative-file pass executes in production.
         */

        public Result {
            Objects.requireNonNull(
                    state,
                    "state");

            Objects.requireNonNull(
                    trueResidualHistory,
                    "trueResidualHistory");

            Objects.requireNonNull(
                    timing,
                    "timing");

            energyGradient =
                    energyGradient.clone();

            trueResidualHistory =
                    List.copyOf(
                            trueResidualHistory);
        }

        @Override
        public double[] energyGradient() {
            return energyGradient.clone();
        }

        @Override
        public List<Double> trueResidualHistory() {
            return List.copyOf(
                    trueResidualHistory);
        }
    }

    public record Timing(
            int observationParallelism,
            long observationConstructionNanos,
            long sampleSpaceSolveNanos,
            long updateRescalingNanos,
            long newStateConstructionNanos,
            long totalIterationNanos) {

        public void print() {
            System.out.printf("""
                    FERMINET_SR_ITERATION_TIMING
                      observation_parallelism=%d
                      observation_construction_ms=%.3f
                      sample_space_solve_ms=%.3f
                      update_rescaling_ms=%.3f
                      new_state_construction_ms=%.3f
                      total_iteration_ms=%.3f

                    """,
                    observationParallelism,
                    millis(observationConstructionNanos),
                    millis(sampleSpaceSolveNanos),
                    millis(updateRescalingNanos),
                    millis(newStateConstructionNanos),
                    millis(totalIterationNanos));
        }
    }

    private static double millis(long nanos) {
        return nanos / 1.0e6;
    }
}
