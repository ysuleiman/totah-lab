package totah.lab.athena.pocket.similar;

import org.junit.jupiter.api.Test;
import totah.lab.athena.pocket.geometry.PocketGeometryBasis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PocketShapeDistanceTest {

    private static PocketShapeDescriptor descriptor(
            double radiusOfGyration,
            double majorExtent,
            double middleExtent,
            double minorExtent,
            double[] radialHistogram
    ) {
        return new PocketShapeDescriptor(
                100,
                PocketGeometryBasis.ALPHA_SPHERES,
                radiusOfGyration,
                radiusOfGyration * 2.0,
                radiusOfGyration * 0.8,
                radiusOfGyration * 0.2,
                majorExtent,
                middleExtent,
                minorExtent,
                majorExtent == 0.0 ? 0.0 : middleExtent / majorExtent,
                majorExtent == 0.0 ? 0.0 : minorExtent / majorExtent,
                radialHistogram
        );
    }

    private static double[] uniformHistogram() {
        double[] histogram = new double[12];
        for (int index = 0; index < 12; index++) {
            histogram[index] = 1.0 / 12.0;
        }
        return histogram;
    }

    @Test
    void identicalDescriptorsHaveZeroDistance() {
        PocketShapeDescriptor descriptor =
                descriptor(10.0, 20.0, 12.0, 4.0, uniformHistogram());

        assertEquals(
                0.0,
                PocketShapeDistance.calculate(descriptor, descriptor),
                1e-12
        );
    }

    @Test
    void distanceIsSymmetric() {
        PocketShapeDescriptor first =
                descriptor(10.0, 20.0, 12.0, 4.0, uniformHistogram());
        PocketShapeDescriptor second =
                descriptor(13.0, 26.0, 10.0, 6.5, uniformHistogram());

        assertEquals(
                PocketShapeDistance.calculate(first, second),
                PocketShapeDistance.calculate(second, first),
                1e-12
        );
    }

    @Test
    void uniformScalingUsesLogRatioDistance() {
        PocketShapeDescriptor first =
                descriptor(10.0, 20.0, 12.0, 4.0, uniformHistogram());
        PocketShapeDescriptor second =
                descriptor(20.0, 40.0, 24.0, 8.0, uniformHistogram());

        // Every scale quantity doubles: log(2)/log(4) = 0.5 for each.
        // Normalized shape ratios and the histogram are unchanged by
        // uniform scaling.
        double expected =
                0.15 * 0.5   // radius of gyration
                + 0.05 * 0.5 // maximum radius
                + 0.05 * 0.5 // mean radius
                + 0.05 * 0.5 // radius spread
                + 0.15 * 0.5 // extents
                + 0.10 * 0.0 // elongation
                + 0.10 * 0.0 // flatness
                + 0.35 * 0.0; // histogram

        assertEquals(
                expected,
                PocketShapeDistance.calculate(first, second),
                1e-9
        );
    }

    @Test
    void zeroVersusZeroScaleValuesAreIdentical() {
        PocketShapeDescriptor first =
                descriptor(0.0, 0.0, 0.0, 0.0, uniformHistogram());
        PocketShapeDescriptor second =
                descriptor(0.0, 0.0, 0.0, 0.0, uniformHistogram());

        assertEquals(
                0.0,
                PocketShapeDistance.calculate(first, second),
                1e-12
        );
    }

    @Test
    void zeroVersusPositiveScaleValueMaxesOut() {
        PocketShapeDescriptor zero =
                descriptor(0.0, 20.0, 12.0, 4.0, uniformHistogram());
        PocketShapeDescriptor positive =
                descriptor(10.0, 20.0, 12.0, 4.0, uniformHistogram());

        // Radius of gyration: 0 vs 10 -> 1.0; maximum/mean/spread
        // also involve zero -> 1.0 each; extents differ only in
        // middle/minor (12/20 vs 12/20 identical major -> all equal).
        double expected =
                0.15 * 1.0   // radius of gyration
                + 0.05 * 1.0 // maximum radius (0 vs 20)
                + 0.05 * 1.0 // mean radius (0 vs 8)
                + 0.05 * 1.0 // radius spread (0 vs 2)
                + 0.15 * 0.0 // extents identical
                + 0.10 * 0.0
                + 0.10 * 0.0
                + 0.35 * 0.0;

        assertEquals(
                expected,
                PocketShapeDistance.calculate(zero, positive),
                1e-9
        );
    }

    @Test
    void rejectsNegativeScaleValues() {
        PocketShapeDescriptor negative =
                descriptor(-1.0, 20.0, 12.0, 4.0, uniformHistogram());
        PocketShapeDescriptor valid =
                descriptor(10.0, 20.0, 12.0, 4.0, uniformHistogram());

        assertThrows(
                IllegalArgumentException.class,
                () -> PocketShapeDistance.calculate(negative, valid)
        );
    }

    @Test
    void rejectsNonFiniteScaleValues() {
        PocketShapeDescriptor nan =
                descriptor(
                        Double.NaN,
                        20.0,
                        12.0,
                        4.0,
                        uniformHistogram()
                );
        PocketShapeDescriptor infinite =
                descriptor(
                        Double.POSITIVE_INFINITY,
                        20.0,
                        12.0,
                        4.0,
                        uniformHistogram()
                );
        PocketShapeDescriptor valid =
                descriptor(10.0, 20.0, 12.0, 4.0, uniformHistogram());

        assertThrows(
                IllegalArgumentException.class,
                () -> PocketShapeDistance.calculate(nan, valid)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> PocketShapeDistance.calculate(infinite, valid)
        );
    }

    @Test
    void rejectsOutOfRangeShapeRatios() {
        PocketShapeDescriptor valid =
                descriptor(10.0, 20.0, 12.0, 4.0, uniformHistogram());

        PocketShapeDescriptor belowZero = new PocketShapeDescriptor(
                100, PocketGeometryBasis.ALPHA_SPHERES,
                10.0, 20.0, 8.0, 2.0,
                20.0, 12.0, 4.0,
                -0.1, 0.2, uniformHistogram()
        );
        PocketShapeDescriptor aboveOne = new PocketShapeDescriptor(
                100, PocketGeometryBasis.ALPHA_SPHERES,
                10.0, 20.0, 8.0, 2.0,
                20.0, 12.0, 4.0,
                0.6, Double.NaN, uniformHistogram()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> PocketShapeDistance.calculate(valid, belowZero)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> PocketShapeDistance.calculate(valid, aboveOne)
        );

        PocketShapeDescriptor elongationAboveOne =
                new PocketShapeDescriptor(
                        100, PocketGeometryBasis.ALPHA_SPHERES,
                        10.0, 20.0, 8.0, 2.0,
                        20.0, 12.0, 4.0,
                        1.4, 0.2, uniformHistogram()
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> PocketShapeDistance.calculate(
                        valid,
                        elongationAboveOne
                )
        );
    }

    @Test
    void histogramDistanceDominatesShapeChange() {
        double[] concentrated = new double[12];
        concentrated[0] = 1.0;

        PocketShapeDescriptor first =
                descriptor(10.0, 20.0, 12.0, 4.0, uniformHistogram());
        PocketShapeDescriptor second =
                descriptor(10.0, 20.0, 12.0, 4.0, concentrated);

        // L1 = |1/12 - 1| + 11 * 1/12 = 11/6, halved -> 11/12.
        double histogramDistance = 11.0 / 12.0;

        assertEquals(
                0.35 * histogramDistance,
                PocketShapeDistance.calculate(first, second),
                1e-9
        );
    }

    @Test
    void rejectsInvalidHistograms() {
        PocketShapeDescriptor valid =
                descriptor(10.0, 20.0, 12.0, 4.0, uniformHistogram());

        PocketShapeDescriptor empty = new PocketShapeDescriptor(
                100, PocketGeometryBasis.ALPHA_SPHERES,
                10.0, 20.0, 8.0, 2.0,
                20.0, 12.0, 4.0,
                0.6, 0.2, new double[0]
        );
        PocketShapeDescriptor notNormalized =
                descriptor(10.0, 20.0, 12.0, 4.0, new double[12]);

        assertThrows(
                IllegalArgumentException.class,
                () -> PocketShapeDistance.calculate(valid, empty)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> PocketShapeDistance.calculate(valid, notNormalized)
        );

        double[] negative = uniformHistogram();
        negative[3] = -0.5;
        negative[4] += 0.5; // keep the sum at 1.0
        PocketShapeDescriptor negativeBin =
                descriptor(10.0, 20.0, 12.0, 4.0, negative);

        assertThrows(
                IllegalArgumentException.class,
                () -> PocketShapeDistance.calculate(valid, negativeBin)
        );
    }

    @Test
    void distanceIsClampedToUnitInterval() {
        double[] firstHistogram = new double[12];
        double[] secondHistogram = new double[12];
        firstHistogram[0] = 1.0;
        secondHistogram[11] = 1.0;

        PocketShapeDescriptor first =
                descriptor(1.0, 1.0, 1.0, 1.0, firstHistogram);
        PocketShapeDescriptor second =
                descriptor(100.0, 100.0, 50.0, 25.0, secondHistogram);

        double distance =
                PocketShapeDistance.calculate(first, second);

        assertTrue(distance >= 0.0 && distance <= 1.0);
    }
}
