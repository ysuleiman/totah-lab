package totah.lab.athena.pocket.similar;

import java.util.Objects;

/**
 * Calculates a coarse global shape distance between complete pocket
 * descriptors.
 *
 * <p>This metric is intended for whole-pocket retrieval and comparison.
 * It is intentionally insensitive to rotation and translation, but it
 * is not designed to detect partial-pocket or embedded-subpocket
 * similarity. Do not tune these weights expecting local matching
 * behavior.</p>
 *
 * <p>Scale quantities (radii, extents) use log-ratio distance, which
 * treats multiplicative size changes symmetrically. Shape ratios
 * (elongation, flatness) are compared as normalized [0,1] differences.
 * The radial histogram carries the largest weight because it describes
 * the overall radial shape distribution rather than a single scalar.
 * Lower scores indicate greater similarity; the result is clamped to
 * [0, 1].</p>
 */
public final class PocketShapeDistance {

    private static final double WEIGHT_RADIUS_OF_GYRATION = 0.15;
    private static final double WEIGHT_MAXIMUM_RADIUS = 0.05;
    private static final double WEIGHT_MEAN_RADIUS = 0.05;
    private static final double WEIGHT_RADIUS_SPREAD = 0.05;
    private static final double WEIGHT_EXTENTS = 0.15;
    private static final double WEIGHT_ELONGATION = 0.10;
    private static final double WEIGHT_FLATNESS = 0.10;
    private static final double WEIGHT_HISTOGRAM = 0.35;

    private static final double HISTOGRAM_SUM_TOLERANCE = 1.0e-3;

    private PocketShapeDistance() {
    }

    public static double calculate(
            PocketShapeDescriptor first,
            PocketShapeDescriptor second) {

        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");

        double radiusOfGyrationDistance =
                logRatioDistance(
                        first.radiusOfGyration(),
                        second.radiusOfGyration());

        double maximumRadiusDistance =
                logRatioDistance(
                        first.maximumRadius(),
                        second.maximumRadius());

        double meanRadiusDistance =
                logRatioDistance(
                        first.meanRadius(),
                        second.meanRadius());

        double radiusSpreadDistance =
                logRatioDistance(
                        first.radiusStandardDeviation(),
                        second.radiusStandardDeviation());

        double extentDistance =
                (
                        logRatioDistance(
                                first.majorExtent(),
                                second.majorExtent())
                                +
                                logRatioDistance(
                                        first.middleExtent(),
                                        second.middleExtent())
                                +
                                logRatioDistance(
                                        first.minorExtent(),
                                        second.minorExtent())
                ) / 3.0;

        double elongationDistance =
                normalizedDifference(
                        first.elongation(),
                        second.elongation(),
                        "elongation");

        double flatnessDistance =
                normalizedDifference(
                        first.flatness(),
                        second.flatness(),
                        "flatness");

        double histogramDistance =
                histogramDistance(
                        first.radialHistogram(),
                        second.radialHistogram());

        double distance =
                WEIGHT_RADIUS_OF_GYRATION * radiusOfGyrationDistance
                + WEIGHT_MAXIMUM_RADIUS * maximumRadiusDistance
                + WEIGHT_MEAN_RADIUS * meanRadiusDistance
                + WEIGHT_RADIUS_SPREAD * radiusSpreadDistance
                + WEIGHT_EXTENTS * extentDistance
                + WEIGHT_ELONGATION * elongationDistance
                + WEIGHT_FLATNESS * flatnessDistance
                + WEIGHT_HISTOGRAM * histogramDistance;

        return clamp(distance);
    }

    /**
     * Log-ratio distance for positive scale quantities, normalized so
     * a fourfold multiplicative difference maps to 1.0. Two zero
     * values are identical and produce 0.0.
     */
    private static double logRatioDistance(
            double first,
            double second) {

        requireNonNegativeFinite(
                first,
                "first scale value");

        requireNonNegativeFinite(
                second,
                "second scale value");

        if (first == 0.0 && second == 0.0) {
            return 0.0;
        }

        if (first == 0.0 || second == 0.0) {
            return 1.0;
        }

        return clamp(
                Math.abs(Math.log(first / second))
                        / Math.log(4.0));
    }

    /**
     * Absolute difference for canonical normalized quantities, which
     * the descriptor factory produces in [0, 1].
     */
    private static double normalizedDifference(
            double first,
            double second,
            String name) {

        requireUnitInterval(
                first,
                "first " + name);

        requireUnitInterval(
                second,
                "second " + name);

        return Math.abs(first - second);
    }

    private static void requireNonNegativeFinite(
            double value,
            String name) {

        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(
                    name
                            + " must be finite and non-negative: "
                            + value);
        }
    }

    private static void requireUnitInterval(
            double value,
            String name) {

        if (!Double.isFinite(value)
                || value < 0.0
                || value > 1.0) {

            throw new IllegalArgumentException(
                    name
                            + " must be within [0, 1]: "
                            + value);
        }
    }

    /**
     * L1 histogram distance normalized to the range [0, 1].
     */
    private static double histogramDistance(
            double[] first,
            double[] second) {

        validateHistogram(first, "first");
        validateHistogram(second, "second");

        if (first.length != second.length) {
            throw new IllegalArgumentException(
                    "Histogram lengths must match: "
                            + first.length
                            + " vs "
                            + second.length);
        }

        double distance = 0.0;

        for (int index = 0;
             index < first.length;
             index++) {

            distance += Math.abs(
                    first[index] - second[index]);
        }

        return clamp(distance / 2.0);
    }

    private static void validateHistogram(
            double[] histogram,
            String name) {

        Objects.requireNonNull(histogram, name + " histogram");

        if (histogram.length == 0) {
            throw new IllegalArgumentException(
                    name + " histogram must not be empty");
        }

        double sum = 0.0;

        for (int index = 0; index < histogram.length; index++) {
            double bin = histogram[index];

            if (!Double.isFinite(bin) || bin < 0.0) {
                throw new IllegalArgumentException(
                        name
                                + " histogram bin "
                                + index
                                + " must be finite and non-negative: "
                                + bin);
            }

            sum += bin;
        }

        if (Math.abs(sum - 1.0) > HISTOGRAM_SUM_TOLERANCE) {
            throw new IllegalArgumentException(
                    name
                            + " histogram must sum to 1.0, but sums to "
                            + sum);
        }
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
