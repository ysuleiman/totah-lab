package totah.lab.gaia.geometry;

import java.util.Objects;

/**
 * Measures the torsion (dihedral) angle defined by four points.
 *
 * <p>The angle follows the standard IUPAC sign convention: a value of
 * zero corresponds to a cis (synperiplanar) arrangement and &pm;&pi;
 * (&pm;180&deg;) to a trans (antiperiplanar) arrangement. This is the
 * measurable inverse of {@link ZMatrixMath#calculatePosition}: if point
 * D was constructed from A, B and C with a given torsion, then
 * {@code measureRadians(D, A, B, C)} recovers that torsion.
 */
public final class Dihedral {

    private static final double VECTOR_EPSILON = 1.0e-10;

    private Dihedral() {
    }

    /**
     * Measures the A-B-C-D torsion angle in radians, in the range
     * (-&pi;, &pi;].
     *
     * @param a first point
     * @param b second point
     * @param c third point
     * @param d fourth point
     * @return signed torsion angle in radians; zero is cis,
     *         &pm;&pi; is trans
     * @throws IllegalArgumentException if any three consecutive points
     *         are collinear (the torsion is then undefined)
     */
    public static double measureRadians(
            Point3D a,
            Point3D b,
            Point3D c,
            Point3D d) {

        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        Objects.requireNonNull(c, "c");
        Objects.requireNonNull(d, "d");

        /*
         * Bond vectors along the A-B-C-D chain.
         */
        Vector3D ba = b.vectorTo(a);
        Vector3D bc = b.vectorTo(c);
        Vector3D cd = c.vectorTo(d);

        Vector3D axis =
                normalize(bc, "b and c must be distinct");

        /*
         * Components of B->A and C->D perpendicular to the central
         * B-C bond.
         */
        Vector3D perpendicularA =
                ba.subtract(axis.scale(ba.dot(axis)));
        Vector3D perpendicularD =
                cd.subtract(axis.scale(cd.dot(axis)));

        Vector3D u =
                normalize(
                        perpendicularA,
                        "a, b and c must not be collinear");
        Vector3D v =
                normalize(
                        perpendicularD,
                        "b, c and d must not be collinear");

        double x = u.dot(v);
        double y = axis.cross(u).dot(v);

        return Math.atan2(y, x);
    }

    /**
     * Measures the A-B-C-D torsion angle in degrees, in the range
     * (-180, 180]. Zero is cis, &pm;180 is trans.
     *
     * @param a first point
     * @param b second point
     * @param c third point
     * @param d fourth point
     * @return signed torsion angle in degrees
     */
    public static double measureDegrees(
            Point3D a,
            Point3D b,
            Point3D c,
            Point3D d) {

        return Math.toDegrees(measureRadians(a, b, c, d));
    }

    private static Vector3D normalize(
            Vector3D vector,
            String errorMessage) {

        double length = vector.magnitude();

        if (!Double.isFinite(length)
                || length < VECTOR_EPSILON) {
            throw new IllegalArgumentException(errorMessage);
        }

        return vector.scale(1.0 / length);
    }
}
