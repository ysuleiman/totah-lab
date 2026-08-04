package totah.lab.geometry;


import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.Vector3D;

import static org.junit.jupiter.api.Assertions.*;

class Vector3DTest {

    private static final double EPSILON = 1.0e-9;

    @Test
    void shouldCreateZeroVector() {
        assertEquals(0.0, Vector3D.ZERO.x(), EPSILON);
        assertEquals(0.0, Vector3D.ZERO.y(), EPSILON);
        assertEquals(0.0, Vector3D.ZERO.z(), EPSILON);
        assertTrue(Vector3D.ZERO.isZero());
    }

    @Test
    void shouldCalculateMagnitude() {
        Vector3D vector = new Vector3D(3.0, 4.0, 12.0);

        assertEquals(13.0, vector.magnitude(), EPSILON);
    }

    @Test
    void shouldCalculateMagnitudeSquared() {
        Vector3D vector = new Vector3D(3.0, 4.0, 12.0);

        assertEquals(169.0, vector.magnitudeSquared(), EPSILON);
    }

    @Test
    void shouldNormalizeVector() {
        Vector3D normalized = new Vector3D(3.0, 0.0, 4.0).normalize();

        assertEquals(1.0, normalized.magnitude(), EPSILON);
        assertEquals(0.6, normalized.x(), EPSILON);
        assertEquals(0.0, normalized.y(), EPSILON);
        assertEquals(0.8, normalized.z(), EPSILON);
    }

    @Test
    void shouldThrowWhenNormalizingZeroVector() {
        assertThrows(
                IllegalStateException.class,
                () -> Vector3D.ZERO.normalize());
    }

    @Test
    void shouldNegateVector() {
        Vector3D vector = new Vector3D(1.0, -2.0, 3.0).negate();

        assertEquals(-1.0, vector.x(), EPSILON);
        assertEquals(2.0, vector.y(), EPSILON);
        assertEquals(-3.0, vector.z(), EPSILON);
    }

    @Test
    void shouldAddVectors() {
        Vector3D result =
                new Vector3D(1, 2, 3)
                        .add(new Vector3D(4, 5, 6));

        assertEquals(new Vector3D(5, 7, 9), result);
    }

    @Test
    void shouldSubtractVectors() {
        Vector3D result =
                new Vector3D(5, 7, 9)
                        .subtract(new Vector3D(1, 2, 3));

        assertEquals(new Vector3D(4, 5, 6), result);
    }

    @Test
    void shouldMultiplyVector() {
        Vector3D result =
                new Vector3D(1, 2, 3).multiply(2.5);

        assertEquals(new Vector3D(2.5, 5.0, 7.5), result);
    }

    @Test
    void shouldDivideVector() {
        Vector3D result =
                new Vector3D(2, 4, 6).divide(2);

        assertEquals(new Vector3D(1, 2, 3), result);
    }

    @Test
    void shouldThrowWhenDividingByZero() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Vector3D(1, 2, 3).divide(0));
    }

    @Test
    void shouldCalculateDotProduct() {
        double dot =
                new Vector3D(1, 2, 3)
                        .dot(new Vector3D(4, 5, 6));

        assertEquals(32.0, dot, EPSILON);
    }

    @Test
    void shouldCalculateCrossProduct() {
        Vector3D cross =
                new Vector3D(1, 0, 0)
                        .cross(new Vector3D(0, 1, 0));

        assertEquals(new Vector3D(0, 0, 1), cross);
    }

    @Test
    void shouldCalculateAngleBetweenVectors() {
        double angle =
                new Vector3D(1, 0, 0)
                        .angle(new Vector3D(0, 1, 0));

        assertEquals(Math.PI / 2.0, angle, EPSILON);
    }

    @Test
    void shouldThrowWhenAngleUsesZeroVector() {
        assertThrows(
                IllegalStateException.class,
                () -> Vector3D.ZERO.angle(new Vector3D(1, 0, 0)));
    }

    @Test
    void shouldCalculateDistance() {
        double distance =
                new Vector3D(1, 2, 3)
                        .distance(new Vector3D(4, 6, 3));

        assertEquals(5.0, distance, EPSILON);
    }

    @Test
    void shouldDetectZeroVector() {
        assertTrue(Vector3D.ZERO.isZero());
        assertFalse(new Vector3D(1, 0, 0).isZero());
    }

    @Test
    void shouldCreateVectorBetweenPoints() {
        Point3D first = new Point3D(1, 2, 3);
        Point3D second = new Point3D(5, 7, 9);

        Vector3D vector =
                Vector3D.between(first, second);

        assertEquals(new Vector3D(4, 5, 6), vector);
    }

    @Test
    void shouldRejectNullPointArguments() {
        Point3D point = new Point3D(0, 0, 0);

        assertThrows(
                NullPointerException.class,
                () -> Vector3D.between(null, point));

        assertThrows(
                NullPointerException.class,
                () -> Vector3D.between(point, null));
    }
}
