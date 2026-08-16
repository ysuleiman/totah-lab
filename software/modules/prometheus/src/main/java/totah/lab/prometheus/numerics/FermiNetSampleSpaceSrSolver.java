package totah.lab.prometheus.numerics;

import java.io.IOException;
import java.util.Objects;

import totah.lab.prometheus.neural.FermiNetSrObservationFile;

/**
 * Exact damped stochastic-reconfiguration solve in sample space.
 *
 * <p>Let C be the centered sample-by-parameter log-derivative matrix and W the
 * diagonal matrix of normalized sample weights. Define B = sqrt(W) C.
 *
 * <p>The conventional parameter-space SR system is:
 *
 * <pre>
 * (B^T B + lambda I) delta = -g
 * </pre>
 *
 * with VMC energy gradient:
 *
 * <pre>
 * g = B^T q
 * q_k = 2 sqrt(w_k) (E_k - mean(E))
 * </pre>
 *
 * Therefore:
 *
 * <pre>
 * delta = -B^T (B B^T + lambda I)^-1 q
 * </pre>
 *
 * so the only dense linear solve is sampleCount x sampleCount.
 *
 * <p>The derivative file is read in parameter blocks. Each solve performs one
 * complete derivative sweep to build the sample-space Gram matrix and one
 * complete derivative sweep to reconstruct delta.
 */
public final class FermiNetSampleSpaceSrSolver {

    private static final int DEFAULT_PARAMETER_BLOCK = 8192;

    public Result solve(
            FermiNetSrObservationFile observations,
            double damping)
            throws IOException {

        return solve(
                observations,
                damping,
                DEFAULT_PARAMETER_BLOCK);
    }

    public Result solve(
            FermiNetSrObservationFile observations,
            double damping,
            int parameterBlockSize)
            throws IOException {

        Objects.requireNonNull(
                observations,
                "observations");

        if (!(damping > 0.0)
                || !Double.isFinite(damping)) {
            throw new IllegalArgumentException(
                    "invalid SR damping");
        }

        if (parameterBlockSize < 1) {
            throw new IllegalArgumentException(
                    "invalid parameter block size");
        }

        int samples =
                observations.sampleCount();

        int parameters =
                observations.parameterCount();

        double weightSum = 0.0;
        double weightedEnergy = 0.0;

        for (int sample = 0;
             sample < samples;
             sample++) {

            double weight =
                    observations.weight(sample);

            if (!Double.isFinite(weight)
                    || weight < 0.0) {
                throw new IllegalArgumentException(
                        "invalid SR sample weight");
            }

            weightSum +=
                    weight;

            weightedEnergy +=
                    weight
                            * observations.localEnergyHartree(sample);
        }

        if (!(weightSum > 0.0)
                || !Double.isFinite(weightSum)) {
            throw new IllegalArgumentException(
                    "non-positive SR total weight");
        }

        double meanEnergy =
                weightedEnergy
                        / weightSum;

        double[] normalizedWeight =
                new double[samples];

        double[] sqrtWeight =
                new double[samples];

        double[] q =
                new double[samples];

        for (int sample = 0;
             sample < samples;
             sample++) {

            normalizedWeight[sample] =
                    observations.weight(sample)
                            / weightSum;

            sqrtWeight[sample] =
                    Math.sqrt(
                            normalizedWeight[sample]);

            q[sample] =
                    2.0
                            * sqrtWeight[sample]
                            * (observations.localEnergyHartree(sample)
                            - meanEnergy);
        }

        double[] gram =
                new double[
                        Math.multiplyExact(
                                samples,
                                samples)];

        int maximumBlock =
                Math.min(
                        parameterBlockSize,
                        parameters);

        double[] block =
                new double[
                        Math.multiplyExact(
                                samples,
                                maximumBlock)];

        double[] centeredWeighted =
                new double[samples];

        long gramDerivativeValuesRead = 0L;

        for (int parameterStart = 0;
             parameterStart < parameters;
             parameterStart += maximumBlock) {

            int length =
                    Math.min(
                            maximumBlock,
                            parameters - parameterStart);

            observations.readParameterBlock(
                    parameterStart,
                    length,
                    block);

            gramDerivativeValuesRead +=
                    (long) samples
                            * length;

            for (int local = 0;
                 local < length;
                 local++) {

                double mean =
                        0.0;

                for (int sample = 0;
                     sample < samples;
                     sample++) {

                    mean +=
                            normalizedWeight[sample]
                                    * block[sample * length + local];
                }

                for (int sample = 0;
                     sample < samples;
                     sample++) {

                    centeredWeighted[sample] =
                            sqrtWeight[sample]
                                    * (block[sample * length + local]
                                    - mean);
                }

                /*
                 * Rank-one update gram += b_i b_i^T.
                 * Accumulate only the lower triangle, then mirror.
                 */
                for (int row = 0;
                     row < samples;
                     row++) {

                    double left =
                            centeredWeighted[row];

                    int rowOffset =
                            row
                                    * samples;

                    for (int column = 0;
                         column <= row;
                         column++) {

                        gram[rowOffset + column] +=
                                left
                                        * centeredWeighted[column];
                    }
                }
            }
        }

        for (int row = 0;
             row < samples;
             row++) {

            int rowOffset =
                    row
                            * samples;

            gram[rowOffset + row] +=
                    damping;

            for (int column = 0;
                 column < row;
                 column++) {

                gram[column * samples + row] =
                        gram[rowOffset + column];
            }
        }

        double[] y =
                choleskySolve(
                        gram,
                        q,
                        samples);

        double[] delta =
                new double[parameters];

        long reconstructionDerivativeValuesRead = 0L;

        for (int parameterStart = 0;
             parameterStart < parameters;
             parameterStart += maximumBlock) {

            int length =
                    Math.min(
                            maximumBlock,
                            parameters - parameterStart);

            observations.readParameterBlock(
                    parameterStart,
                    length,
                    block);

            reconstructionDerivativeValuesRead +=
                    (long) samples
                            * length;

            for (int local = 0;
                 local < length;
                 local++) {

                double mean =
                        0.0;

                for (int sample = 0;
                     sample < samples;
                     sample++) {

                    mean +=
                            normalizedWeight[sample]
                                    * block[sample * length + local];
                }

                double value =
                        0.0;

                for (int sample = 0;
                     sample < samples;
                     sample++) {

                    double centered =
                            block[sample * length + local]
                                    - mean;

                    value +=
                            sqrtWeight[sample]
                                    * centered
                                    * y[sample];
                }

                delta[parameterStart + local] =
                        -value;
            }
        }

        requireFinite(
                delta,
                "sample-space SR update");

        double[] residual =
                multiply(
                        gram,
                        y,
                        samples);

        for (int i = 0; i < samples; i++) {
            residual[i] -=
                    q[i];
        }

        double absoluteResidual =
                norm(residual);

        double qNorm =
                norm(q);

        return new Result(
                delta,
                meanEnergy,
                absoluteResidual,
                qNorm == 0.0
                        ? 0.0
                        : absoluteResidual / qNorm,
                gramDerivativeValuesRead,
                reconstructionDerivativeValuesRead,
                parameterBlockSize);
    }

