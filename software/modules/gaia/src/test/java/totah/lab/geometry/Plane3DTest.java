package totah.lab.geometry;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Plane3D;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.Vector3D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Plane3DTest {

    private static final double EPSILON = 1.0E-9;

    private static final List<Point3D> XY_PLANE_AT_Z2 = List.of(
            new Point3D(0.0, 0.0, 2.0),
            new Point3D(2.0, 0.0, 2.0),
            new Point3D(0.0, 2.0, 2.0),
            new Point3D(2.0, 2.0, 2.0));

    @Test
    void shouldFitXyPlaneExactly() {
        Plane3D plane = Plane3D.fit(XY_PLANE_AT_Z2);

        assertEquals(new Point3D(1.0, 1.0, 2.0), plane.centroid());
        assertVectorEquals(new Vector3D(0.0, 0.0, 1.0), plane.normal());
    }

    @Test
    void shouldFitXzPlaneWithCanonicalNormal() {
        Plane3D plane = Plane3D.fit(List.of(
                new Point3D(0.0, -3.0, 0.0),
                new Point3D(3.0, -3.0, 0.0),
                new Point3D(0.0, -3.0, 4.0),
                new Point3D(3.0, -3.0, 4.0)));

        assertEquals(new Point3D(1.5, -3.0, 2.0), plane.centroid());
        assertVectorEquals(new Vector3D(0.0, 1.0, 0.0), plane.normal());
    }

    @Test
    void shouldFitYzPlaneWithCanonicalNormal() {
        Plane3D plane = Plane3D.fit(List.of(
                new Point3D(-1.0, 0.0, 0.0),
                new Point3D(-1.0, 5.0, 0.0),
                new Point3D(-1.0, 0.0, 7.0),
                new Point3D(-1.0, 5.0, 7.0)));

        assertEquals(new Point3D(-1.0, 2.5, 3.5), plane.centroid());
        assertVectorEquals(new Vector3D(1.0, 0.0, 0.0), plane.normal());
    }

    @Test
    void shouldFitTiltedPlaneWithKnownNormal() {
        /*
         * Plane x + y + z = 6, normal (1, 1, 1) / sqrt(3).
         */
        Plane3D plane = Plane3D.fit(List.of(
                new Point3D(6.0, 0.0, 0.0),
                new Point3D(0.0, 6.0, 0.0),
                new Point3D(0.0, 0.0, 6.0),
                new Point3D(2.0, 2.0, 2.0)));

        assertPointEquals(new Point3D(2.0, 2.0, 2.0), plane.centroid());
        double component = 1.0 / Math.sqrt(3.0);
        assertVectorEquals(
                new Vector3D(component, component, component),
                plane.normal());
        assertEquals(
                -2.0 * Math.sqrt(3.0),
                plane.distanceTo(new Point3D(0.0, 0.0, 0.0)),
                EPSILON);
    }

    @Test
    void shouldFitHexagonalRingInXyPlane() {
        List<Point3D> hexagon = ring(
                new Point3D(0.0, 0.0, 1.5),
                new Vector3D(0.0, 0.0, 1.0),
                1.4,
                6);

        Plane3D plane = Plane3D.fit(hexagon);

        assertPointEquals(new Point3D(0.0, 0.0, 1.5), plane.centroid());
        assertVectorEquals(new Vector3D(0.0, 0.0, 1.0), plane.normal());
        for (Point3D point : hexagon) {
            assertEquals(0.0, plane.absoluteDistanceTo(point), EPSILON);
        }
    }

    @Test
    void shouldFitPentagonalRingOnTiltedPlane() {
        Point3D center = new Point3D(1.0, 2.0, 3.0);
        Vector3D ringNormal = new Vector3D(0.0, 1.0, 1.0).normalize();

        List<Point3D> pentagon = ring(center, ringNormal, 1.2, 5);

        Plane3D plane = Plane3D.fit(pentagon);

        assertPointEquals(center, plane.centroid());
        assertVectorEquals(ringNormal, plane.normal());
        for (Point3D point : pentagon) {
            assertEquals(0.0, plane.absoluteDistanceTo(point), EPSILON);
        }
    }

    @Test
    void shouldReportSignedDistanceByNormalDirection() {
        Plane3D plane = Plane3D.fit(XY_PLANE_AT_Z2);

        assertEquals(
                3.0,
                plane.distanceTo(new Point3D(0.25, 0.75, 5.0)),
                EPSILON);
        assertEquals(
                -4.0,
                plane.distanceTo(new Point3D(0.25, 0.75, -2.0)),
                EPSILON);
        assertEquals(
                0.0,
                plane.distanceTo(new Point3D(0.25, 0.75, 2.0)),
                EPSILON);
        assertEquals(
                4.0,
                plane.absoluteDistanceTo(new Point3D(0.25, 0.75, -2.0)),
                EPSILON);
    }

    @Test
    void shouldProjectOntoAxisAlignedPlane() {
        Plane3D plane = Plane3D.fit(XY_PLANE_AT_Z2);

        assertPointEquals(
                new Point3D(0.25, 0.75, 2.0),
                plane.project(new Point3D(0.25, 0.75, 5.0)));
        assertPointEquals(
                new Point3D(0.25, 0.75, 2.0),
                plane.project(new Point3D(0.25, 0.75, -1.0)));
    }

    @Test
    void projectionShouldLandOnTiltedPlane() {
        Plane3D plane = Plane3D.fit(ring(
                new Point3D(1.0, 2.0, 3.0),
                new Vector3D(0.0, 1.0, 1.0).normalize(),
                1.2,
                5));

        Point3D offPlane = new Point3D(4.0, -1.0, 8.0);
        Point3D projected = plane.project(offPlane);

        assertEquals(0.0, plane.distanceTo(projected), EPSILON);
        assertEquals(
                plane.absoluteDistanceTo(offPlane),
                projected.distance(offPlane),
                EPSILON);
    }

    @Test
    void projectionShouldRoundTripWithSignedDistance() {
        Plane3D plane = Plane3D.fit(List.of(
                new Point3D(6.0, 0.0, 0.0),
                new Point3D(0.0, 6.0, 0.0),
                new Point3D(0.0, 0.0, 6.0),
                new Point3D(2.0, 2.0, 2.0)));

        Point3D point = new Point3D(-3.0, 7.5, 1.25);
        Point3D roundTripped = plane.project(point)
                .add(plane.normal().scale(plane.distanceTo(point)));

        assertPointEquals(point, roundTripped);
    }

    @Test
    void shouldMeasureZeroAngleBetweenParallelPlanes() {
        Plane3D first = Plane3D.fit(XY_PLANE_AT_Z2);
        Plane3D second = Plane3D.fit(ring(
                new Point3D(-4.0, 9.0, -7.0),
                new Vector3D(0.0, 0.0, -1.0),
                2.0,
                6));

        assertEquals(0.0, first.angleToDegrees(second), EPSILON);
    }

    @Test
    void shouldMeasureNinetyDegreesBetweenPerpendicularPlanes() {
        Plane3D xyPlane = Plane3D.fit(XY_PLANE_AT_Z2);
        Plane3D xzPlane = Plane3D.fit(ring(
                new Point3D(1.0, 2.0, 3.0),
                new Vector3D(0.0, -1.0, 0.0),
                1.5,
                5));

        assertEquals(90.0, xyPlane.angleToDegrees(xzPlane), EPSILON);
    }

    @Test
    void shouldMeasureFortyFiveDegreesBetweenTiltedPlanes() {
        Plane3D xyPlane = Plane3D.fit(XY_PLANE_AT_Z2);
        Plane3D tilted = Plane3D.fit(ring(
                new Point3D(0.0, 0.0, 0.0),
                new Vector3D(1.0, 0.0, 1.0).normalize(),
                1.5,
                6));

        assertEquals(45.0, xyPlane.angleToDegrees(tilted), EPSILON);
    }

    @Test
    void shouldFoldObtuseNormalAngleToAcutePlaneAngle() {
        /*
         * Normals 170 degrees apart describe planes only 10 degrees
         * apart, because planes are unoriented.
         */
        Plane3D first = Plane3D.fit(XY_PLANE_AT_Z2);
        Plane3D second = Plane3D.fit(ring(
                new Point3D(0.0, 0.0, 0.0),
                new Vector3D(
                        Math.sin(Math.toRadians(170.0)),
                        0.0,
                        Math.cos(Math.toRadians(170.0))),
                1.5,
                6));

        assertEquals(10.0, first.angleToDegrees(second), 1.0E-7);
    }

    @Test
    void shouldProduceIdenticalNormalForPermutedPointOrder() {
        List<Point3D> points = new ArrayList<>(List.of(
                new Point3D(6.0, 0.0, 0.0),
                new Point3D(0.0, 6.0, 0.0),
                new Point3D(0.0, 0.0, 6.0),
                new Point3D(2.0, 2.0, 2.0),
                new Point3D(4.0, 1.0, 1.0),
                new Point3D(1.0, 4.0, 1.0)));

        Vector3D reference = Plane3D.fit(points).normal();

        for (int seed = 1; seed <= 5; seed++) {
            List<Point3D> shuffled = new ArrayList<>(points);
            Collections.shuffle(shuffled, new java.util.Random(seed));

            Vector3D normal = Plane3D.fit(shuffled).normal();

            assertVectorEquals(reference, normal);
            assertTrue(reference.dot(normal) > 0.0);
        }
    }

    @Test
    void shouldProduceIdenticalNormalForReversedPointOrder() {
        List<Point3D> hexagon = ring(
                new Point3D(0.0, 0.0, 1.5),
                new Vector3D(0.0, 0.0, 1.0),
                1.4,
                6);

        List<Point3D> reversed = new ArrayList<>(hexagon);
        Collections.reverse(reversed);

        assertVectorEquals(
                Plane3D.fit(hexagon).normal(),
                Plane3D.fit(reversed).normal());
    }

    @Test
    void shouldKeepCanonicalSignWhenPlaneIsReflected() {
        List<Point3D> reflected = XY_PLANE_AT_Z2.stream()
                .map(point -> new Point3D(point.x(), point.y(), -point.z()))
                .toList();

        Plane3D plane = Plane3D.fit(reflected);

        assertVectorEquals(new Vector3D(0.0, 0.0, 1.0), plane.normal());
    }

    @Test
    void shouldRejectFewerThanThreePoints() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Plane3D.fit(List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> Plane3D.fit(List.of(new Point3D(0.0, 0.0, 0.0))));
        assertThrows(
                IllegalArgumentException.class,
                () -> Plane3D.fit(List.of(
                        new Point3D(0.0, 0.0, 0.0),
                        new Point3D(1.0, 1.0, 1.0))));
    }

    @Test
    void shouldRejectCoincidentPoints() {
        List<Point3D> coincident = List.of(
                new Point3D(1.0, 2.0, 3.0),
                new Point3D(1.0, 2.0, 3.0),
                new Point3D(1.0, 2.0, 3.0),
                new Point3D(1.0, 2.0, 3.0));

        assertThrows(
                IllegalArgumentException.class,
                () -> Plane3D.fit(coincident));
    }

    @Test
    void shouldRejectCollinearPoints() {
        List<Point3D> collinear = List.of(
                new Point3D(0.0, 0.0, 0.0),
                new Point3D(1.0, 1.0, 1.0),
                new Point3D(2.0, 2.0, 2.0),
                new Point3D(3.0, 3.0, 3.0));

        assertThrows(
                IllegalArgumentException.class,
                () -> Plane3D.fit(collinear));
    }

    @Test
    void shouldRejectNearlyCollinearPoints() {
        List<Point3D> nearlyCollinear = List.of(
                new Point3D(0.0, 0.0, 0.0),
                new Point3D(1.0, 0.0, 0.0),
                new Point3D(2.0, 0.0, 0.0),
                new Point3D(1.0, 1.0e-8, 0.0));

        assertThrows(
                IllegalArgumentException.class,
                () -> Plane3D.fit(nearlyCollinear));
    }

    @Test
    void shouldRejectNullPointList() {
        assertThrows(
                NullPointerException.class,
                () -> Plane3D.fit(null));
    }

    @Test
    void shouldRejectNullPointElement() {
        List<Point3D> points = new ArrayList<>();
        points.add(new Point3D(0.0, 0.0, 0.0));
        points.add(new Point3D(1.0, 0.0, 0.0));
        points.add(null);

        assertThrows(
                NullPointerException.class,
                () -> Plane3D.fit(points));
    }

    @Test
    void shouldRejectNullArgumentsOnInstanceMethods() {
        Plane3D plane = Plane3D.fit(XY_PLANE_AT_Z2);

        assertThrows(
                NullPointerException.class,
                () -> plane.distanceTo(null));
        assertThrows(
                NullPointerException.class,
                () -> plane.project(null));
        assertThrows(
                NullPointerException.class,
                () -> plane.angleToDegrees(null));
    }

    /*
     * Builds {@code count} points on a circle of the given radius
     * around {@code center}, lying in the plane through {@code center}
     * perpendicular to {@code normal}.
     */
    private static List<Point3D> ring(
            Point3D center,
            Vector3D normal,
            double radius,
            int count) {

        Vector3D axis = Math.abs(normal.x()) < 0.9
                ? new Vector3D(1.0, 0.0, 0.0)
                : new Vector3D(0.0, 1.0, 0.0);
        Vector3D u = axis
                .subtract(normal.scale(axis.dot(normal)))
                .normalize();
        Vector3D v = normal.cross(u);

        List<Point3D> points = new ArrayList<>();
        for (int k = 0; k < count; k++) {
            double theta = 2.0 * Math.PI * k / count;
            points.add(center
                    .add(u.scale(radius * Math.cos(theta)))
                    .add(v.scale(radius * Math.sin(theta))));
        }
        return points;
    }

    private static void assertPointEquals(
            Point3D expected,
            Point3D actual) {

        assertEquals(expected.x(), actual.x(), EPSILON);
        assertEquals(expected.y(), actual.y(), EPSILON);
        assertEquals(expected.z(), actual.z(), EPSILON);
    }

    private static void assertVectorEquals(
            Vector3D expected,
            Vector3D actual) {

        assertEquals(expected.x(), actual.x(), EPSILON);
        assertEquals(expected.y(), actual.y(), EPSILON);
        assertEquals(expected.z(), actual.z(), EPSILON);
    }
}
