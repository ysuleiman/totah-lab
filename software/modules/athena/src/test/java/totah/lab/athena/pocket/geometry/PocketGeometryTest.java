package totah.lab.athena.pocket.geometry;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.BoundingBox;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketId;
import totah.lab.gaia.pocket.PocketSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PocketGeometryTest {
    private static final double TOLERANCE = 1.0e-6;

    @Test
    void preservesAlphaSphereCentroidAndBoundsBehavior() {
        Pocket pocket = pocket(
                new AlphaSphere(1, new Point3D(0, 2, 4), 1),
                new AlphaSphere(2, new Point3D(4, 6, 8), 2));

        AlphaSphereSet spheres = pocket.alphaSphereSet().orElseThrow();
        Point3D centroid = PocketGeometry.alphaSphereCentroid(spheres);
        BoundingBox bounds = PocketGeometry.alphaSphereBounds(spheres, true);

        assertEquals(2.0, centroid.x(), TOLERANCE);
        assertEquals(4.0, centroid.y(), TOLERANCE);
        assertEquals(6.0, centroid.z(), TOLERANCE);
        assertEquals(-1.0, bounds.min().x(), TOLERANCE);
        assertEquals(10.0, bounds.max().z(), TOLERANCE);
        assertEquals(7.0 * 7.0 * 7.0, bounds.volume(), TOLERANCE);
    }

    @Test
    void preservesIntersectionAndIouBehavior() {
        BoundingBox first = new BoundingBox(
                new Point3D(0, 0, 0), new Point3D(2, 2, 2));
        BoundingBox second = new BoundingBox(
                new Point3D(1, 1, 1), new Point3D(3, 3, 3));

        assertEquals(
                1.0,
                PocketGeometry.intersectionVolume(first, second),
                TOLERANCE);
        assertEquals(
                1.0 / 15.0,
                PocketGeometry.intersectionOverUnion(first, second),
                TOLERANCE);
        assertEquals(
                1.0,
                PocketGeometry.axisOverlap(0, 2, 1, 3),
                TOLERANCE);
    }

    @Test
    void centerDistanceUsesAlphaSphereCentroids() {
        Pocket first = pocket(
                new AlphaSphere(1, new Point3D(0, 0, 0), 1));
        Pocket second = pocket(
                new AlphaSphere(2, new Point3D(3, 4, 0), 1));

        assertEquals(
                5.0,
                PocketGeometry.alphaSphereCenterDistance(first, second),
                TOLERANCE);
    }

    @Test
    void overlapRejectsMixedGeometryBases() {
        PocketGeometryResult spheres = geometryResult(
                PocketGeometryBasis.ALPHA_SPHERES);
        PocketGeometryResult residues = geometryResult(
                PocketGeometryBasis.RESOLVED_RESIDUE_HEAVY_ATOMS);

        assertThrows(
                IllegalArgumentException.class,
                () -> PocketGeometry.overlap(spheres, residues));
        assertThrows(
                IllegalArgumentException.class,
                () -> PocketGeometry.overlap(residues, spheres));
    }

    @Test
    void overlapBetweenSameBasisIsUnchanged() {
        PocketGeometryResult first = geometryResult(
                PocketGeometryBasis.ALPHA_SPHERES,
                new BoundingBox(
                        new Point3D(0, 0, 0), new Point3D(2, 2, 2)));
        PocketGeometryResult second = geometryResult(
                PocketGeometryBasis.ALPHA_SPHERES,
                new BoundingBox(
                        new Point3D(1, 1, 1), new Point3D(3, 3, 3)));

        PocketOverlapResult result = PocketGeometry.overlap(first, second);

        assertEquals(1.0, result.intersectionVolume(), TOLERANCE);
        assertEquals(1.0 / 15.0, result.intersectionOverUnion(), TOLERANCE);
        assertEquals(
                PocketGeometryBasis.ALPHA_SPHERES, result.firstBasis());
        assertEquals(
                PocketGeometryBasis.ALPHA_SPHERES, result.secondBasis());
    }

    private static PocketGeometryResult geometryResult(
            PocketGeometryBasis basis) {
        return geometryResult(
                basis,
                new BoundingBox(
                        new Point3D(0, 0, 0), new Point3D(2, 2, 2)));
    }

    private static PocketGeometryResult geometryResult(
            PocketGeometryBasis basis,
            BoundingBox bounds) {
        return new PocketGeometryResult(
                bounds,
                bounds.center(),
                basis,
                List.of());
    }

    private static Pocket pocket(AlphaSphere... spheres) {
        return new Pocket(
                new PocketId("1"),
                "Pocket 1",
                PocketSource.FPOCKET,
                new Point3D(0, 0, 0),
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.of(new AlphaSphereSet(List.of(spheres))),
                Map.of());
    }

}
