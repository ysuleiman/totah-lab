package totah.lab.athena.pocket.compare;

import org.junit.jupiter.api.Test;
import totah.lab.athena.pocket.geometry.PocketGeometryBasis;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the composite (principal-axis + ICP) alignment and
 * comparison path against point clouds transformed by known rigid
 * transforms.
 */
class KnownTransformAlignmentTest {

    private static final double LOOSE = 1.0e-3;

    private static final double[][] BASE_COORDINATES = {
            {0.0, 0.0, 0.0},
            {10.0, 0.0, 0.0},
            {0.0, 6.0, 0.0},
            {0.0, 0.0, 3.0},
            {8.0, 5.0, 2.0},
            {2.0, 4.0, 6.0},
            {7.0, 1.0, 5.0},
            {3.0, 8.0, 1.0}
    };

    // 90 degrees about the z-axis.
    private static final double[][] ROTATION_90_Z = {
            {0.0, -1.0, 0.0},
            {1.0, 0.0, 0.0},
            {0.0, 0.0, 1.0}
    };

    private static final RigidTransform ROTATE_AND_TRANSLATE =
            new RigidTransform(
                    ROTATION_90_Z,
                    new Point3D(3.0, -2.0, 5.0)
            );

    private static final RigidTransform TRANSLATE_ONLY =
            new RigidTransform(
                    identity(),
                    new Point3D(-7.0, 4.0, 11.0)
            );

    private static final RigidTransform ROTATE_ONLY =
            new RigidTransform(
                    ROTATION_90_Z,
                    new Point3D(0.0, 0.0, 0.0)
            );

    private final PocketComparator comparator =
            new PocketComparator(
                    new CompositePocketAligner(),
                    PocketComparisonOptions.defaults()
            );

    @Test
    void alignsRigidlyTransformedCopy() {
        assertRecoversTransform(ROTATE_AND_TRANSLATE);
    }

    @Test
    void alignsTranslatedCopy() {
        assertRecoversTransform(TRANSLATE_ONLY);
    }

    @Test
    void alignsRotatedCopy() {
        assertRecoversTransform(ROTATE_ONLY);
    }

    @Test
    void handlesUnequalPointCounts() {
        PocketPointCloud query = baseCloud();

        // Transformed copy with two points removed.
        List<Point3D> subset = new ArrayList<>(
                ROTATE_AND_TRANSLATE.apply(query.points())
                        .subList(0, 6)
        );

        PocketPointCloud candidate = new PocketPointCloud(
                subset,
                PocketGeometryBasis.RESIDUE_ATOMS
        );

        PocketComparison comparison =
                comparator.compare(query, candidate);

        // The retained six points still superimpose approximately:
        // distances stay small, and every candidate point finds a
        // close query neighbor.
        assertTrue(
                comparison.meanBidirectionalDistance() < 1.0,
                "bidirectional distance was "
                        + comparison.meanBidirectionalDistance()
        );
        assertTrue(
                comparison.candidateCoverage() >= 0.75,
                "candidate coverage was "
                        + comparison.candidateCoverage()
        );
        assertTrue(
                comparison.queryCoverage() < 1.0,
                "query coverage should drop when points are missing"
        );
    }

    @Test
    void outliersReduceSimilarityRatherThanCorruptRanking() {
        PocketPointCloud query = baseCloud();

        List<Point3D> points = new ArrayList<>(
                ROTATE_AND_TRANSLATE.apply(query.points())
        );
        points.add(new Point3D(25.0, 25.0, 25.0));

        PocketPointCloud candidate = new PocketPointCloud(
                points,
                PocketGeometryBasis.RESIDUE_ATOMS
        );

        PocketComparison comparison =
                comparator.compare(query, candidate);

        // Documented behavior: the composite aligner is not
        // outlier-robust. A single moderate outlier can derail the
        // principal-axis frame, so similarity collapses instead of the
        // pair being reported as a match.
        assertTrue(
                comparison.overallSimilarity() < 0.5,
                "overall similarity was "
                        + comparison.overallSimilarity()
        );
    }

    @Test
    void scoresClearlyUnrelatedCloudLow() {
        PocketPointCloud query = baseCloud();

        List<Point3D> scaled = query.points()
                .stream()
                .map(point -> new Point3D(
                        point.x() * 2.0,
                        point.y() * 2.0,
                        point.z() * 2.0
                ))
                .toList();

        PocketPointCloud candidate = new PocketPointCloud(
                scaled,
                PocketGeometryBasis.RESIDUE_ATOMS
        );

        PocketComparison comparison =
                comparator.compare(query, candidate);

        assertTrue(
                comparison.overallSimilarity() < 0.3,
                "overall similarity was "
                        + comparison.overallSimilarity()
        );
    }

    @Test
    void producesDeterministicResults() {
        PocketPointCloud query = baseCloud();
        PocketPointCloud candidate = new PocketPointCloud(
                ROTATE_AND_TRANSLATE.apply(query.points()),
                PocketGeometryBasis.RESIDUE_ATOMS
        );

        PocketComparison first =
                comparator.compare(query, candidate);
        PocketComparison second =
                comparator.compare(query, candidate);

        assertEquals(
                first.overallSimilarity(),
                second.overallSimilarity(),
                0.0
        );
        assertEquals(
                first.meanBidirectionalDistance(),
                second.meanBidirectionalDistance(),
                0.0
        );
    }

    private void assertRecoversTransform(RigidTransform knownTransform) {
        PocketPointCloud query = baseCloud();
        PocketPointCloud candidate = new PocketPointCloud(
                knownTransform.apply(query.points()),
                PocketGeometryBasis.RESIDUE_ATOMS
        );

        PocketAlignment alignment =
                comparator.align(query, candidate);

        // The recovered transform maps the transformed candidate back
        // onto the original query points (the inverse mapping).
        List<Point3D> recovered =
                alignment.transform().apply(candidate.points());

        assertEquals(query.size(), recovered.size());

        for (int index = 0; index < recovered.size(); index++) {
            assertEquals(
                    query.points().get(index).x(),
                    recovered.get(index).x(),
                    LOOSE,
                    "x at index " + index
            );
            assertEquals(
                    query.points().get(index).y(),
                    recovered.get(index).y(),
                    LOOSE,
                    "y at index " + index
            );
            assertEquals(
                    query.points().get(index).z(),
                    recovered.get(index).z(),
                    LOOSE,
                    "z at index " + index
            );
        }

        PocketComparison comparison =
                comparator.compareAligned(alignment);

        assertEquals(
                1.0,
                comparison.queryCoverage(),
                LOOSE
        );
        assertEquals(
                1.0,
                comparison.candidateCoverage(),
                LOOSE
        );
        assertTrue(
                comparison.meanBidirectionalDistance() < LOOSE,
                "bidirectional distance was "
                        + comparison.meanBidirectionalDistance()
        );
        assertTrue(
                comparison.geometrySimilarity() > 0.99,
                "geometry similarity was "
                        + comparison.geometrySimilarity()
        );
    }

    private static PocketPointCloud baseCloud() {
        List<Point3D> points = new ArrayList<>();

        for (double[] coordinate : BASE_COORDINATES) {
            points.add(new Point3D(
                    coordinate[0],
                    coordinate[1],
                    coordinate[2]
            ));
        }

        return new PocketPointCloud(
                points,
                PocketGeometryBasis.RESIDUE_ATOMS
        );
    }

    private static double[][] identity() {
        return new double[][]{
                {1.0, 0.0, 0.0},
                {0.0, 1.0, 0.0},
                {0.0, 0.0, 1.0}
        };
    }
}
