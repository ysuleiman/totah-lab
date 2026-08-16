package totah.lab.prometheus.numerics;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Applies a regularized covariance operator from replayable weighted
 * observations while centering against a fixed supplied mean on the fly.
 *
 * <p>No centered observation vector is materialized:
 *
 * <pre>
 * c = observation - mean
 * result += weight * c * (c dot vector)
 * result += regularization * vector
 * </pre>
 *
 * <p>Memory complexity per application is O(p), independent of sample count.
 */
public final class StreamingMeanCenteredCovarianceOperator
        implements LinearOperator {

    private final int dimension;
    private final ObservationSource source;
    private final double[] mean;
    private final double regularization;

    private final AtomicLong applications = new AtomicLong();
    private final AtomicLong observations = new AtomicLong();
    private final AtomicLong passes = new AtomicLong();

    public StreamingMeanCenteredCovarianceOperator(
            int dimension,
            ObservationSource source,
            double[] mean,
            double regularization) {

        if (dimension < 1) {
            throw new IllegalArgumentException("invalid covariance dimension");
        }

        if (!(regularization > 0.0)
                || !Double.isFinite(regularization)) {
            throw new IllegalArgumentException(
                    "invalid covariance regularization");
        }

        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(mean, "mean");

        if (mean.length != dimension) {
            throw new IllegalArgumentException(
                    "mean dimension mismatch");
        }

        for (double value : mean) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "non-finite covariance mean");
            }
        }

        this.dimension = dimension;
        this.source = source;
        this.mean = mean.clone();
        this.regularization = regularization;
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

        for (double value : vector) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "non-finite covariance input vector");
            }
        }

        double[] result = new double[dimension];

        source.forEach((weight, observation) -> {
            if (!Double.isFinite(weight)
                    || weight < 0.0) {
                throw new IllegalArgumentException(
                        "invalid observation weight");
            }

            if (observation.length != dimension) {
                throw new IllegalArgumentException(
                        "observation dimension mismatch");
            }

            /*
             * Preserve the same left-to-right scalar reduction structure used
             * by StreamingCovarianceOperator / PCG dot products.
             *
             * Critically, no double[dimension] centered vector is allocated.
             */
            double projection = 0.0;

            for (int i = 0; i < dimension; i++) {
                double value = observation[i];

                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException(
                            "non-finite covariance observation");
                }

                projection +=
                        (value - mean[i]) * vector[i];
            }

            for (int i = 0; i < dimension; i++) {
                result[i] +=
                        weight
                                * (observation[i] - mean[i])
                                * projection;
            }

            observations.incrementAndGet();
        });

        for (int i = 0; i < dimension; i++) {
            result[i] +=
                    regularization * vector[i];

            if (!Double.isFinite(result[i])) {
                throw new IllegalArgumentException(
                        "non-finite covariance result");
            }
        }

        applications.incrementAndGet();
        passes.incrementAndGet();

        return result;
    }

    public Counters counters() {
        return new Counters(
                applications.get(),
                passes.get(),
                observations.get());
    }

    @FunctionalInterface
    public interface ObservationSource {
        void forEach(ObservationConsumer consumer);
    }

    @FunctionalInterface
    public interface ObservationConsumer {
        void accept(
                double normalizedWeight,
                double[] observation);
    }

    public record Counters(
            long operatorApplications,
            long streamedPasses,
            long observations) {
    }
}