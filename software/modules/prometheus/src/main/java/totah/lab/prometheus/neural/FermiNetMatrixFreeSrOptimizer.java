package totah.lab.prometheus.neural;

import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.numerics.FixedPreconditioners;
import totah.lab.prometheus.numerics.LinearOperator;
import totah.lab.prometheus.numerics.StreamingCovarianceOperator;
import totah.lab.prometheus.numerics.TrueResidualPreconditionedConjugateGradientSolver;
import totah.lab.prometheus.variational.QuantumCoordinates;

/**
 * Streaming stochastic reconfiguration for derivative-complete FermiNet states.
 *
 * <p>The current bounded-memory implementation uses identity preconditioning.
 * This preserves O(p) solver-vector storage and avoids materializing either a
 * dense covariance matrix or a covariance-diagonal preconditioner pass.
 *
 * <p>The SR operator itself remains the regularized covariance:
 *
 * <pre>
 *     (S + damping I) delta = -gradient
 * </pre>
 *
 * and convergence is accepted only through the independently recomputed
 * true-residual gate in {@link TrueResidualPreconditionedConjugateGradientSolver}.
 */
public final class FermiNetMatrixFreeSrOptimizer {

    public Result oneIteration(
            FermiNetV1State state,
            List<WeightedSample> samples,
            Configuration configuration) {

        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(configuration, "configuration");

        if (samples.isEmpty()) {
            throw new IllegalArgumentException(
                    "empty FermiNet SR sample set");
        }

        int parameters = state.parameterCount();
        Counters counters = new Counters();

        Statistics statistics =
                statistics(
                        state,
                        samples,
                        parameters,
                        counters);

        double gradientNorm =
                norm(statistics.gradient);

        requireFinite(
                gradientNorm,
                "gradient norm");

        StreamingCovarianceOperator covariance =
                new StreamingCovarianceOperator(
                        parameters,
                        consumer -> forEachEvaluation(
                                state,
                                samples,
                                parameters,
                                counters,
                                evaluation -> {
                                    double[] centered =
                                            evaluation.derivatives.clone();

                                    for (int i = 0; i < parameters; i++) {
                                        centered[i] -=
                                                statistics.meanDerivative[i];
                                    }

                                    consumer.accept(
                                            evaluation.weight
                                                    / statistics.weightSum,
                                            centered);
                                }),
                        configuration.damping());

        LinearOperator checkedOperator =
                new LinearOperator() {
                    @Override
                    public int dimension() {
                        return parameters;
                    }

                    @Override
                    public double[] apply(double[] vector) {
                        double[] result =
                                covariance.apply(vector);

                        requireFinite(
                                result,
                                "covariance-operator result");

                        return result;
                    }
                };

        double[] rightHandSide =
                statistics.gradient.clone();

        for (int i = 0; i < parameters; i++) {
            rightHandSide[i] =
                    -rightHandSide[i];
        }

        TrueResidualPreconditionedConjugateGradientSolver.Result solve;

        if (gradientNorm == 0.0) {
            solve =
                    new TrueResidualPreconditionedConjugateGradientSolver.Result(
                            new double[parameters],
                            0,
                            0,
                            0.0,
                            0.0,
                            List.of(0.0),
                            true);
        } else {
            solve =
                    new TrueResidualPreconditionedConjugateGradientSolver()
                            .solve(
                                    checkedOperator,
                                    FixedPreconditioners.identity(parameters),
                                    rightHandSide,
                                    new TrueResidualPreconditionedConjugateGradientSolver.Configuration(
                                            configuration.maxSolverIterations(),
                                            configuration.relativeTolerance(),
                                            configuration.absoluteTolerance()));
        }

        if (!solve.converged()
                || !Double.isFinite(solve.relativeTrueResidual())
                || !Double.isFinite(solve.absoluteTrueResidual())) {

            throw new IllegalStateException(
                    "FermiNet SR failed true-residual convergence gate"
                            + System.lineSeparator()
                            + "iterations="
                            + solve.iterations()
                            + System.lineSeparator()
                            + "absoluteTrueResidual="
                            + solve.absoluteTrueResidual()
                            + System.lineSeparator()
                            + "relativeTrueResidual="
                            + solve.relativeTrueResidual()
                            + System.lineSeparator()
                            + "trueResidualHistory="
                            + solve.trueResidualHistory());
        }

        double[] delta =
                solve.solution();

        requireFinite(
                delta,
                "SR solution");

        double[] update =
                new double[parameters];

        for (int i = 0; i < parameters; i++) {
            update[i] =
                    configuration.learningRate()
                            * delta[i];
        }

        double rawUpdateNorm =
                norm(update);

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

            for (int i = 0; i < parameters; i++) {
                update[i] *= scale;
            }
        }

        double appliedUpdateNorm =
                norm(update);

        requireFinite(
                appliedUpdateNorm,
                "applied update norm");

        double[] next =
                state.parameterArray();

        for (int i = 0; i < parameters; i++) {
            next[i] +=
                    update[i];

            requireFinite(
                    next[i],
                    "updated parameter");
        }

