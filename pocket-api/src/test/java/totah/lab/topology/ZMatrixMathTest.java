package totah.lab.topology;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import totah.lab.protein.Point3D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ZMatrixMath rebuilds an atom position from internal coordinates
 * (bond length / angle / dihedral against three anchors). Tests use
 * hand-computed constructions plus anchor-independent invariants.
 */
public class ZMatrixMathTest {

    private static final double EPS = 1e-9;
    private static final double EPSILON = 1e-5;

    // Right-angle anchor frame: u = (-1,0,0), v = (0,-1,0), w = (0,0,1)
    private static final Point3D A = new Point3D(0, 0, 0);
    private static final Point3D B = new Point3D(1, 0, 0);
    private static final Point3D C = new Point3D(1, 1, 0);

    /**
     * Standard dihedral convention pinned here: delta = 0 -> cis/eclipsed
     * (D on the SAME side of the A-B axis as C), delta = PI -> trans/anti
     * (D on the OPPOSITE side). Sign of the out-of-plane rotation follows the
     * right-handed frame w = u x vCB (see ZMatrixMath).
     */
    @Test
    public void zeroDihedralPlacesAtomInPlaneTowardC() {
        // vBA = (-1,0,0) -> u = (-1,0,0). vCB = (0,-1,0). w = u x vCB = (0,0,1). v = w x u = (0,-1,0).
        // C lies on the +y side (direction -v). cis (delta=0): y = 1.5 * cos(0 + PI) = -1.5
        // -> Global = A - 1.5*v = (0, 1.5, 0), same side as C.
        Point3D d = ZMatrixMath.calculatePosition(A, B, C, 1.5, Math.PI / 2, 0.0);
        assertPointEquals(new Point3D(0, 1.5, 0), d);
    }

    @Test
    public void piDihedralPlacesAtomInPlaneOppositeC() {
        // trans (delta=PI): y = 1.5 * cos(2*PI) = 1.5 -> Global = A + 1.5*v = (0, -1.5, 0)
        Point3D d = ZMatrixMath.calculatePosition(A, B, C, 1.5, Math.PI / 2, Math.PI);
        assertPointEquals(new Point3D(0, -1.5, 0), d);
    }

    @Test
    public void halfPiDihedralLiftsAtomOutOfPlane() {
        // z = 1.5 * sin(PI/2 + PI) = -1.5. Global = A - 1.5*w = (0, 0, -1.5)
        Point3D d = ZMatrixMath.calculatePosition(A, B, C, 1.5, Math.PI / 2, Math.PI / 2);
        assertPointEquals(new Point3D(0, 0, -1.5), d);

        // z = 1.5 * sin(-PI/2 + PI) = 1.5. Global = A + 1.5*w = (0, 0, 1.5)
        Point3D dNeg = ZMatrixMath.calculatePosition(A, B, C, 1.5, Math.PI / 2, -Math.PI / 2);
        assertPointEquals(new Point3D(0, 0, 1.5), dNeg);
    }

    @Test
    public void acuteAngleConstructionMatchesHandComputedPosition() {
        // angle = 60 deg, dihedral = 0.0 (cis)
        // x = -2 * cos(60) = -1.0. y = 2 * sin(60) * cos(PI) = -sqrt(3). z = 0
        // Global = A + (-1.0)*u + (-sqrt(3))*v = (-1)(-1,0,0) + (-sqrt(3))(0,-1,0) = (1.0, Math.sqrt(3), 0)
        Point3D d = ZMatrixMath.calculatePosition(A, B, C, 2.0, Math.PI / 3, 0.0);
        assertPointEquals(new Point3D(1.0, Math.sqrt(3), 0), d);
    }

