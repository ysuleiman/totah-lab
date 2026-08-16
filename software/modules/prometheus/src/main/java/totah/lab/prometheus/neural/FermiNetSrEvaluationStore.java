package totah.lab.prometheus.neural;

import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.variational.QuantumCoordinates;

/**
 * Frozen heap-backed cache of the expensive per-sample quantities required by
 * one FermiNet stochastic-reconfiguration iteration.
 *
 * <p>Each non-zero-weight sample is evaluated exactly once. The store retains
 * its weight, local energy, and complete parameter log-derivative vector.
 *
 * <p>Derivatives are stored row-major in one contiguous {@code double[]}:
 * {@code sampleIndex * parameterCount + parameterIndex}.
 *
 * <p>This first implementation is intentionally heap-backed for validating the
 * cached-SR architecture at the current H2O scale. A memory-mapped backing can
 * later implement the same logical contract for larger sample counts.
 */
public final class FermiNetSrEvaluationStore {

    private final int parameterCount;
    private final int sampleCount;
    private final double[] weights;
    private final double[] localEnergiesHartree;
    private final double[] derivatives;
    private final double weightSum;
    private final long neuralEvaluations;

    private FermiNetSrEvaluationStore(
            int parameterCount,
            int sampleCount,
            double[] weights,
            double[] localEnergiesHartree,
            double[] derivatives,
            double weightSum,
            long neuralEvaluations) {
        this.parameterCount = parameterCount;
        this.sampleCount = sampleCount;
        this.weights = weights;
        this.localEnergiesHartree = localEnergiesHartree;
        this.derivatives = derivatives;
        this.weightSum = weightSum;
        this.neuralEvaluations = neuralEvaluations;
    }

    /**
     * Evaluates every non-zero-weight SR sample exactly once and freezes the
     * resulting energy/derivative data.
     */
    public static FermiNetSrEvaluationStore build(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples) {

        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(samples, "samples");

        if (samples.isEmpty()) {
            throw new IllegalArgumentException("empty FermiNet SR sample set");
        }

        int parameters = state.parameterCount();
        int nonZeroSamples = 0;

        for (var sample : samples) {
            Objects.requireNonNull(sample, "sample");
            double weight = sample.weight();

            if (!Double.isFinite(weight) || weight < 0.0) {
                throw new IllegalArgumentException("invalid FermiNet SR sample weight");
            }

            if (weight > 0.0) {
                nonZeroSamples++;
            }
        }

        if (nonZeroSamples == 0) {
            throw new IllegalArgumentException("zero total FermiNet SR sample weight");
        }

        int derivativeElements;
        try {
            derivativeElements = Math.multiplyExact(nonZeroSamples, parameters);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "FermiNet SR derivative store exceeds heap-array indexing",
                    exception);
        }

        double[] weights = new double[nonZeroSamples];
        double[] energies = new double[nonZeroSamples];
        double[] derivatives = new double[derivativeElements];

        double weightSum = 0.0;
        long evaluations = 0L;
        int storedSample = 0;

        for (var sample : samples) {
            if (sample.weight() == 0.0) {
                continue;
            }

            FermiNetV1State.Evaluation evaluation =
                    state.evaluate(sample.coordinates());

            double[] parameterDerivatives =
                    evaluation.parameterLogDerivatives();

            if (parameterDerivatives.length != parameters) {
                throw new IllegalArgumentException(
                        "parameter derivative dimension mismatch");
            }

            requireFinite(parameterDerivatives, "parameter log derivative");

            double localEnergy =
                    FermiNetVmc.localEnergy(
                                    state,
                                    sample.coordinates(),
                                    evaluation)
                            .totalHartree();

            requireFinite(localEnergy, "local energy");

            int offset = Math.multiplyExact(storedSample, parameters);

            System.arraycopy(
                    parameterDerivatives,
                    0,
                    derivatives,
                    offset,
                    parameters);

            weights[storedSample] = sample.weight();
            energies[storedSample] = localEnergy;

            weightSum += sample.weight();
            evaluations++;
            storedSample++;
        }

        if (!(weightSum > 0.0) || !Double.isFinite(weightSum)) {
            throw new IllegalArgumentException(
                    "non-positive FermiNet SR total weight");
        }

        if (storedSample != nonZeroSamples) {
            throw new IllegalStateException(
                    "FermiNet SR stored sample count mismatch");
        }

        return new FermiNetSrEvaluationStore(
                parameters,
                nonZeroSamples,
                weights,
                energies,
                derivatives,
                weightSum,
                evaluations);
    }

    public int parameterCount() {
        return parameterCount;
    }

    public int sampleCount() {
        return sampleCount;
    }

    public double weightSum() {
        return weightSum;
    }

    public long neuralEvaluations() {
        return neuralEvaluations;
    }

    public double weight(int sampleIndex) {
        checkSampleIndex(sampleIndex);
        return weights[sampleIndex];
    }

    public double normalizedWeight(int sampleIndex) {
        checkSampleIndex(sampleIndex);
        return weights[sampleIndex] / weightSum;
    }

    public double localEnergyHartree(int sampleIndex) {
        checkSampleIndex(sampleIndex);
        return localEnergiesHartree[sampleIndex];
    }

    public double parameterLogDerivative(
            int sampleIndex,
            int parameterIndex) {

        checkSampleIndex(sampleIndex);
        checkParameterIndex(parameterIndex);

        return derivatives[
                derivativeOffset(sampleIndex) + parameterIndex];
    }

    /** Verification convenience; hot operator code should not use this. */
    public double[] parameterLogDerivatives(int sampleIndex) {
        checkSampleIndex(sampleIndex);

        double[] result = new double[parameterCount];

        System.arraycopy(
                derivatives,
                derivativeOffset(sampleIndex),
                result,
                0,
                parameterCount);

        return result;
    }

    /** Primitive-array footprint only; object headers/alignment are excluded. */
    public long primitiveStorageBytes() {
        long doubles =
                (long) derivatives.length
                        + weights.length
                        + localEnergiesHartree.length;

        return Math.multiplyExact(doubles, Double.BYTES);
    }

    /*
     * Package-private hot-path access for StoredCenteredCovarianceOperator.
     * The returned storage must be treated as read-only.
     */
    double[] derivativeStorage() {
        return derivatives;
    }

    int derivativeOffset(int sampleIndex) {
        checkSampleIndex(sampleIndex);
        return Math.multiplyExact(sampleIndex, parameterCount);
    }

    private void checkSampleIndex(int sampleIndex) {
        if (sampleIndex < 0 || sampleIndex >= sampleCount) {
            throw new IndexOutOfBoundsException(
                    "sample index: " + sampleIndex);
        }
    }

    private void checkParameterIndex(int parameterIndex) {
        if (parameterIndex < 0 || parameterIndex >= parameterCount) {
            throw new IndexOutOfBoundsException(
                    "parameter index: " + parameterIndex);
        }
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("non-finite " + label);
        }
    }

    private static void requireFinite(double[] values, String label) {
        for (double value : values) {
            requireFinite(value, label);
        }
    }
}