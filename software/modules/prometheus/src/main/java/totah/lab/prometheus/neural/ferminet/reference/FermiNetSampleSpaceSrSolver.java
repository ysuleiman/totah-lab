package totah.lab.prometheus.neural.ferminet.reference;

import totah.lab.prometheus.neural.ferminet.runtime.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import totah.lab.prometheus.neural.ferminet.reference.FermiNetSrObservationFile;

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
    private static final int DEFAULT_GRAM_WORKERS = 12;

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

        long totalStarted = System.nanoTime();

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

        /*
         * ------------------------------------------------------------
         * PHASE 1: weights, mean energy, q construction
         * ------------------------------------------------------------
         */

        long setupStarted = System.nanoTime();

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

        double[] parameterMeans =
                new double[maximumBlock];

        int gramWorkers =
                Math.min(
                        DEFAULT_GRAM_WORKERS,
                        samples);

        long setupNanos =
                System.nanoTime() - setupStarted;

        /*
         * ------------------------------------------------------------
         * PHASE 2: first derivative sweep / Gram construction
         * ------------------------------------------------------------
         *
         * Separate file-read time from arithmetic time.
         */

        long gramTotalStarted =
                System.nanoTime();

        long gramReadNanos = 0L;
        long gramArithmeticNanos = 0L;
        long gramDerivativeValuesRead = 0L;
        int gramBlocksRead = 0;

        ExecutorService gramExecutor =
                Executors.newFixedThreadPool(gramWorkers);

        try {
            for (int parameterStart = 0;
                 parameterStart < parameters;
                 parameterStart += maximumBlock) {

            int length =
                    Math.min(
                            maximumBlock,
                            parameters - parameterStart);

            long readStarted =
                    System.nanoTime();

            observations.readParameterBlock(
                    parameterStart,
                    length,
                    block);

            gramReadNanos +=
                    System.nanoTime() - readStarted;

            gramBlocksRead++;

            gramDerivativeValuesRead +=
                    (long) samples
                            * length;

            long arithmeticStarted =
                    System.nanoTime();

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

                    parameterMeans[local] = mean;
                }

                for (int local = 0;
                     local < length;
                     local++) {

                    double mean = parameterMeans[local];

                    for (int sample = 0;
                         sample < samples;
                         sample++) {

                        int index = sample * length + local;

                        block[index] =
                                sqrtWeight[sample]
                                        * (block[index] - mean);
                    }
                }

                accumulateGramRows(
                        gramExecutor,
                        gramWorkers,
                        gram,
                        block,
                        samples,
                        length);

                gramArithmeticNanos +=
                        System.nanoTime() - arithmeticStarted;
            }
        } finally {
            gramExecutor.shutdownNow();
        }

        /*
         * Damping + symmetric upper triangle.
         */
        long gramFinalizeStarted =
                System.nanoTime();

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

        long gramFinalizeNanos =
                System.nanoTime() - gramFinalizeStarted;

        long gramTotalNanos =
                System.nanoTime() - gramTotalStarted;

        /*
         * ------------------------------------------------------------
         * PHASE 3: N x N Cholesky solve
         * ------------------------------------------------------------
         */

        long linearSolveStarted =
                System.nanoTime();

        double[] y =
                choleskySolve(
                        gram,
                        q,
                        samples);

        long linearSolveNanos =
                System.nanoTime() - linearSolveStarted;

        /*
         * ------------------------------------------------------------
         * PHASE 4: second derivative sweep / delta and gradient reconstruction
         * ------------------------------------------------------------
         */

        long reconstructionTotalStarted =
                System.nanoTime();

        double[] delta =
                new double[parameters];

        double[] energyGradient =
                new double[parameters];

        long reconstructionDerivativeValuesRead = 0L;
        long reconstructionReadNanos = 0L;
        long reconstructionArithmeticNanos = 0L;
        int reconstructionBlocksRead = 0;

        for (int parameterStart = 0;
             parameterStart < parameters;
             parameterStart += maximumBlock) {

            int length =
                    Math.min(
                            maximumBlock,
                            parameters - parameterStart);

            long readStarted =
                    System.nanoTime();

            observations.readParameterBlock(
                    parameterStart,
                    length,
                    block);

            reconstructionReadNanos +=
                    System.nanoTime() - readStarted;

            reconstructionBlocksRead++;

            reconstructionDerivativeValuesRead +=
                    (long) samples
                            * length;

            long arithmeticStarted =
                    System.nanoTime();

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

                double gradientValue =
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

                    gradientValue +=
                            2.0
                                    * normalizedWeight[sample]
                                    * (observations.localEnergyHartree(sample)
                                    - meanEnergy)
                                    * block[sample * length + local];
                }

                delta[parameterStart + local] =
                        -value;

                energyGradient[parameterStart + local] =
                        gradientValue;
            }

            reconstructionArithmeticNanos +=
                    System.nanoTime() - arithmeticStarted;
        }

        long reconstructionTotalNanos =
                System.nanoTime() - reconstructionTotalStarted;

        /*
         * ------------------------------------------------------------
         * PHASE 5: validation / residual diagnostics
         * ------------------------------------------------------------
         */

        long diagnosticsStarted =
                System.nanoTime();

        requireFinite(
                delta,
                "sample-space SR update");

        requireFinite(
                energyGradient,
                "energy gradient");

        double[] residual =
                multiply(
                        gram,
                        y,
                        samples);

        for (int i = 0;
             i < samples;
             i++) {

            residual[i] -=
                    q[i];
        }

        double absoluteResidual =
                norm(residual);

        double qNorm =
                norm(q);

        long diagnosticsNanos =
                System.nanoTime() - diagnosticsStarted;

        long totalNanos =
                System.nanoTime() - totalStarted;

        /*
         * Print once per solve. No logging occurs inside parameter loops.
         */
        System.out.printf("""
                FERMINET_SAMPLE_SPACE_SR_TIMING
                  samples=%d
                  parameters=%d
                  parameter_block_size=%d

                  setup_ms=%.3f

                  gram_total_ms=%.3f
                  gram_read_ms=%.3f
                  gram_arithmetic_ms=%.3f
                  gram_finalize_ms=%.3f
                  gram_workers=%d
                  gram_blocks_read=%d
                  gram_derivative_values_read=%d

                  linear_solve_ms=%.3f

                  reconstruction_total_ms=%.3f
                  reconstruction_read_ms=%.3f
                  reconstruction_arithmetic_ms=%.3f
                  reconstruction_blocks_read=%d
                  reconstruction_derivative_values_read=%d

                  diagnostics_ms=%.3f

                  total_solver_ms=%.3f
                %n""",
                samples,
                parameters,
                parameterBlockSize,

                millis(setupNanos),

                millis(gramTotalNanos),
                millis(gramReadNanos),
                millis(gramArithmeticNanos),
                millis(gramFinalizeNanos),
                gramWorkers,
                gramBlocksRead,
                gramDerivativeValuesRead,

                millis(linearSolveNanos),

                millis(reconstructionTotalNanos),
                millis(reconstructionReadNanos),
                millis(reconstructionArithmeticNanos),
                reconstructionBlocksRead,
                reconstructionDerivativeValuesRead,

                millis(diagnosticsNanos),

                millis(totalNanos));

        return new Result(
                delta,
                energyGradient,
                meanEnergy,
                absoluteResidual,
                qNorm == 0.0
                        ? 0.0
                        : absoluteResidual / qNorm,
                gramDerivativeValuesRead,
                reconstructionDerivativeValuesRead,
                parameterBlockSize);
    }

    private static void accumulateGramRows(
            ExecutorService executor,
            int workers,
            double[] gram,
            double[] block,
            int samples,
            int length)
            throws IOException {

        List<Future<?>> futures = new ArrayList<>(workers);

        long totalElements =
                (long) samples * (samples + 1L) / 2L;

        for (int worker = 0; worker < workers; worker++) {
            int rowStart =
                    rowBoundary(
                            totalElements * worker / workers,
                            samples);
            int rowEnd =
                    rowBoundary(
                            totalElements * (worker + 1L) / workers,
                            samples);

            futures.add(executor.submit(() -> {
                for (int row = rowStart; row < rowEnd; row++) {
                    int gramRow = row * samples;
                    int blockRow = row * length;

                    for (int column = 0; column <= row; column++) {
                        double value = gram[gramRow + column];
                        int blockColumn = column * length;

                        for (int local = 0; local < length; local++) {
                            value +=
                                    block[blockRow + local]
                                            * block[blockColumn + local];
                        }

                        gram[gramRow + column] = value;
                    }
                }
            }));
        }

        try {
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException(
                    "interrupted during parallel Gram construction",
                    exception);
        } catch (ExecutionException exception) {
            throw new IOException(
                    "parallel Gram construction failed",
                    exception.getCause());
        }
    }

    private static int rowBoundary(
            long targetElements,
            int samples) {

        int low = 0;
        int high = samples;

        while (low < high) {
            int middle = (low + high) >>> 1;
            long elements =
                    (long) middle * (middle + 1L) / 2L;

            if (elements < targetElements) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }

        return low;
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

    private static double millis(
            long nanos) {

        return nanos / 1.0e6;
    }

    public record Result(
            double[] delta,
            double[] energyGradient,
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

            Objects.requireNonNull(
                    energyGradient,
                    "energyGradient");

            delta =
                    delta.clone();

            energyGradient =
                    energyGradient.clone();
        }

        @Override
        public double[] delta() {
            return delta.clone();
        }

        @Override
        public double[] energyGradient() {
            return energyGradient.clone();
        }
    }
}
