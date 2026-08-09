package totah.lab.euclid.spatial;

import org.junit.jupiter.api.Test;
import totah.lab.euclid.spatial.RmsdClusterer.Clustering;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RmsdClustererTest {

    private final RmsdClusterer clusterer = new RmsdClusterer();

    @Test
    void rmsdIsZeroForIdenticalPoses() {
        List<double[]> pose = List.of(
                new double[]{1, 2, 3},
                new double[]{4, 5, 6}
        );
        assertEquals(0.0, RmsdClusterer.rmsd(pose, pose), 1.0e-9);
    }

    @Test
    void rmsdMatchesHandComputedValue() {
        // two atoms shifted by 3 and 4 along x: mean square (9+16)/2
        List<double[]> first = List.of(
                new double[]{0, 0, 0},
                new double[]{0, 0, 0}
        );
        List<double[]> second = List.of(
                new double[]{3, 0, 0},
                new double[]{4, 0, 0}
        );
        assertEquals(
                Math.sqrt(12.5),
                RmsdClusterer.rmsd(first, second),
                1.0e-9
        );
    }

    @Test
    void rmsdRejectsMismatchedAtomCounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RmsdClusterer.rmsd(
                        List.of(new double[]{0, 0, 0}),
                        List.of(
                                new double[]{0, 0, 0},
                                new double[]{1, 1, 1}
                        )
                )
        );
    }

    @Test
    void clustersKnownStructureAtThreshold() {
        // two tight groups of three/two poses far apart, one outlier
        List<List<double[]>> poses = List.of(
                pose(0.0),
                pose(0.5),
                pose(1.0),
                pose(10.0),
                pose(10.5),
                pose(30.0)
        );

        Clustering clustering = clusterer.cluster(poses, 2.0);

        assertEquals(3, clustering.clusterCount());
        assertEquals(3, clustering.largestClusterSize());
        assertEquals(List.of(0, 1, 2), clustering.topCluster());
    }

    @Test
    void completeLinkageKeepsChainedButDistantPosesSeparate() {
        // 0-1 and 1-2 are within threshold, but 0-2 is not: complete
        // linkage must not merge all three
        List<List<double[]>> poses = List.of(
                pose(0.0),
                pose(1.5),
                pose(3.0)
        );

        Clustering clustering = clusterer.cluster(poses, 2.0);

        assertEquals(2, clustering.clusterCount());
        assertEquals(2, clustering.largestClusterSize());
    }

    @Test
    void exactThresholdIsIncluded() {
        Clustering clustering = clusterer.cluster(
                List.of(pose(0.0), pose(2.0)),
                2.0
        );

        assertEquals(1, clustering.clusterCount());
    }

    @Test
    void rejectsInvalidThresholdsAndCoordinates() {
        assertThrows(
                IllegalArgumentException.class,
                () -> clusterer.cluster(List.of(pose(0.0)), -0.1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> clusterer.cluster(List.of(pose(0.0)), Double.NaN)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RmsdClusterer.rmsd(
                        List.of(new double[]{0, 0}),
                        List.of(new double[]{0, 0, 0})
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RmsdClusterer.rmsd(
                        List.of(new double[]{Double.POSITIVE_INFINITY, 0, 0}),
                        List.of(new double[]{0, 0, 0})
                )
        );
    }

    @Test
    void clusteringResultIsImmutable() {
        Clustering clustering = clusterer.cluster(
                List.of(pose(0.0), pose(0.5)),
                1.0
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> clustering.clusters().add(List.of(2))
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> clustering.topCluster().add(2)
        );
    }

    @Test
    void emptyInputYieldsNoClusters() {
        Clustering clustering = clusterer.cluster(List.of(), 2.0);

        assertEquals(0, clustering.clusterCount());
        assertEquals(0, clustering.largestClusterSize());
        assertEquals(List.of(), clustering.topCluster());
    }

    /**
     * A two-atom pose translated along x; intra-pair RMSD of two
     * poses equals the translation difference.
     */
    private static List<double[]> pose(double offset) {
        return List.of(
                new double[]{offset, 0, 0},
                new double[]{offset, 2, 0}
        );
    }
}
