package totah.lab.prometheus.neural;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Exact sample-space SR using generic structured FermiNet sufficient statistics. */
final class FermiNetStructuredSampleSpaceSrSolver {

    private static final int MAX_WORKERS = 12;

    Result solve(
            FermiNetStructuredSrObservationFile observations,
            double damping)
            throws IOException {

        long totalStarted = System.nanoTime();
        if (!(damping > 0.0) || !Double.isFinite(damping)) {
            throw new IllegalArgumentException("invalid SR damping");
        }

        int samples = observations.sampleCount();
        int parameters = observations.parameterCount();
        double weightSum = 0.0;
        double weightedEnergy = 0.0;
        for (int sample = 0; sample < samples; sample++) {
            double weight = observations.weight(sample);
            if (!Double.isFinite(weight) || weight < 0.0) {
                throw new IllegalArgumentException("invalid SR sample weight");
            }
            weightSum += weight;
            weightedEnergy += weight * observations.localEnergyHartree(sample);
        }
        if (!(weightSum > 0.0) || !Double.isFinite(weightSum)) {
            throw new IllegalArgumentException("non-positive SR total weight");
        }

        double meanEnergy = weightedEnergy / weightSum;
        double[] weight = new double[samples];
        double[] sqrtWeight = new double[samples];
        double[] q = new double[samples];
        for (int sample = 0; sample < samples; sample++) {
            weight[sample] = observations.weight(sample) / weightSum;
            sqrtWeight[sample] = Math.sqrt(weight[sample]);
            q[sample] = 2.0 * sqrtWeight[sample]
                    * (observations.localEnergyHartree(sample) - meanEnergy);
        }

        double[] kernel = new double[Math.multiplyExact(samples, samples)];
        int workers = Math.min(MAX_WORKERS, samples);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        long gramStarted = System.nanoTime();
        long familyReadNanos = 0L;
        long familyArithmeticNanos = 0L;

        try {
            for (FermiNetStructuredSrStatistics.Family family
                    : observations.schema().families()) {
                long started = System.nanoTime();
                double[] statistics = observations.readFamily(family);
                familyReadNanos += System.nanoTime() - started;
                started = System.nanoTime();
                accumulateFamilyKernel(
                        kernel,
                        statistics,
                        family,
                        samples,
                        workers,
                        executor);
                familyArithmeticNanos += System.nanoTime() - started;
            }
        } finally {
            executor.shutdownNow();
        }

        double[] weightedKernelMean = new double[samples];
        double totalKernelMean = 0.0;
        for (int row = 0; row < samples; row++) {
            double mean = 0.0;
            for (int column = 0; column < samples; column++) {
                mean += weight[column] * kernel[row * samples + column];
            }
            weightedKernelMean[row] = mean;
            totalKernelMean += weight[row] * mean;
        }

        double[] gram = new double[kernel.length];
        for (int row = 0; row < samples; row++) {
            for (int column = 0; column < samples; column++) {
                double centered = kernel[row * samples + column]
                        - weightedKernelMean[row]
                        - weightedKernelMean[column]
                        + totalKernelMean;
                gram[row * samples + column] =
                        sqrtWeight[row] * sqrtWeight[column] * centered;
            }
            gram[row * samples + row] += damping;
        }
        long gramNanos = System.nanoTime() - gramStarted;

        long solveStarted = System.nanoTime();
        double[] y = choleskySolve(gram, q, samples);
        long solveNanos = System.nanoTime() - solveStarted;

        double sumY = 0.0;
        double sumQ = 0.0;
        for (int sample = 0; sample < samples; sample++) {
            sumY += sqrtWeight[sample] * y[sample];
            sumQ += sqrtWeight[sample] * q[sample];
        }
        double[] deltaCoefficients = new double[samples];
        double[] gradientCoefficients = new double[samples];
        for (int sample = 0; sample < samples; sample++) {
            deltaCoefficients[sample] =
                    -(sqrtWeight[sample] * y[sample] - weight[sample] * sumY);
            gradientCoefficients[sample] =
                    sqrtWeight[sample] * q[sample] - weight[sample] * sumQ;
        }

        long reconstructionStarted = System.nanoTime();
        double[] delta = new double[parameters];
        double[] gradient = new double[parameters];
        long reconstructionReadNanos = 0L;
        long reconstructionArithmeticNanos = 0L;
        for (FermiNetStructuredSrStatistics.Family family
                : observations.schema().families()) {
            long started = System.nanoTime();
            double[] statistics = observations.readFamily(family);
            reconstructionReadNanos += System.nanoTime() - started;
            started = System.nanoTime();
            reconstructFamilyPair(
                    statistics,
                    family,
                    observations.schema().layout().block(family.blockName()),
                    samples,
                    deltaCoefficients,
                    gradientCoefficients,
                    delta,
                    gradient);
            reconstructionArithmeticNanos += System.nanoTime() - started;
        }
        long reconstructionNanos = System.nanoTime() - reconstructionStarted;

        double[] residual = multiply(gram, y, samples);
        for (int i = 0; i < samples; i++) {
            residual[i] -= q[i];
        }
        double absoluteResidual = norm(residual);
        double qNorm = norm(q);
        long totalNanos = System.nanoTime() - totalStarted;

        System.out.printf("""
                FERMINET_STRUCTURED_SAMPLE_SPACE_SR_TIMING
                  samples=%d
                  parameters=%d
                  gram_workers=%d
                  statistics_spool_bytes=%d
                  statistics_generation_sum_ms=%.3f
                  statistics_write_sum_ms=%.3f
                  structured_gram_ms=%.3f
                  structured_gram_read_ms=%.3f
                  structured_gram_arithmetic_ms=%.3f
                  linear_solve_ms=%.3f
                  structured_reconstruction_ms=%.3f
                  structured_reconstruction_read_ms=%.3f
                  structured_reconstruction_arithmetic_ms=%.3f
                  total_solver_ms=%.3f

                """,
                samples,
                parameters,
                workers,
                observations.spoolBytes(),
                millis(observations.generationNanos()),
                millis(observations.writeNanos()),
                millis(gramNanos),
                millis(familyReadNanos),
                millis(familyArithmeticNanos),
                millis(solveNanos),
                millis(reconstructionNanos),
                millis(reconstructionReadNanos),
                millis(reconstructionArithmeticNanos),
                millis(totalNanos));

        return new Result(
                delta,
                gradient,
                gram,
                meanEnergy,
                absoluteResidual,
                qNorm == 0.0 ? 0.0 : absoluteResidual / qNorm,
                1,
                observations.spoolBytes(),
                totalNanos);
    }