    @Test
    public void inputDihedralIsRecoveredByIndependentMeasurement() {
        // Anchor-independent: rebuild D for several dihedrals, then measure the
        // D-A-B-C dihedral back with the standard atan2 formula.
        double[] dihedrals = {0.0, Math.PI / 2, -Math.PI / 2, Math.PI, 2.3, -0.7};
        for (double delta : dihedrals) {
            Point3D d = ZMatrixMath.calculatePosition(A, B, C, 1.5, Math.PI / 2, delta);
            assertEquals(delta, measuredDihedral(d, A, B, C), 1e-9,
                    "rebuilt dihedral D-A-B-C does not match the input " + delta);
        }
    }

    @Test
    public void bondLengthIsExactForArbitraryAnchors() {
        Point3D a = new Point3D(1, 2, 3);
        Point3D b = new Point3D(4, 6, 3);
        Point3D c = new Point3D(4, 6, 9);
        Point3D d = ZMatrixMath.calculatePosition(a, b, c, 2.25, 1.1, 0.7);
        assertEquals(2.25, distance(a, d), 1e-9, "rebuilt bond length drifted");
    }

    @Test
    public void bondAngleIsRecoveredForArbitraryAnchors() {
        Point3D a = new Point3D(1, 2, 3);
        Point3D b = new Point3D(4, 6, 3);
        Point3D c = new Point3D(4, 6, 9);
        double angle = 1.9;
        Point3D d = ZMatrixMath.calculatePosition(a, b, c, 1.8, angle, -0.4);
        assertEquals(angle, angleBetween(d, a, b), 1e-9,
                "rebuilt bond angle D-A-B does not match the input");
    }

    @Test
    public void tetrahedralHydrogenPlacementKeepsBondLength() {
        Point3D n = new Point3D(0, 0, 0);
        Point3D ca = new Point3D(1.46, 0, 0);
        Point3D cPrev = new Point3D(2.0, 1.4, 0);
        double tetra = Math.toRadians(109.5);
        Point3D h = ZMatrixMath.calculatePosition(n, ca, cPrev, 1.0, tetra, Math.PI);
        assertEquals(1.0, distance(n, h), 1e-9, "N-H bond length");
    }

    @Test
    public void tetrahedralHydrogenPlacementMatchesDocumentedAngle() {
        Point3D n = new Point3D(0, 0, 0);
        Point3D ca = new Point3D(1.46, 0, 0);
        Point3D cPrev = new Point3D(2.0, 1.4, 0);
        double tetra = Math.toRadians(109.5);
        Point3D h = ZMatrixMath.calculatePosition(n, ca, cPrev, 1.0, tetra, Math.PI);
        assertEquals(tetra, angleBetween(h, n, ca), 1e-9, "H-N-CA angle");
    }

    @Test
    @DisplayName("Should accurately calculate a standard coplanar 90-degree projection")
    void testStandardProjection() {
        Point3D posA = new Point3D(1.0, 0.0, 0.0);
        Point3D posB = new Point3D(0.0, 0.0, 0.0);
        Point3D posC = new Point3D(0.0, 1.0, 0.0);

        double bondLength = 1.0;
        double bondAngle = Math.toRadians(90.0);
        double dihedral = Math.toRadians(0.0);

        Point3D posD = ZMatrixMath.calculatePosition(posA, posB, posC, bondLength, bondAngle, dihedral);

        // Standard convention: dihedral = 0 is cis, so D lands on the same side as C (+y here)
        assertNotNull(posD);
        assertEquals(1.0, posD.x(), EPSILON);
        assertEquals(1.0, posD.y(), EPSILON);
        assertEquals(0.0, posD.z(), EPSILON);
    }

    @Test
    @DisplayName("Should intercept overlapping atoms without throwing an exception or returning NaN values")
    void testOverlappingAtomsGuard() {
        Point3D posA = new Point3D(1.0, 1.0, 1.0);
        Point3D posB = new Point3D(1.0, 1.0, 1.0);
        Point3D posC = new Point3D(0.0, 0.0, 0.0);

        Point3D posD = ZMatrixMath.calculatePosition(posA, posB, posC, 1.0, 1.0, 1.0);

        assertEquals(posA.x(), posD.x());
        assertEquals(posA.y(), posD.y());
        assertEquals(posA.z(), posD.z());
    }

