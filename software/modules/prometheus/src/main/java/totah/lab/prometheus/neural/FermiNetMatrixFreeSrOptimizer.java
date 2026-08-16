package totah.lab.prometheus.neural;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.numerics.FermiNetSampleSpaceSrSolver;
import totah.lab.prometheus.variational.QuantumCoordinates;

/**
 * Sample-space stochastic reconfiguration for derivative-complete FermiNet states.
 *
 * <p>Each non-zero-weight sample is evaluated exactly once and its complete
 * parameter log-derivative vector is written to a temporary observation file.
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

        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(configuration, "configuration");

        if (samples.isEmpty()) {
            throw new IllegalArgumentException("empty FermiNet SR sample set");
        }

        try (FermiNetSrObservationFile observations =
                     FermiNetSrObservationFile.build(
                             state,
                             samples)) {

            FermiNetSampleSpaceSrSolver.Result solve =
                    new FermiNetSampleSpaceSrSolver()
                            .solve(
                                    observations,
                                    configuration.damping(),
                                    configuration.blockSize());

            double[] delta =
                    solve.delta();

            requireFinite(
                    delta,
                    "SR solution");

            double[] energyGradient =
                    gradientFromObservations(
                            observations);

            double gradientNorm =
                    norm(
                            energyGradient);

            requireFinite(
                    gradientNorm,
                    "gradient norm");

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

            double[] next =
                    state.parameterArray();

            for (int i = 0; i < next.length; i++) {
                next[i] +=
                        update[i];

                requireFinite(
                        next[i],
                        "updated parameter");
            }

            return new Result(
                    state.withParameters(next),
                    solve.meanEnergyHartree(),
                    gradientNorm,
                    rawUpdateNorm,
                    appliedUpdateNorm,
                    solve.absoluteSampleSpaceResidual(),
                    solve.relativeSampleSpaceResidual(),
                    1,
                    2,
                    observations.neuralEvaluations(),
                    rescaled,
                    energyGradient,
                    List.of(
                            solve.relativeSampleSpaceResidual()));
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

    private static double[] gradientFromObservations(
            FermiNetSrObservationFile observations)
            throws IOException {

        int samples =
                observations.sampleCount();

        int parameters =
                observations.parameterCount();

        double weightSum =
                0.0;

        double meanEnergy =
                0.0;

        for (int sample = 0; sample < samples; sample++) {
            weightSum +=
                    observations.weight(sample);

            meanEnergy +=
                    observations.weight(sample)
                            * observations.localEnergyHartree(sample);
        }

        if (!(weightSum > 0.0)
                || !Double.isFinite(weightSum)) {
            throw new IllegalArgumentException(
                    "non-positive FermiNet SR total weight");
        }

        meanEnergy /=
                weightSum;

        double[] gradient =
                new double[parameters];

        int blockSize =
                Math.min(
                        8192,
                        parameters);

        double[] block =
                new double[
                        Math.multiplyExact(
                                samples,
                                blockSize)];

        for (int start = 0;
             start < parameters;
             start += blockSize) {

            int length =
                    Math.min(
                            blockSize,
                            parameters - start);

            observations.readParameterBlock(
                    start,
                    length,
                    block);

            for (int local = 0;
                 local < length;
                 local++) {

                double value =
                        0.0;

                for (int sample = 0;
                     sample < samples;
                     sample++) {

                    double normalizedWeight =
                            observations.weight(sample)
                                    / weightSum;

                    value +=
                            2.0
                                    * normalizedWeight
                                    * (observations.localEnergyHartree(sample)
                                    - meanEnergy)
                                    * block[sample * length + local];
                }

                gradient[start + local] =
                        value;
            }
        }

        requireFinite(
                gradient,
                "energy gradient");

        return gradient;
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
}