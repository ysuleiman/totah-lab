package totah.lab.prometheus.neural;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Experimental generic block-diagonal KFAC updates for FermiNet-v1.
 * Exact structured SR remains the production default.
 */
public final class FermiNetKfacOptimizer {

    private final int observationParallelism;
    private FermiNetKfacState curvatureState = new FermiNetKfacState();

    public FermiNetKfacOptimizer() {
        this(1);
    }

    public FermiNetKfacOptimizer(int observationParallelism) {
        if (observationParallelism < 1) {
            throw new IllegalArgumentException("invalid KFAC observation parallelism");
        }
        this.observationParallelism = observationParallelism;
    }

    public synchronized Result oneIteration(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples,
            Configuration configuration) {
        return oneIteration(state, samples, null, configuration);
    }

    synchronized Result oneIteration(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples,
            FermiNetKnownLocalEnergies knownLocalEnergies,
            Configuration configuration) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(configuration, "configuration");
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("empty FermiNet KFAC sample set");
        }
        if (knownLocalEnergies != null) {
            knownLocalEnergies.validate(state, samples);
        }

        long totalStarted = System.nanoTime();
        long statisticsStarted = System.nanoTime();
        try (FermiNetStructuredSrObservationFile observations =
                     knownLocalEnergies == null
                             ? FermiNetStructuredSrObservationFile.buildParallel(
                                     state, samples,
                                     observationParallelism)
                             : FermiNetStructuredSrObservationFile.buildParallel(
                                     state, samples, knownLocalEnergies,
                                     observationParallelism)) {
            long statisticsNanos = System.nanoTime() - statisticsStarted;

            long gradientStarted = System.nanoTime();
            double[] gradient = FermiNetStructuredSampleSpaceSrSolver
                    .energyGradient(observations);
            long gradientNanos = System.nanoTime() - gradientStarted;
            requireFinite(gradient, "KFAC energy gradient");

            long curvatureStarted = System.nanoTime();
            CurvatureUpdate curvature = updateCurvature(
                    observations, configuration, curvatureState);
            long curvatureNanos = System.nanoTime() - curvatureStarted;

            long preconditionStarted = System.nanoTime();
            double[] direction = precondition(
                    observations.schema(), gradient, curvature.state(),
                    configuration.damping());
            double directionNorm = norm(direction);
            double fisherSquared = fisherSquaredNorm(
                    observations.schema(), direction, curvature.state());
            boolean fisherRescaled = configuration.normConstraint() > 0.0
                    && fisherSquared > configuration.normConstraint();
            if (fisherRescaled) {
                scale(direction, Math.sqrt(
                        configuration.normConstraint() / fisherSquared));
                fisherSquared = configuration.normConstraint();
            }

            double[] update = direction.clone();
            scale(update, configuration.learningRate());
            double rawUpdateNorm = norm(update);
            boolean maxUpdateRescaled = rawUpdateNorm
                    > configuration.maxUpdateNorm();
            if (maxUpdateRescaled) {
                scale(update, configuration.maxUpdateNorm() / rawUpdateNorm);
            }
            double appliedUpdateNorm = norm(update);
            long preconditionNanos = System.nanoTime() - preconditionStarted;

            double[] next = state.parameterArray();
            for (int i = 0; i < next.length; i++) {
                next[i] += update[i];
            }
            requireFinite(next, "KFAC updated parameters");
            FermiNetV1State nextState = state.withParameters(next);
            curvatureState = curvature.state();

            long totalNanos = System.nanoTime() - totalStarted;
            return new Result(
                    nextState,
                    meanEnergy(observations),
                    norm(gradient),
                    directionNorm,
                    rawUpdateNorm,
                    appliedUpdateNorm,
                    fisherSquared,
                    fisherRescaled,
                    maxUpdateRescaled,
                    true,
                    curvature.factorizationUpdated(),
                    curvature.denseBlocks(),
                    curvature.diagonalBlocks(),
                    observations.neuralEvaluations(),
                    gradient,
                    update,
                    curvatureState,
                    new Timing(
                            statisticsNanos,
                            gradientNanos,
                            curvatureNanos,
                            preconditionNanos,
                            totalNanos));
        } catch (IOException exception) {
            throw new UncheckedIOException("FermiNet KFAC observation I/O failed", exception);
        }
    }

    public synchronized FermiNetKfacState state() {
        return curvatureState;
    }

    private static CurvatureUpdate updateCurvature(
            FermiNetStructuredSrObservationFile observations,
            Configuration configuration,
            FermiNetKfacState previous)
            throws IOException {
        Map<String, FermiNetKfacState.DenseBlock> dense = new LinkedHashMap<>();
        Map<String, FermiNetKfacState.DiagonalBlock> diagonal =
                new LinkedHashMap<>();
        boolean refreshFactorizations = previous.iteration() == 0
                || previous.iteration() % configuration.inverseUpdatePeriod() == 0;
        double[] weights = normalizedWeights(observations);
        int denseCount = 0;
        int diagonalCount = 0;

        for (FermiNetStructuredSrStatistics.Family family
                : observations.schema().families()) {
            double[] statistics = observations.readFamily(family);
            if (family.kind() == FermiNetStructuredSrStatistics.Kind.DENSE_WEIGHT) {
                double[] inputBatch = denseFactor(
                        statistics, family, observations.sampleCount(), weights, true);
                double[] outputBatch = denseFactor(
                        statistics, family, observations.sampleCount(), weights, false);
                FermiNetKfacState.DenseBlock old =
                        previous.denseBlocks().get(family.blockName());
                double[] input = old == null
                        ? inputBatch
                        : ema(old.inputFactor(), inputBatch,
                                configuration.curvatureEma());
                double[] output = old == null
                        ? outputBatch
                        : ema(old.outputFactor(), outputBatch,
                                configuration.curvatureEma());

                double[] inputCholesky;
                double[] outputCholesky;
                int factorizationIteration;
                if (old == null || refreshFactorizations) {
                    /*
                     * KFAC-v1 uses symmetric factored damping:
                     *   A_d = A + sqrt(lambda) I
                     *   G_d = G + sqrt(lambda) I.
                     * The Kronecker product therefore includes lambda I plus
                     * the documented cross terms sqrt(lambda)(A kron I + I kron G).
                     */
                    double factorDamping = Math.sqrt(configuration.damping());
                    inputCholesky = choleskyDamped(
                            input, family.inputs(), factorDamping);
                    outputCholesky = choleskyDamped(
                            output, family.outputs(), factorDamping);
                    factorizationIteration = previous.iteration();
                } else {
                    inputCholesky = old.dampedInputCholesky();
                    outputCholesky = old.dampedOutputCholesky();
                    factorizationIteration = old.factorizationIteration();
                }
                dense.put(family.blockName(), new FermiNetKfacState.DenseBlock(
                        family.inputs(), family.outputs(), input, output,
                        inputCholesky, outputCholesky, factorizationIteration));
                denseCount++;
            } else {
                int length = family.statisticLength();
                double[] batch = new double[length];
                for (int sample = 0; sample < observations.sampleCount(); sample++) {
                    int base = sample * length;
                    for (int parameter = 0; parameter < length; parameter++) {
                        double value = statistics[base + parameter];
                        batch[parameter] += weights[sample] * value * value;
                    }
                }
                FermiNetKfacState.DiagonalBlock old =
                        previous.diagonalBlocks().get(family.blockName());
                double[] values = old == null
                        ? batch
                        : ema(old.curvature(), batch, configuration.curvatureEma());
                diagonal.put(family.blockName(),
                        new FermiNetKfacState.DiagonalBlock(values));
                diagonalCount++;
            }
        }
        return new CurvatureUpdate(
                new FermiNetKfacState(previous.iteration() + 1, dense, diagonal),
                refreshFactorizations,
                denseCount,
                diagonalCount);
    }

    static double[] denseFactor(
            double[] statistics,
            FermiNetStructuredSrStatistics.Family family,
            int samples,
            double[] weights,
            boolean inputFactor) {
        int dimension = inputFactor ? family.inputs() : family.outputs();
        int occurrenceStride = dimension;
        int familyStride = family.statisticLength();
        int offset = inputFactor ? 0 : family.inputLength();
        double[] factor = new double[Math.multiplyExact(dimension, dimension)];
        for (int sample = 0; sample < samples; sample++) {
            double scale = weights[sample] / family.occurrences();
            int sampleBase = sample * familyStride + offset;
            for (int occurrence = 0;
                 occurrence < family.occurrences();
                 occurrence++) {
                int base = sampleBase + occurrence * occurrenceStride;
                for (int row = 0; row < dimension; row++) {
                    double left = statistics[base + row];
                    for (int column = 0; column <= row; column++) {
                        factor[row * dimension + column] +=
                                scale * left * statistics[base + column];
                    }
                }
            }
        }
        mirror(factor, dimension);
        return factor;
    }

    private static double[] precondition(
            FermiNetStructuredSrStatistics.Schema schema,
            double[] gradient,
            FermiNetKfacState state,
            double damping) {
        double[] direction = new double[gradient.length];
        for (FermiNetStructuredSrStatistics.Family family : schema.families()) {
            FermiNetParameterLayout.Block block =
                    schema.layout().block(family.blockName());
            if (family.kind() == FermiNetStructuredSrStatistics.Kind.EXPLICIT) {
                double[] curvature = state.diagonalBlocks()
                        .get(family.blockName()).curvature();
                for (int i = 0; i < curvature.length; i++) {
                    direction[block.startInclusive() + i] =
                            -gradient[block.startInclusive() + i]
                                    / (curvature[i] + damping);
                }
                continue;
            }

            FermiNetKfacState.DenseBlock factors =
                    state.denseBlocks().get(family.blockName());
            int inputs = family.inputs();
            int outputs = family.outputs();
            double[] blockGradient = new double[Math.multiplyExact(outputs, inputs)];
            System.arraycopy(
                    gradient, block.startInclusive(), blockGradient, 0,
                    blockGradient.length);
            double[] solved = preconditionDense(
                    blockGradient, inputs, outputs,
                    factors.dampedInputCholesky(),
                    factors.dampedOutputCholesky());
            for (int i = 0; i < solved.length; i++) {
                direction[block.startInclusive() + i] = -solved[i];
            }
        }
        requireFinite(direction, "KFAC preconditioned direction");
        return direction;
    }

    /** Applies G^-1 Grad A^-1 to an output-major J-by-I gradient matrix. */
    static double[] preconditionDense(
            double[] gradient,
            int inputs,
            int outputs,
            double[] inputCholesky,
            double[] outputCholesky) {
        if (gradient.length != Math.multiplyExact(inputs, outputs)) {
            throw new IllegalArgumentException("dense KFAC gradient dimension mismatch");
        }
        double[] leftSolved = new double[gradient.length];
        double[] rhs = new double[outputs];
        for (int input = 0; input < inputs; input++) {
            for (int output = 0; output < outputs; output++) {
                rhs[output] = gradient[output * inputs + input];
            }
            double[] solved = solveCholesky(outputCholesky, rhs, outputs);
            for (int output = 0; output < outputs; output++) {
                leftSolved[output * inputs + input] = solved[output];
            }
        }
        double[] result = new double[gradient.length];
        rhs = new double[inputs];
        for (int output = 0; output < outputs; output++) {
            System.arraycopy(leftSolved, output * inputs, rhs, 0, inputs);
            double[] solved = solveCholesky(inputCholesky, rhs, inputs);
            System.arraycopy(solved, 0, result, output * inputs, inputs);
        }
        return result;
    }

    private static double fisherSquaredNorm(
            FermiNetStructuredSrStatistics.Schema schema,
            double[] direction,
            FermiNetKfacState state) {
        double result = 0.0;
        for (FermiNetStructuredSrStatistics.Family family : schema.families()) {
            FermiNetParameterLayout.Block block =
                    schema.layout().block(family.blockName());
            if (family.kind() == FermiNetStructuredSrStatistics.Kind.EXPLICIT) {
                double[] curvature = state.diagonalBlocks()
                        .get(family.blockName()).curvature();
                for (int i = 0; i < curvature.length; i++) {
                    double value = direction[block.startInclusive() + i];
                    result += curvature[i] * value * value;
                }
                continue;
            }
            FermiNetKfacState.DenseBlock factors =
                    state.denseBlocks().get(family.blockName());
            int inputs = family.inputs();
            int outputs = family.outputs();
            double[] rightProduct = new double[Math.multiplyExact(outputs, inputs)];
            for (int output = 0; output < outputs; output++) {
                int row = block.startInclusive() + output * inputs;
                int productRow = output * inputs;
                for (int rightInput = 0; rightInput < inputs; rightInput++) {
                    double value = 0.0;
                    for (int leftInput = 0; leftInput < inputs; leftInput++) {
                        value += direction[row + leftInput]
                                * factors.inputFactor()[
                                        leftInput * inputs + rightInput];
                    }
                    rightProduct[productRow + rightInput] = value;
                }
            }
            for (int outputLeft = 0; outputLeft < outputs; outputLeft++) {
                for (int outputRight = 0; outputRight < outputs; outputRight++) {
                    double g = factors.outputFactor()[
                            outputLeft * outputs + outputRight];
                    if (g == 0.0) {
                        continue;
                    }
                    int productRow = outputLeft * inputs;
                    int directionRow = block.startInclusive()
                            + outputRight * inputs;
                    for (int input = 0; input < inputs; input++) {
                        result += g * rightProduct[productRow + input]
                                * direction[directionRow + input];
                    }
                }
            }
        }
        if (!Double.isFinite(result) || result < -1.0e-10) {
            throw new IllegalArgumentException("invalid KFAC Fisher norm");
        }
        return Math.max(0.0, result);
    }

    private static double[] normalizedWeights(
            FermiNetStructuredSrObservationFile observations) {
        double sum = 0.0;
        for (int i = 0; i < observations.sampleCount(); i++) {
            sum += observations.weight(i);
        }
        if (!(sum > 0.0) || !Double.isFinite(sum)) {
            throw new IllegalArgumentException("invalid KFAC weights");
        }
        double[] result = new double[observations.sampleCount()];
        for (int i = 0; i < result.length; i++) {
            result[i] = observations.weight(i) / sum;
        }
        return result;
    }

    private static double meanEnergy(
            FermiNetStructuredSrObservationFile observations) {
        double sumWeight = 0.0;
        double sumEnergy = 0.0;
        for (int i = 0; i < observations.sampleCount(); i++) {
            sumWeight += observations.weight(i);
            sumEnergy += observations.weight(i) * observations.localEnergyHartree(i);
        }
        return sumEnergy / sumWeight;
    }

    static double[] ema(double[] old, double[] batch, double decay) {
        double[] result = new double[batch.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = decay * old[i] + (1.0 - decay) * batch[i];
        }
        return result;
    }

    static double[] choleskyDamped(double[] matrix, int size, double damping) {
        double[] lower = matrix.clone();
        for (int i = 0; i < size; i++) {
            lower[i * size + i] += damping;
        }
        for (int row = 0; row < size; row++) {
            for (int column = 0; column <= row; column++) {
                double value = lower[row * size + column];
                for (int k = 0; k < column; k++) {
                    value -= lower[row * size + k]
                            * lower[column * size + k];
                }
                if (row == column) {
                    if (!(value > 0.0) || !Double.isFinite(value)) {
                        throw new IllegalArgumentException(
                                "non-SPD damped KFAC factor");
                    }
                    lower[row * size + column] = Math.sqrt(value);
                } else {
                    lower[row * size + column] =
                            value / lower[column * size + column];
                }
            }
            for (int column = row + 1; column < size; column++) {
                lower[row * size + column] = 0.0;
            }
        }
        return lower;
    }

    static double[] solveCholesky(double[] lower, double[] rhs, int size) {
        double[] result = rhs.clone();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < row; column++) {
                result[row] -= lower[row * size + column] * result[column];
            }
            result[row] /= lower[row * size + row];
        }
        for (int row = size - 1; row >= 0; row--) {
            for (int column = row + 1; column < size; column++) {
                result[row] -= lower[column * size + row] * result[column];
            }
            result[row] /= lower[row * size + row];
        }
        return result;
    }

    private static void mirror(double[] matrix, int size) {
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < row; column++) {
                matrix[column * size + row] = matrix[row * size + column];
            }
        }
    }

    private static void scale(double[] values, double scale) {
        for (int i = 0; i < values.length; i++) {
            values[i] *= scale;
        }
    }

    private static double norm(double[] values) {
        double scale = 0.0;
        double sum = 1.0;
        for (double value : values) {
            double absolute = Math.abs(value);
            if (absolute == 0.0) {
                continue;
            }
            if (scale < absolute) {
                double ratio = scale / absolute;
                sum = 1.0 + sum * ratio * ratio;
                scale = absolute;
            } else {
                double ratio = absolute / scale;
                sum += ratio * ratio;
            }
        }
        return scale == 0.0 ? 0.0 : scale * Math.sqrt(sum);
    }

    private static void requireFinite(double[] values, String label) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("non-finite " + label);
            }
        }
    }

    public record Configuration(
            double learningRate,
            double damping,
            double curvatureEma,
            int inverseUpdatePeriod,
            double maxUpdateNorm,
            double normConstraint) {
        public Configuration {
            if (!(learningRate > 0.0) || !Double.isFinite(learningRate)
                    || !(damping > 0.0) || !Double.isFinite(damping)
                    || curvatureEma < 0.0 || curvatureEma >= 1.0
                    || !Double.isFinite(curvatureEma)
                    || inverseUpdatePeriod < 1
                    || !(maxUpdateNorm > 0.0) || !Double.isFinite(maxUpdateNorm)
                    || normConstraint < 0.0 || !Double.isFinite(normConstraint)) {
                throw new IllegalArgumentException("invalid FermiNet KFAC configuration");
            }
        }
    }

    public record Timing(
            long statisticsNanos,
            long gradientNanos,
            long curvatureNanos,
            long preconditionNanos,
            long totalNanos) {}

    public record Result(
            FermiNetV1State state,
            double initialEnergyHartree,
            double gradientNorm,
            double preconditionedDirectionNorm,
            double rawUpdateNorm,
            double appliedUpdateNorm,
            double approximateFisherSquaredNorm,
            boolean fisherNormRescaled,
            boolean maxUpdateRescaled,
            boolean curvatureUpdated,
            boolean factorDecompositionUpdated,
            int denseKfacBlockCount,
            int diagonalBlockCount,
            long statisticsEvaluationCount,
            double[] energyGradient,
            double[] appliedUpdate,
            FermiNetKfacState curvatureState,
            Timing timing) {
        public Result {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(curvatureState, "curvatureState");
            Objects.requireNonNull(timing, "timing");
            energyGradient = energyGradient.clone();
            appliedUpdate = appliedUpdate.clone();
        }
        @Override public double[] energyGradient() { return energyGradient.clone(); }
        @Override public double[] appliedUpdate() { return appliedUpdate.clone(); }
    }

    private record CurvatureUpdate(
            FermiNetKfacState state,
            boolean factorizationUpdated,
            int denseBlocks,
            int diagonalBlocks) {}
}