    static double[] materializeSampleFamily(
            double[] statistics,
            FermiNetStructuredSrStatistics.Family family,
            FermiNetParameterLayout.Block block,
            int samples,
            int sample) {
        double[] coefficients = new double[samples];
        coefficients[sample] = 1.0;
        double[] all = new double[block.endExclusive()];
        reconstructFamilyPair(
                statistics,
                family,
                block,
                samples,
                coefficients,
                new double[samples],
                all,
                new double[block.endExclusive()]);
        return java.util.Arrays.copyOfRange(
                all,
                block.startInclusive(),
                block.endExclusive());
    }

    static double familyDot(
            double[] statistics,
            FermiNetStructuredSrStatistics.Family family,
            int leftSample,
            int rightSample) {
        return family.kind()
                == FermiNetStructuredSrStatistics.Kind.DENSE_WEIGHT
                ? denseDot(statistics, family, leftSample, rightSample)
                : explicitDot(statistics, family, leftSample, rightSample);
    }

    private static void accumulateFamilyKernel(
            double[] kernel,
            double[] statistics,
            FermiNetStructuredSrStatistics.Family family,
            int samples,
            int workers,
            ExecutorService executor) {
        if (useMaterializedDenseKernel(family)) {
            accumulateMaterializedDenseKernel(
                    kernel, statistics, family, samples, workers, executor);
            return;
        }
        accumulateFactorizedFamilyKernel(
                kernel, statistics, family, samples, workers, executor);
    }

    private static boolean useMaterializedDenseKernel(
            FermiNetStructuredSrStatistics.Family family) {
        if (family.kind() != FermiNetStructuredSrStatistics.Kind.DENSE_WEIGHT) {
            return false;
        }
        long materializedDot = Math.multiplyExact(
                (long) family.inputs(), family.outputs());
        long factorizedDot = Math.multiplyExact(
                Math.multiplyExact(
                        (long) family.occurrences(), family.occurrences()),
                Math.addExact(family.inputs(), family.outputs()));
        return materializedDot * 4L < factorizedDot;
    }

