package totah.lab.athena.pocket.similar;

import java.util.Objects;

/**
 * Calculates distance between rotation-independent pocket descriptors.
 *
 * <p>Lower scores indicate greater similarity.</p>
 */
public final class PocketShapeDistance {

    private static final double EPSILON = 1.0e-9;

    private PocketShapeDistance() {
    }

    public static double calculate(
            PocketShapeDescriptor first,
            PocketShapeDescriptor second) {

        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");

        double pointCountDistance =
                relativeDifference(
                        first.pointCount(),
                        second.pointCount());

        double radiusOfGyrationDistance =
                relativeDifference(
                        first.radiusOfGyration(),
                        second.radiusOfGyration());

        double maximumRadiusDistance =
                relativeDifference(
                        first.maximumRadius(),
                        second.maximumRadius());

        double meanRadiusDistance =
                relativeDifference(
                        first.meanRadius(),
                        second.meanRadius());

        double radiusSpreadDistance =
                relativeDifference(
                        first.radiusStandardDeviation(),
                        second.radiusStandardDeviation());

        double extentDistance =
                (
                        relativeDifference(
                                first.majorExtent(),
                                second.majorExtent())
                                +
                                relativeDifference(
                                        first.middleExtent(),
                                        second.middleExtent())
                                +
                                relativeDifference(
                                        first.minorExtent(),
                                        second.minorExtent())
                ) / 3.0;

        double elongationDistance =
                relativeDifference(
                        first.elongation(),
                        second.elongation());

        double flatnessDistance =
                relativeDifference(
                        first.flatness(),
                        second.flatness());

        double histogramDistance =
                histogramDistance(
                        first.radialHistogram(),
                        second.radialHistogram());

        return 0.05 * pointCountDistance
                + 0.15 * radiusOfGyrationDistance
                + 0.10 * maximumRadiusDistance
                + 0.10 * meanRadiusDistance
                + 0.10 * radiusSpreadDistance
                + 0.20 * extentDistance
                + 0.05 * elongationDistance
                + 0.05 * flatnessDistance
                + 0.20 * histogramDistance;
    }

    private static double relativeDifference(
            double first,
            double second) {

        double denominator =
                Math.max(
                        Math.max(
                                Math.abs(first),
                                Math.abs(second)),
                        EPSILON);

        return Math.abs(first - second)
                / denominator;
    }

    /**
     * L1 histogram distance normalized to the range [0, 1].
     */
    private static double histogramDistance(
            double[] first,
            double[] second) {

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

        return distance / 2.0;
    }
}
