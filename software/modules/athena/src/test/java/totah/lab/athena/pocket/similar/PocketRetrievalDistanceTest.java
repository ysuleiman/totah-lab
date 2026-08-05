package totah.lab.athena.pocket.similar;

import org.junit.jupiter.api.Test;
import totah.lab.athena.pocket.geometry.PocketGeometryBasis;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.gaia.geometry.Point3D;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PocketRetrievalDistanceTest {

    private static final double TOLERANCE = 1.0e-9;

    private static final double[] UNIFORM_HISTOGRAM = {
            1.0 / 12, 1.0 / 12, 1.0 / 12, 1.0 / 12,
            1.0 / 12, 1.0 / 12, 1.0 / 12, 1.0 / 12,
            1.0 / 12, 1.0 / 12, 1.0 / 12, 1.0 / 12
    };

    @Test
    void identicalDescriptorsHaveZeroDistance() {
        PocketShapeDescriptor descriptor = descriptor(
                5.0, 10.0, 6.0, 2.0, UNIFORM_HISTOGRAM
        );

        assertEquals(
                0.0,
                PocketRetrievalDistance.scaleAwareDistance(
                        descriptor, descriptor
                ),
                TOLERANCE
        );
        assertEquals(
                0.0,
                PocketRetrievalDistance.scaleNormalizedDistance(
                        descriptor, descriptor
                ),
                TOLERANCE
        );
        assertEquals(
                0.0,
                PocketRetrievalDistance.retrievalDistance(
                        descriptor, descriptor
                ),
                TOLERANCE
        );
    }

    @Test
    void uniformScalingOnlyPenalizesTheScaleAwareForm() {
        PocketShapeDescriptor compact = describe(cloud(1.0));
        PocketShapeDescriptor doubled = describe(cloud(2.0));

        double expectedScaleAware =
                0.20 * 0.5 + 0.20 * 0.5;

        assertEquals(
                expectedScaleAware,
                PocketRetrievalDistance.scaleAwareDistance(compact, doubled),
                1.0e-6
        );
        assertEquals(
                0.0,
                PocketRetrievalDistance.scaleNormalizedDistance(
                        compact, doubled
                ),
                1.0e-6
        );

        // The retrieval distance picks the normalized path, so a compact
        // query stays retrievable against its 2x merged counterpart —
        // paying only the partial-match penalty.
        assertEquals(
                PocketRetrievalDistance.PARTIAL_MATCH_PENALTY,
                PocketRetrievalDistance.retrievalDistance(compact, doubled),
                1.0e-6
        );
    }

    @Test
    void histogramDistanceIsHalfTheL1Distance() {
        double[] first = new double[12];
        double[] second = new double[12];
        first[0] = 1.0;
        second[11] = 1.0;

        PocketShapeDescriptor a = descriptor(5.0, 10.0, 6.0, 2.0, first);
        PocketShapeDescriptor b = descriptor(5.0, 10.0, 6.0, 2.0, second);

        // 0.5 * (|1-0| + |0-1|) = 1.0 — maximally different histograms.
        double expectedNormalized = 0.30 * 1.0 / 0.60;

        assertEquals(
                expectedNormalized,
                PocketRetrievalDistance.scaleNormalizedDistance(a, b),
                TOLERANCE
        );
        assertTrue(expectedNormalized >= 0.0 && expectedNormalized <= 1.0);
    }

    @Test
    void histogramOnlyDifferenceIsBounded() {
        PocketShapeDescriptor a = descriptor(
                5.0, 10.0, 6.0, 2.0, UNIFORM_HISTOGRAM
        );
        PocketShapeDescriptor b = descriptor(
                5.0, 10.0, 6.0, 2.0,
                new double[]{0.5, 0.5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}
        );

        double normalized =
                PocketRetrievalDistance.scaleNormalizedDistance(a, b);
        assertTrue(normalized >= 0.0 && normalized <= 1.0);

        double aware = PocketRetrievalDistance.scaleAwareDistance(a, b);
        assertTrue(aware >= 0.0 && aware <= 1.0);
    }

    @Test
    void nonPositiveSizesFallBackToMaximumLogRatio() {
        PocketShapeDescriptor degenerate = descriptor(
                0.0, 0.0, 0.0, 0.0, UNIFORM_HISTOGRAM
        );
        PocketShapeDescriptor normal = descriptor(
                5.0, 10.0, 6.0, 2.0, UNIFORM_HISTOGRAM
        );

        // rg and major components both fall back to 1.0; the degenerate
        // descriptor's normalized elongation/flatness are 0.
        double expected =
                0.20 * 1.0
                        + 0.20 * 1.0
                        + 0.15 * Math.abs(0.0 - 0.6)
                        + 0.15 * Math.abs(0.0 - 0.2);

        assertEquals(
                expected,
                PocketRetrievalDistance.scaleAwareDistance(
                        degenerate, normal
                ),
                TOLERANCE
        );
    }

    @Test
    void retrievalNeverExceedsTheScaleAwareDistance() {
        PocketShapeDescriptor query = describe(cloud(1.0));

        for (double scale : new double[]{0.5, 1.0, 1.7, 2.0, 3.9}) {
            PocketShapeDescriptor candidate = describe(cloud(scale));

            double aware =
                    PocketRetrievalDistance.scaleAwareDistance(
                            query, candidate
                    );
            double normalized =
                    PocketRetrievalDistance.scaleNormalizedDistance(
                            query, candidate
                    );
            double retrieval =
                    PocketRetrievalDistance.retrievalDistance(
                            query, candidate
                    );

            assertTrue(
                    retrieval <= aware + TOLERANCE,
                    "retrieval " + retrieval + " exceeds scale-aware "
                            + aware + " at scale " + scale
            );
            assertTrue(
                    retrieval <= normalized
                            + PocketRetrievalDistance.PARTIAL_MATCH_PENALTY
                            + TOLERANCE,
                    "retrieval exceeds normalized + penalty at scale "
                            + scale
            );
        }
    }

    @Test
    void unequalHistogramLengthsAreRejected() {
        PocketShapeDescriptor a = descriptor(
                5.0, 10.0, 6.0, 2.0, UNIFORM_HISTOGRAM
        );
        PocketShapeDescriptor b = descriptor(
                5.0, 10.0, 6.0, 2.0, new double[]{0.5, 0.5}
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> PocketRetrievalDistance.retrievalDistance(a, b)
        );
    }

    private static PocketShapeDescriptor describe(
            PocketPointCloud cloud
    ) {
        return PocketShapeDescriptorFactory.describe(
                cloud,
                PocketShapeDescriptorFactory.DEFAULT_RADIAL_BIN_COUNT
        );
    }

    /**
     * An irregular 8-point cloud, uniformly scaled; scaling leaves the
     * normalized elongation/flatness and the radial histogram unchanged.
     */
    private static PocketPointCloud cloud(double scale) {
        double[][] coordinates = {
                {0.0, 0.0, 0.0},
                {10.0, 0.0, 0.0},
                {0.0, 6.0, 0.0},
                {0.0, 0.0, 3.0},
                {8.0, 5.0, 2.0},
                {2.0, 4.0, 6.0},
                {7.0, 1.0, 5.0},
                {3.0, 8.0, 1.0}
        };

        List<Point3D> points = Arrays.stream(coordinates)
                .map(coordinate -> new Point3D(
                        coordinate[0] * scale,
                        coordinate[1] * scale,
                        coordinate[2] * scale
                ))
                .toList();

        return new PocketPointCloud(
                points,
                PocketGeometryBasis.ALPHA_SPHERES
        );
    }

    private static PocketShapeDescriptor descriptor(
            double radiusOfGyration,
            double majorExtent,
            double middleExtent,
            double minorExtent,
            double[] radialHistogram
    ) {
        return new PocketShapeDescriptor(
                8,
                PocketGeometryBasis.ALPHA_SPHERES,
                radiusOfGyration,
                majorExtent,
                radiusOfGyration,
                0.0,
                majorExtent,
                middleExtent,
                minorExtent,
                middleExtent == 0.0 ? 1.0 : majorExtent / middleExtent,
                minorExtent == 0.0 ? 1.0 : middleExtent / minorExtent,
                radialHistogram
        );
    }
}