    @Test
    @DisplayName("Should successfully handle parallel collinear heavy atoms without generating NaN errors")
    void testCollinearAtomsGuard() {
        Point3D posA = new Point3D(2.0, 0.0, 0.0);
        Point3D posB = new Point3D(1.0, 0.0, 0.0);
        Point3D posC = new Point3D(0.0, 0.0, 0.0);

        double bondLength = 1.0;
        double bondAngle = Math.toRadians(90.0);
        double dihedral = Math.toRadians(0.0);

        Point3D posD = ZMatrixMath.calculatePosition(posA, posB, posC, bondLength, bondAngle, dihedral);

        assertFalse(Double.isNaN(posD.x()), "X must be a number");
        assertFalse(Double.isNaN(posD.y()), "Y must be a number");
        assertFalse(Double.isNaN(posD.z()), "Z must be a number");
        assertEquals(2.0, posD.x(), EPSILON);
    }

    // ==================== PRIVATE UNIT TESTING UTILITIES ====================

    /** Angle p1-p2-p3 at p2, from the dot product (independent of ZMatrixMath). */
    private static double angleBetween(Point3D p1, Point3D p2, Point3D p3) {
        double ux = p1.x() - p2.x(), uy = p1.y() - p2.y(), uz = p1.z() - p2.z();
        double vx = p3.x() - p2.x(), vy = p3.y() - p2.y(), vz = p3.z() - p2.z();
        double dot = ux * vx + uy * vy + uz * vz;
        double nu = Math.sqrt(ux * ux + uy * uy + uz * uz);
        double nv = Math.sqrt(vx * vx + vy * vy + vz * vz);
        return Math.acos(dot / (nu * nv));
    }

    /**
     * Standard IUPAC dihedral d-a-b-c measured with the atan2 projection
     * formula (independent of ZMatrixMath). cis/eclipsed = 0, trans/anti = PI.
     */
    private static double measuredDihedral(Point3D d, Point3D a, Point3D b, Point3D c) {
        // Axis along a -> b
        double bx = b.x() - a.x(), by = b.y() - a.y(), bz = b.z() - a.z();
        double bl = Math.sqrt(bx * bx + by * by + bz * bz);
        bx /= bl; by /= bl; bz /= bl;

        // Project (d - a) and (c - b) onto the plane perpendicular to the axis
        double dax = d.x() - a.x(), day = d.y() - a.y(), daz = d.z() - a.z();
        double cbx = c.x() - b.x(), cby = c.y() - b.y(), cbz = c.z() - b.z();
        double dotDA = dax * bx + day * by + daz * bz;
        double dotCB = cbx * bx + cby * by + cbz * bz;
        double vx = dax - dotDA * bx, vy = day - dotDA * by, vz = daz - dotDA * bz;
        double wx = cbx - dotCB * bx, wy = cby - dotCB * by, wz = cbz - dotCB * bz;

        double x = vx * wx + vy * wy + vz * wz;
        // cross(axis, v) . w
        double cx = by * vz - bz * vy, cy = bz * vx - bx * vz, cz = bx * vy - by * vx;
        double y = cx * wx + cy * wy + cz * wz;
        return Math.atan2(y, x);
    }

    private static double distance(Point3D p1, Point3D p2) {
        double dx = p1.x() - p2.x();
        double dy = p1.y() - p2.y();
        double dz = p1.z() - p2.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void assertPointEquals(Point3D expected, Point3D actual) {
        assertEquals(expected.x(), actual.x(), EPS, "x mismatch");
        assertEquals(expected.y(), actual.y(), EPS, "y mismatch");
        assertEquals(expected.z(), actual.z(), EPS, "z mismatch");
    }
}
