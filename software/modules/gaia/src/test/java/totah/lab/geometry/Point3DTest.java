package totah.lab.geometry;


import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.Vector3D;

import static org.junit.jupiter.api.Assertions.*;

class Point3DTest {

    private static final double EPSILON = 1.0E-9;

    @Test
    void shouldCalculateDistance() {
        Point3D first = new Point3D(1, 2, 3);
        Point3D second = new Point3D(4, 6, 3);

        assertEquals(5.0, first.distance(second), EPSILON);
    }

    @Test
    void shouldCalculateZeroDistance() {
        Point3D point = new Point3D(1.2, 3.4, 5.6);

        assertEquals(0.0, point.distance(point), EPSILON);
    }

    @Test
    void shouldRejectNullDistanceArgument() {
        Point3D point = new Point3D(1, 2, 3);

        assertThrows(
                NullPointerException.class,
                () -> point.distance(null)
        );
    }

    @Test
    void shouldCalculateMidpoint() {
        Point3D first = new Point3D(0, 0, 0);
        Point3D second = new Point3D(2, 4, 6);

        Point3D midpoint = first.midpoint(second);

        assertEquals(new Point3D(1, 2, 3), midpoint);
    }

    @Test
    void shouldRejectNullMidpointArgument() {
        Point3D point = new Point3D(1, 2, 3);

        assertThrows(
                NullPointerException.class,
                () -> point.midpoint(null)
        );
    }

    @Test
    void shouldTranslatePointByVector() {
        Point3D result = new Point3D(1, 2, 3)
                .add(new Vector3D(4, 5, 6));

        assertEquals(new Point3D(5, 7, 9), result);
    }

    @Test
    void shouldRejectNullVectorWhenAdding() {
        Point3D point = new Point3D(1, 2, 3);

        assertThrows(
                NullPointerException.class,
                () -> point.add(null)
        );
    }

    @Test
    void shouldTranslatePointAgainstVector() {
        Point3D result = new Point3D(5, 7, 9)
                .subtract(new Vector3D(1, 2, 3));

        assertEquals(new Point3D(4, 5, 6), result);
    }

    @Test
    void shouldRejectNullVectorWhenSubtracting() {
        Point3D point = new Point3D(1, 2, 3);

        assertThrows(
                NullPointerException.class,
                () -> point.subtract(null)
        );
    }

    @Test
    void shouldCreateVectorToAnotherPoint() {
        Point3D first = new Point3D(1, 2, 3);
        Point3D second = new Point3D(5, 7, 9);

        Vector3D result = first.vectorTo(second);

        assertEquals(new Vector3D(4, 5, 6), result);
    }

    @Test
    void shouldCreateVectorFromAnotherPoint() {
        Point3D point = new Point3D(5, 7, 9);
        Point3D origin = new Point3D(1, 2, 3);

        Vector3D result = point.vectorFrom(origin);

        assertEquals(new Vector3D(4, 5, 6), result);
    }

    @Test
    void shouldRejectNullVectorToArgument() {
        Point3D point = new Point3D(1, 2, 3);

        assertThrows(
                NullPointerException.class,
                () -> point.vectorTo(null)
        );
    }

    @Test
    void shouldRejectNullVectorFromArgument() {
        Point3D point = new Point3D(1, 2, 3);

        assertThrows(
                NullPointerException.class,
                () -> point.vectorFrom(null)
        );
    }

    @Test
    void shouldFormatToString() {
        Point3D point = new Point3D(1.23456, 2.34567, 3.45678);

        assertEquals(
                "(1.235, 2.346, 3.457)",
                point.toString()
        );
    }
}