    private static double[] choleskySolve(
            double[] matrix,
            double[] rhs,
            int n) {

        double[] lower =
                matrix.clone();

        /*
         * In-place lower-triangular Cholesky in a flat row-major array.
         */
        for (int row = 0;
             row < n;
             row++) {

            for (int column = 0;
                 column <= row;
                 column++) {

                double value =
                        lower[row * n + column];

                for (int k = 0;
                     k < column;
                     k++) {

                    value -=
                            lower[row * n + k]
                                    * lower[column * n + k];
                }

                if (row == column) {
                    if (!(value > 0.0)
                            || !Double.isFinite(value)) {
                        throw new IllegalArgumentException(
                                "sample-space SR matrix is not SPD at diagonal "
                                        + row
                                        + ": "
                                        + value);
                    }

                    lower[row * n + column] =
                            Math.sqrt(value);
                } else {
                    lower[row * n + column] =
                            value
                                    / lower[column * n + column];
                }
            }

            for (int column = row + 1;
                 column < n;
                 column++) {

                lower[row * n + column] =
                        0.0;
            }
        }

        double[] forward =
                rhs.clone();

        for (int row = 0;
             row < n;
             row++) {

            double value =
                    forward[row];

            for (int column = 0;
                 column < row;
                 column++) {

                value -=
                        lower[row * n + column]
                                * forward[column];
            }

            forward[row] =
                    value
                            / lower[row * n + row];
        }

        double[] solution =
                forward.clone();

        for (int row = n - 1;
             row >= 0;
             row--) {

            double value =
                    solution[row];

            for (int column = row + 1;
                 column < n;
                 column++) {

                value -=
                        lower[column * n + row]
                                * solution[column];
            }

            solution[row] =
                    value
                            / lower[row * n + row];
        }

        requireFinite(
                solution,
                "sample-space solution");

        return solution;
    }

    private static double[] multiply(
            double[] matrix,
            double[] vector,
            int n) {

        double[] result =
                new double[n];

        for (int row = 0;
             row < n;
             row++) {

            double value =
                    0.0;

            int offset =
                    row
                            * n;

            for (int column = 0;
                 column < n;
                 column++) {

                value +=
                        matrix[offset + column]
                                * vector[column];
            }

            result[row] =
                    value;
        }

        return result;
    }

    private static double norm(
            double[] values) {

        double sum =
                0.0;

        for (double value : values) {
            sum +=
                    value
                            * value;
        }

        return Math.sqrt(sum);
    }

    private static void requireFinite(
            double[] values,
            String label) {

        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "non-finite "
                                + label);
            }
        }
    }

    public record Result(
            double[] delta,
            double meanEnergyHartree,
            double absoluteSampleSpaceResidual,
            double relativeSampleSpaceResidual,
            long gramDerivativeValuesRead,
            long reconstructionDerivativeValuesRead,
            int parameterBlockSize) {

        public Result {
            Objects.requireNonNull(
                    delta,
                    "delta");

            delta =
                    delta.clone();
        }

        @Override
        public double[] delta() {
            return delta.clone();
        }
    }
}