    private static void accumulateMaterializedDenseKernel(
            double[] kernel,
            double[] statistics,
            FermiNetStructuredSrStatistics.Family family,
            int samples,
            int workers,
            ExecutorService executor) {
        int inputs = family.inputs();
        int outputs = family.outputs();
        int occurrences = family.occurrences();
        int stride = family.statisticLength();
        int gradientLength = Math.multiplyExact(inputs, outputs);
        double[] gradients = new double[Math.multiplyExact(samples, gradientLength)];

        List<Future<?>> futures = new ArrayList<>(workers);
        for (int worker = 0; worker < workers; worker++) {
            int start = samples * worker / workers;
            int end = samples * (worker + 1) / workers;
            futures.add(executor.submit(() -> {
                for (int sample = start; sample < end; sample++) {
                    int statisticBase = sample * stride;
                    int adjointBase = statisticBase + family.inputLength();
                    int gradientBase = sample * gradientLength;
                    for (int output = 0; output < outputs; output++) {
                        int outputBase = gradientBase + output * inputs;
                        for (int input = 0; input < inputs; input++) {
                            double value = 0.0;
                            for (int occurrence = 0;
                                 occurrence < occurrences;
                                 occurrence++) {
                                value += statistics[adjointBase
                                                + occurrence * outputs
                                                + output]
                                        * statistics[statisticBase
                                                + occurrence * inputs
                                                + input];
                            }
                            gradients[outputBase + input] = value;
                        }
                    }
                }
            }));
        }
        await(futures);

        futures.clear();
        for (int worker = 0; worker < workers; worker++) {
            int start = rowBoundary(worker, workers, samples);
            int end = rowBoundary(worker + 1, workers, samples);
            futures.add(executor.submit(() -> {
                for (int row = start; row < end; row++) {
                    int left = row * gradientLength;
                    for (int column = 0; column <= row; column++) {
                        int right = column * gradientLength;
                        double value = 0.0;
                        for (int parameter = 0;
                             parameter < gradientLength;
                             parameter++) {
                            value += gradients[left + parameter]
                                    * gradients[right + parameter];
                        }
                        kernel[row * samples + column] += value;
                    }
                }
            }));
        }
        await(futures);
        mirrorLowerTriangle(kernel, samples);
    }

    private static void accumulateFactorizedFamilyKernel(
            double[] kernel,
            double[] statistics,
            FermiNetStructuredSrStatistics.Family family,
            int samples,
            int workers,
            ExecutorService executor) {
        List<Future<?>> futures = new ArrayList<>(workers);
        for (int worker = 0; worker < workers; worker++) {
            int start = rowBoundary(worker, workers, samples);
            int end = rowBoundary(worker + 1, workers, samples);
            futures.add(executor.submit(() -> {
                for (int row = start; row < end; row++) {
                    for (int column = 0; column <= row; column++) {
                        double value = family.kind()
                                == FermiNetStructuredSrStatistics.Kind.DENSE_WEIGHT
                                ? denseDot(statistics, family, row, column)
                                : explicitDot(statistics, family, row, column);
                        kernel[row * samples + column] += value;
                    }
                }
            }));
        }
        await(futures);
        mirrorLowerTriangle(kernel, samples);
    }

    private static void mirrorLowerTriangle(double[] kernel, int samples) {
        for (int row = 0; row < samples; row++) {
            for (int column = 0; column < row; column++) {
                kernel[column * samples + row] = kernel[row * samples + column];
            }
        }
    }

    private static double denseDot(
            double[] data,
            FermiNetStructuredSrStatistics.Family family,
            int leftSample,
            int rightSample) {
        int stride = family.statisticLength();
        int left = leftSample * stride;
        int right = rightSample * stride;
        int leftAdj = left + family.inputLength();
        int rightAdj = right + family.inputLength();
        double value = 0.0;
        for (int a = 0; a < family.occurrences(); a++) {
            for (int b = 0; b < family.occurrences(); b++) {
                double inputDot = 0.0;
                for (int k = 0; k < family.inputs(); k++) {
                    inputDot += data[left + a * family.inputs() + k]
                            * data[right + b * family.inputs() + k];
                }
                double adjointDot = 0.0;
                for (int o = 0; o < family.outputs(); o++) {
                    adjointDot += data[leftAdj + a * family.outputs() + o]
                            * data[rightAdj + b * family.outputs() + o];
                }
                value += inputDot * adjointDot;
            }
        }
        return value;
    }

    private static double explicitDot(
            double[] data,
            FermiNetStructuredSrStatistics.Family family,
            int leftSample,
            int rightSample) {
        int length = family.statisticLength();
        int left = leftSample * length;
        int right = rightSample * length;
        double value = 0.0;
        for (int i = 0; i < length; i++) {
            value += data[left + i] * data[right + i];
        }
        return value;
    }

