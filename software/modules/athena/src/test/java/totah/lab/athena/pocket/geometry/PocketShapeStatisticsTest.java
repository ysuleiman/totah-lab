package totah.lab.athena.pocket.geometry;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PocketShapeStatisticsTest {

    @Test
    void computesStatisticsForTwoPoints() {
        PocketShapeStatistics statistics = PocketShapeStatistics.of(
                List.of(new Point3D(0, 0, 0), new Point3D(2, 0, 0)));

        assertEquals(2, statistics.heavyAtomCount());
        assertEquals(1.0, statistics.maximumCentroidDistance(), 1e-9);
        assertEquals(1.0, statistics.meanCentroidDistance(), 1e-9);
        assertEquals(1.0, statistics.percentile95CentroidDistance(), 1e-9);
        assertEquals(2.0, statistics.maximumPairwiseSpan(), 1e-9);
        assertEquals(1.0, statistics.radiusOfGyration(), 1e-9);
    }

    @Test
    void computesStatisticsForAsymmetricPoints() {
        PocketShapeStatistics statistics = PocketShapeStatistics.of(List.of(
                new Point3D(0, 0, 0),
                new Point3D(0, 0, 0),
                new Point3D(0, 0, 6)));

        // Centroid is (0, 0, 2); distances are 2, 2, 4.
        assertEquals(3, statistics.heavyAtomCount());
        assertEquals(4.0, statistics.maximumCentroidDistance(), 1e-9);
        assertEquals(8.0 / 3.0, statistics.meanCentroidDistance(), 1e-9);
        assertEquals(4.0, statistics.percentile95CentroidDistance(), 1e-9);
        assertEquals(6.0, statistics.maximumPairwiseSpan(), 1e-9);
        assertEquals(
                Math.sqrt((4.0 + 4.0 + 16.0) / 3.0),
                statistics.radiusOfGyration(),
                1e-9);
    }

    @Test
    void rejectsEmptyPositions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PocketShapeStatistics.of(List.of()));
    }
}
