package totah.lab.gaia.geometry;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoundingBoxTest {

    private static final double EPSILON = 1.0E-9;

    @Test
    void shouldRejectNullMinimumPoint() {
        Point3D max = new Point3D(1.0, 1.0, 1.0);

        assertThrows(
                NullPointerException.class,
                () -> new BoundingBox(null, max));
    }

    @Test
    void shouldRejectNullMaximumPoint() {
        Point3D min = new Point3D(0.0, 0.0, 0.0);

        assertThrows(
                NullPointerException.class,
                () -> new BoundingBox(min, null));
    }

    @Test
    void shouldRejectInvalidCoordinateOrder() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BoundingBox(
                        new Point3D(2.0, 0.0, 0.0),
                        new Point3D(1.0, 1.0, 1.0)));
    }

    @Test
    void shouldCalculateDimensions() {
        BoundingBox box = new BoundingBox(
                new Point3D(1.0, 2.0, 3.0),
                new Point3D(5.0, 8.0, 13.0));

        assertEquals(4.0, box.width(), EPSILON);
        assertEquals(6.0, box.height(), EPSILON);
        assertEquals(10.0, box.depth(), EPSILON);
    }

    @Test
    void shouldCalculateCenter() {
        BoundingBox box = new BoundingBox(
                new Point3D(0.0, 2.0, 4.0),
                new Point3D(4.0, 6.0, 10.0));

        assertEquals(
                new Point3D(2.0, 4.0, 7.0),
                box.center());
    }

    @Test
    void shouldCalculateVolume() {
        BoundingBox box = new BoundingBox(
                new Point3D(0.0, 0.0, 0.0),
                new Point3D(2.0, 3.0, 4.0));

        assertEquals(24.0, box.volume(), EPSILON);
    }

    @Test
    void shouldIdentifyEmptyBox() {
        BoundingBox box = new BoundingBox(
                new Point3D(1.0, 1.0, 1.0),
                new Point3D(1.0, 2.0, 3.0));

        assertTrue(box.isEmpty());
        assertEquals(0.0, box.volume(), EPSILON);
    }

    @Test
    void shouldIdentifyNonEmptyBox() {
        BoundingBox box = new BoundingBox(
                new Point3D(0.0, 0.0, 0.0),
                new Point3D(1.0, 1.0, 1.0));

        assertFalse(box.isEmpty());
    }

    @Test
    void shouldContainInteriorPoint() {
        BoundingBox box = unitBox();

        assertTrue(box.contains(
                new Point3D(0.5, 0.5, 0.5)));
    }

    @Test
    void shouldContainBoundaryPoint() {
        BoundingBox box = unitBox();

        assertTrue(box.contains(
                new Point3D(1.0, 1.0, 1.0)));
    }

    @Test
    void shouldNotContainOutsidePoint() {
        BoundingBox box = unitBox();

        assertFalse(box.contains(
                new Point3D(1.1, 0.5, 0.5)));
    }

    @Test
    void emptyBoxShouldContainNoPoints() {
        assertFalse(BoundingBox.EMPTY.contains(
                new Point3D(0.0, 0.0, 0.0)));
    }

    @Test
    void shouldDetectIntersection() {
        BoundingBox first = new BoundingBox(
                new Point3D(0.0, 0.0, 0.0),
                new Point3D(4.0, 4.0, 4.0));

        BoundingBox second = new BoundingBox(
                new Point3D(2.0, 2.0, 2.0),
                new Point3D(6.0, 6.0, 6.0));

        assertTrue(first.intersects(second));
        assertTrue(second.intersects(first));
    }

    @Test
    void touchingFacesShouldNotCountAsVolumeIntersection() {
        BoundingBox first = unitBox();

        BoundingBox second = new BoundingBox(
                new Point3D(1.0, 0.0, 0.0),
                new Point3D(2.0, 1.0, 1.0));

        assertFalse(first.intersects(second));
        assertEquals(
                BoundingBox.EMPTY,
                first.intersection(second));
    }

    @Test
    void shouldCalculateIntersection() {
        BoundingBox first = new BoundingBox(
                new Point3D(0.0, 0.0, 0.0),
                new Point3D(4.0, 4.0, 4.0));

        BoundingBox second = new BoundingBox(
                new Point3D(2.0, 1.0, 3.0),
                new Point3D(6.0, 5.0, 7.0));

        BoundingBox intersection =
                first.intersection(second);

        assertEquals(
                new BoundingBox(
                        new Point3D(2.0, 1.0, 3.0),
                        new Point3D(4.0, 4.0, 4.0)),
                intersection);
    }

    @Test
    void shouldReturnEmptyIntersectionForDisjointBoxes() {
        BoundingBox first = unitBox();

        BoundingBox second = new BoundingBox(
                new Point3D(2.0, 2.0, 2.0),
                new Point3D(3.0, 3.0, 3.0));

        BoundingBox intersection =
                first.intersection(second);

        assertTrue(intersection.isEmpty());
        assertEquals(BoundingBox.EMPTY, intersection);
    }

    @Test
    void shouldCalculateIntersectionVolume() {
        BoundingBox first = new BoundingBox(
                new Point3D(0.0, 0.0, 0.0),
                new Point3D(4.0, 4.0, 4.0));

        BoundingBox second = new BoundingBox(
                new Point3D(2.0, 2.0, 2.0),
                new Point3D(6.0, 6.0, 6.0));

        assertEquals(
                8.0,
                first.intersectionVolume(second),
                EPSILON);
    }

    @Test
    void shouldReturnZeroIntersectionVolumeForDisjointBoxes() {
        BoundingBox first = unitBox();

        BoundingBox second = new BoundingBox(
                new Point3D(2.0, 2.0, 2.0),
                new Point3D(3.0, 3.0, 3.0));

        assertEquals(
                0.0,
                first.intersectionVolume(second),
                EPSILON);
    }

    @Test
    void shouldCalculateIntersectionOverUnion() {
        BoundingBox first = new BoundingBox(
                new Point3D(0.0, 0.0, 0.0),
                new Point3D(2.0, 2.0, 2.0));

        BoundingBox second = new BoundingBox(
                new Point3D(1.0, 1.0, 1.0),
                new Point3D(3.0, 3.0, 3.0));

        // Each box has volume 8.
        // Intersection has volume 1.
        // Union has volume 8 + 8 - 1 = 15.
        assertEquals(
                1.0 / 15.0,
                first.intersectionOverUnion(second),
                EPSILON);
    }

    @Test
    void identicalBoxesShouldHaveIntersectionOverUnionOfOne() {
        BoundingBox box = new BoundingBox(
                new Point3D(-1.0, -2.0, -3.0),
                new Point3D(4.0, 5.0, 6.0));

        assertEquals(
                1.0,
                box.intersectionOverUnion(box),
                EPSILON);
    }

    @Test
    void disjointBoxesShouldHaveIntersectionOverUnionOfZero() {
        BoundingBox first = unitBox();

        BoundingBox second = new BoundingBox(
                new Point3D(2.0, 2.0, 2.0),
                new Point3D(3.0, 3.0, 3.0));

        assertEquals(
                0.0,
                first.intersectionOverUnion(second),
                EPSILON);
    }

    @Test
    void twoEmptyBoxesShouldHaveIntersectionOverUnionOfZero() {
        assertEquals(
                0.0,
                BoundingBox.EMPTY.intersectionOverUnion(
                        BoundingBox.EMPTY),
                EPSILON);
    }

    @Test
    void shouldExpandBox() {
        BoundingBox expanded =
                unitBox().expand(1.0);

        assertEquals(
                new BoundingBox(
                        new Point3D(-1.0, -1.0, -1.0),
                        new Point3D(2.0, 2.0, 2.0)),
                expanded);
    }

    @Test
    void shouldRejectNegativeExpansionMargin() {
        assertThrows(
                IllegalArgumentException.class,
                () -> unitBox().expand(-0.1));
    }

    @Test
    void shouldRejectNonFiniteExpansionMargin() {
        assertThrows(
                IllegalArgumentException.class,
                () -> unitBox().expand(Double.NaN));

        assertThrows(
                IllegalArgumentException.class,
                () -> unitBox().expand(
                        Double.POSITIVE_INFINITY));
    }

    @Test
    void shouldCalculateUnion() {
        BoundingBox first = new BoundingBox(
                new Point3D(0.0, 1.0, 2.0),
                new Point3D(3.0, 4.0, 5.0));

        BoundingBox second = new BoundingBox(
                new Point3D(-2.0, 2.0, 1.0),
                new Point3D(2.0, 7.0, 8.0));

        BoundingBox union = first.union(second);

        assertEquals(
                new BoundingBox(
                        new Point3D(-2.0, 1.0, 1.0),
                        new Point3D(3.0, 7.0, 8.0)),
                union);
    }

    @Test
    void unionWithEmptyBoxShouldReturnNonEmptyBox() {
        BoundingBox box = unitBox();

        assertEquals(box, box.union(BoundingBox.EMPTY));
        assertEquals(box, BoundingBox.EMPTY.union(box));
    }

    private static BoundingBox unitBox() {
        return new BoundingBox(
                new Point3D(0.0, 0.0, 0.0),
                new Point3D(1.0, 1.0, 1.0));
    }
}
