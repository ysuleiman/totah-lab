package totah.lab.prometheus.neural;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import totah.lab.prometheus.numerics.LinearOperator;

/**
 * Covariance action backed by a frozen {@link FermiNetSrEvaluationStore}.
 *
 * <p>The store is sample-major: each sample owns one contiguous parameter
 * derivative row. The weighted derivative mean is computed once in the
 * constructor. Each {@link #apply(double[])} call performs pure numerical work
 * only; it never reevaluates the FermiNet.
 *
 * <p>The applied operator is:
 *
 * <pre>
 * A v =
 *     sum_k w_k (O_k - meanO) ((O_k - meanO) dot v)
 *     + regularization v
 * </pre>
 *
 * where {@code w_k} are normalized sample weights.
 */
public final class StoredCenteredCovarianceOperator implements LinearOperator {

    private final FermiNetSrEvaluationStore store;
    private final int dimension;
    private final double regularization;
    private final double[] meanDerivative;

    private final AtomicLong applications = new AtomicLong();
    private final AtomicLong derivativeRowsRead = new AtomicLong();

    public StoredCenteredCovarianceOperator(
            FermiNetSrEvaluationStore store,
            double regularization) {

        this.store = Objects.requireNonNull(store, "store");

        if (!(regularization > 0.0)
                || !Double.isFinite(regularization)) {
            throw new IllegalArgumentException(
                    "invalid covariance regularization");
        }

        this.dimension = store.parameterCount();
        this.regularization = regularization;
        this.meanDerivative = computeMeanDerivative(store);
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public double[] apply(double[] vector) {
        Objects.requireNonNull(vector, "vector");

        if (vector.length != dimension) {
            throw new IllegalArgumentException(
                    "vector dimension mismatch");
        }

        requireFinite(vector, "operator vector");

        double[] result = new double[dimension];
        double[] storage = store.derivativeStorage();

        /*
         * Process one sample row at a time.
         *
         * The first loop obtains the scalar projection:
         *
         *     dot_k = (O_k - meanO) dot v
         *
         * The second immediately reuses the same sample row to accumulate:
         *
         *     result += w_k * dot_k * (O_k - meanO)
         *
         * This deliberately favors temporal reuse of the current sample row.
         */
        for (int sample = 0; sample < store.sampleCount(); sample++) {
            int offset = store.derivativeOffset(sample);

            double projection = 0.0;

            for (int parameter = 0;
                 parameter < dimension;
                 parameter++) {

                double centered =
                        storage[offset + parameter]
                                - meanDerivative[parameter];

                projection +=
                        centered
                                * vector[parameter];
            }

            double scale =
                    store.normalizedWeight(sample)
                            * projection;

            for (int parameter = 0;
                 parameter < dimension;
                 parameter++) {

                double centered =
                        storage[offset + parameter]
                                - meanDerivative[parameter];

                result[parameter] +=
                        scale
                                * centered;
            }

            derivativeRowsRead.addAndGet(2L);
        }

        for (int parameter = 0;
             parameter < dimension;
             parameter++) {

            result[parameter] +=
                    regularization
                            * vector[parameter];
        }

        requireFinite(result, "covariance-operator result");

        applications.incrementAndGet();

        return result;
    }

    public double[] meanDerivative() {
        return meanDerivative.clone();
    }

    public Counters counters() {
        return new Counters(
                applications.get(),
                derivativeRowsRead.get(),
                store.neuralEvaluations());
    }

    private static double[] computeMeanDerivative(
            FermiNetSrEvaluationStore store) {

        int parameters = store.parameterCount();
        double[] mean = new double[parameters];
        double[] storage = store.derivativeStorage();

        for (int sample = 0;
             sample < store.sampleCount();
             sample++) {

            int offset = store.derivativeOffset(sample);
            double weight = store.normalizedWeight(sample);

            for (int parameter = 0;
                 parameter < parameters;
                 parameter++) {

                mean[parameter] +=
                        weight
                                * storage[offset + parameter];
            }
        }

        requireFinite(mean, "mean parameter derivative");

        return mean;
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

    public record Counters(
            long operatorApplications,
            long derivativeRowsRead,
            long neuralEvaluations) {
    }
}