        return new Result(
                state.withParameters(next),
                statistics.energy,
                gradientNorm,
                rawUpdateNorm,
                appliedUpdateNorm,
                solve.absoluteTrueResidual(),
                solve.relativeTrueResidual(),
                solve.iterations(),
                covariance.counters().streamedPasses(),
                counters.evaluations,
                rescaled,
                statistics.gradient,
                solve.trueResidualHistory());
    }

    /**
     * Package-private verification seam using the exact same streamed covariance
     * operator as production SR.
     */
    double[] covarianceAction(
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

        Counters counters =
                new Counters();

        Statistics statistics =
                statistics(
                        state,
                        samples,
                        parameters,
                        counters);

        StreamingCovarianceOperator operator =
                new StreamingCovarianceOperator(
                        parameters,
                        consumer -> forEachEvaluation(
                                state,
                                samples,
                                parameters,
                                counters,
                                evaluation -> {
                                    double[] centered =
                                            evaluation.derivatives.clone();

                                    for (int i = 0; i < parameters; i++) {
                                        centered[i] -=
                                                statistics.meanDerivative[i];
                                    }

                                    consumer.accept(
                                            evaluation.weight
                                                    / statistics.weightSum,
                                            centered);
                                }),
                        damping);

        double[] result =
                operator.apply(vector);

        requireFinite(
                result,
                "covariance-operator result");

        return result;
    }

    private static Statistics statistics(
            FermiNetV1State state,
            List<WeightedSample> samples,
            int parameters,
            Counters counters) {

        double[] sumDerivative =
                new double[parameters];

        double[] sumDerivativeEnergy =
                new double[parameters];

        double[] totals =
                new double[2];

        forEachEvaluation(
                state,
                samples,
                parameters,
                counters,
                evaluation -> {
                    totals[0] +=
                            evaluation.weight;

                    totals[1] +=
                            evaluation.weight
                                    * evaluation.energy;

                    for (int i = 0; i < parameters; i++) {
                        sumDerivative[i] +=
                                evaluation.weight
                                        * evaluation.derivatives[i];

                        sumDerivativeEnergy[i] +=
                                evaluation.weight
                                        * evaluation.derivatives[i]
                                        * evaluation.energy;
                    }
                });

        if (!(totals[0] > 0.0)
                || !Double.isFinite(totals[0])) {

            throw new IllegalArgumentException(
                    "non-positive FermiNet SR total weight");
        }

        double energy =
                totals[1]
                        / totals[0];

        requireFinite(
                energy,
                "mean local energy");

        double[] mean =
                new double[parameters];

        double[] gradient =
                new double[parameters];

        for (int i = 0; i < parameters; i++) {
            mean[i] =
                    sumDerivative[i]
                            / totals[0];

            gradient[i] =
                    2.0
                            * (sumDerivativeEnergy[i] / totals[0]
                            - mean[i] * energy);
        }

        requireFinite(
                mean,
                "mean parameter derivative");

        requireFinite(
                gradient,
                "energy gradient");

        return new Statistics(
                totals[0],
                energy,
                mean,
                gradient);
    }

    private static void forEachEvaluation(
            FermiNetV1State state,
            List<WeightedSample> samples,
            int parameters,
            Counters counters,
            EvaluationConsumer consumer) {

        for (WeightedSample sample : samples) {
            if (!Double.isFinite(sample.weight())
                    || sample.weight() < 0.0) {

                throw new IllegalArgumentException(
                        "invalid FermiNet SR sample weight");
            }

            if (sample.weight() == 0.0) {
                continue;
            }

            FermiNetV1State.Evaluation evaluated =
                    state.evaluate(
                            sample.coordinates());

            double[] derivatives =
                    evaluated.parameterLogDerivatives();

            if (derivatives.length != parameters) {
                throw new IllegalArgumentException(
                        "parameter derivative dimension mismatch");
            }

            requireFinite(
                    derivatives,
                    "parameter log derivative");

            /*
             * The validated APIs do not expose a canonical local-energy method
             * accepting this Evaluation. FermiNetVmc.localEnergy is therefore
             * retained as the Hamiltonian boundary.
             */
            double energy =
                    FermiNetVmc.localEnergy(
                                    state,
                                    sample.coordinates())
                            .totalHartree();

            requireFinite(
                    energy,
                    "local energy");

            counters.evaluations++;

            consumer.accept(
                    new SampleEvaluation(
                            sample.weight(),
                            energy,
                            derivatives));
        }
    }

    private static double norm(double[] values) {
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
            int blockSize,
            int maxSolverIterations,
            double relativeTolerance,
            double absoluteTolerance) {

        public Configuration {
            if (!(learningRate > 0.0)
                    || !Double.isFinite(learningRate)
                    || !(damping > 0.0)
                    || !Double.isFinite(damping)
                    || !(maxUpdateNorm > 0.0)
                    || !Double.isFinite(maxUpdateNorm)
                    || blockSize < 1
                    || maxSolverIterations < 1
                    || !(relativeTolerance > 0.0)
                    || !Double.isFinite(relativeTolerance)
                    || !(absoluteTolerance > 0.0)
                    || !Double.isFinite(absoluteTolerance)) {

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
            List<Double> trueResidualHistory) {

        public Result {
            Objects.requireNonNull(
                    state,
                    "state");

            Objects.requireNonNull(
                    energyGradient,
                    "energyGradient");

            Objects.requireNonNull(
                    trueResidualHistory,
                    "trueResidualHistory");

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

    private record Statistics(
            double weightSum,
            double energy,
            double[] meanDerivative,
            double[] gradient) {
    }

    private record SampleEvaluation(
            double weight,
            double energy,
            double[] derivatives) {
    }

    @FunctionalInterface
    private interface EvaluationConsumer {
        void accept(
                SampleEvaluation value);
    }

    private static final class Counters {
        private long evaluations;
    }
}