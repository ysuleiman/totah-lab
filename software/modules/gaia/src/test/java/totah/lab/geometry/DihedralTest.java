package totah.lab.geometry;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Dihedral;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.ZMatrixMath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DihedralTest {

    private static final double EPSILON = 1.0E-9;

    /*
     * Chain along the x-axis with A fixed above B; rotating D around
     * the B-C bond by theta yields a torsion of exactly theta.
     */
    private static final Point3D A = new Point3D(0.0, 1.0, 0.0);
    private static final Point3D B = new Point3D(0.0, 0.0, 0.0);
    private static final Point3D C = new Point3D(1.0, 0.0, 0.0);

    @Test
    void cisArrangementShouldMeasureZero() {
        Point3D d = rotated(0.0);

        assertEquals(
                0.0,
                Dihedral.measureRadians(A, B, C, d),
                EPSILON);
    }

    @Test
    void transArrangementShouldMeasurePi() {
        Point3D d = rotated(Math.PI);

        assertEquals(
                Math.PI,
                Math.abs(Dihedral.measureRadians(A, B, C, d)),
                EPSILON);
    }

    @Test
    void gaucheArrangementShouldMeasureSixtyDegrees() {
        Point3D d = rotated(Math.PI / 3.0);

        assertEquals(
                Math.PI / 3.0,
                Dihedral.measureRadians(A, B, C, d),
                EPSILON);
    }

    @Test
    void negativeTorsionShouldPreserveSign() {
        Point3D d = rotated(-Math.PI / 2.0);

        assertEquals(
                -Math.PI / 2.0,
                Dihedral.measureRadians(A, B, C, d),
                EPSILON);
    }

    @Test
    void shouldMeasureInDegrees() {
        Point3D d = rotated(Math.PI / 3.0);

        assertEquals(
                60.0,
                Dihedral.measureDegrees(A, B, C, d),
                1.0E-7);
    }

    @Test
    void transShouldMeasurePlusOrMinus180Degrees() {
        Point3D d = rotated(Math.PI);

        assertEquals(
                180.0,
                Math.abs(Dihedral.measureDegrees(A, B, C, d)),
                1.0E-7);
    }

    @Test
    void shouldRecoverTorsionConstructedByZMatrixMath() {
        Point3D positionA = new Point3D(1.0, 0.5, -0.25);
        Point3D positionB = new Point3D(0.0, 0.0, 0.0);
        Point3D positionC = new Point3D(-1.2, 0.4, 0.9);

        double[] torsions = {
                0.0,
                Math.PI / 3.0,
                Math.PI / 2.0,
                -2.8,
                3.0
        };

        for (double torsion : torsions) {
            Point3D d =
                    ZMatrixMath.calculatePosition(
                            positionA,
                            positionB,
                            positionC,
                            1.5,
                            1.9,
                            torsion);

            assertEquals(
                    torsion,
                    Dihedral.measureRadians(d, positionA, positionB, positionC),
                    1.0E-9,
                    "torsion " + torsion);
        }
    }

    @Test
    void shouldRejectCollinearPoints() {
        Point3D d = new Point3D(2.0, 0.0, 0.0);

        assertThrows(
                IllegalArgumentException.class,
                () -> Dihedral.measureRadians(A, B, C, d));
    }

    @Test
    void shouldRejectCoincidentCentralPoints() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Dihedral.measureRadians(A, B, B, C));
    }

    @Test
    void shouldRejectNullPoints() {
        assertThrows(
                NullPointerException.class,
                () -> Dihedral.measureRadians(null, B, C, A));
        assertThrows(
                NullPointerException.class,
                () -> Dihedral.measureRadians(A, B, C, null));
    }

    private static Point3D rotated(double theta) {
        return new Point3D(
                1.0,
                Math.cos(theta),
                Math.sin(theta));
    }
}