    private static void reconstructFamilyPair(
            double[] statistics,
            FermiNetStructuredSrStatistics.Family family,
            FermiNetParameterLayout.Block block,
            int samples,
            double[] firstCoefficients,
            double[] secondCoefficients,
            double[] firstDestination,
            double[] secondDestination) {
        if (family.kind() == FermiNetStructuredSrStatistics.Kind.EXPLICIT) {
            int length = family.statisticLength();
            for (int parameter = 0; parameter < length; parameter++) {
                double first = 0.0;
                double second = 0.0;
                for (int sample = 0; sample < samples; sample++) {
                    double sampleGradient =
                            statistics[sample * length + parameter];
                    first += firstCoefficients[sample] * sampleGradient;
                    second += secondCoefficients[sample] * sampleGradient;
                }
                firstDestination[block.startInclusive() + parameter] = first;
                secondDestination[block.startInclusive() + parameter] = second;
            }
            return;
        }

        int stride = family.statisticLength();
        for (int output = 0; output < family.outputs(); output++) {
            for (int input = 0; input < family.inputs(); input++) {
                double first = 0.0;
                double second = 0.0;
                for (int sample = 0; sample < samples; sample++) {
                    int row = sample * stride;
                    int adj = row + family.inputLength();
                    double sampleGradient = 0.0;
                    for (int occurrence = 0;
                         occurrence < family.occurrences();
                         occurrence++) {
                        sampleGradient +=
                                statistics[adj
                                        + occurrence * family.outputs()
                                        + output]
                                        * statistics[row
                                        + occurrence * family.inputs()
                                        + input];
                    }
                    first += firstCoefficients[sample] * sampleGradient;
                    second += secondCoefficients[sample] * sampleGradient;
                }
                firstDestination[block.startInclusive()
                        + output * family.inputs()
                        + input] = first;
                secondDestination[block.startInclusive()
                        + output * family.inputs()
                        + input] = second;
            }
        }
    }

    private static int rowBoundary(int worker, int workers, int rows) {
        long total = (long) rows * (rows + 1L) / 2L;
        long target = total * worker / workers;
        int low = 0;
        int high = rows;
        while (low < high) {
            int middle = (low + high) >>> 1;
            long elements = (long) middle * (middle + 1L) / 2L;
            if (elements < target) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static void await(List<Future<?>> futures) {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "interrupted structured Gram", exception);
            } catch (ExecutionException exception) {
                throw new IllegalStateException(
                        "failed structured Gram", exception.getCause());
            }
        }
    }

    private static double[] choleskySolve(double[] matrix, double[] rhs, int n) {
        double[] lower = matrix.clone();
        for (int row = 0; row < n; row++) {
            for (int column = 0; column <= row; column++) {
                double value = lower[row * n + column];
                for (int k = 0; k < column; k++) {
                    value -= lower[row * n + k] * lower[column * n + k];
                }
                if (row == column) {
                    if (!(value > 0.0) || !Double.isFinite(value)) {
                        throw new IllegalArgumentException(
                                "structured SR matrix is not SPD at " + row);
                    }
                    lower[row * n + column] = Math.sqrt(value);
                } else {
                    lower[row * n + column] =
                            value / lower[column * n + column];
                }
            }
        }
        double[] forward = rhs.clone();
        for (int row = 0; row < n; row++) {
            double value = forward[row];
            for (int column = 0; column < row; column++) {
                value -= lower[row * n + column] * forward[column];
            }
            forward[row] = value / lower[row * n + row];
        }
        double[] solution = forward.clone();
        for (int row = n - 1; row >= 0; row--) {
            double value = solution[row];
            for (int column = row + 1; column < n; column++) {
                value -= lower[column * n + row] * solution[column];
            }
            solution[row] = value / lower[row * n + row];
        }
        return solution;
    }

    private static double[] multiply(double[] matrix, double[] vector, int n) {
        double[] result = new double[n];
        for (int row = 0; row < n; row++) {
            for (int column = 0; column < n; column++) {
                result[row] += matrix[row * n + column] * vector[column];
            }
        }
        return result;
    }

    private static double norm(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }

    private static double millis(long nanos) {
        return nanos / 1.0e6;
    }

    record Result(
            double[] delta,
            double[] energyGradient,
            double[] centeredDampedGram,
            double meanEnergyHartree,
            double absoluteSampleSpaceResidual,
            double relativeSampleSpaceResidual,
            int linearSolveCount,
            long statisticsSpoolBytes,
            long totalSolverNanos) {

        Result {
            delta = delta.clone();
            energyGradient = energyGradient.clone();
            centeredDampedGram = centeredDampedGram.clone();
        }

        @Override
        public double[] delta() {
            return delta.clone();
        }

        @Override
        public double[] energyGradient() {
            return energyGradient.clone();
        }

        @Override
        public double[] centeredDampedGram() {
            return centeredDampedGram.clone();
        }
    }
}
