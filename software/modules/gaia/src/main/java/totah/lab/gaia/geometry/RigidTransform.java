package totah.lab.gaia.geometry;

import java.util.List;
import java.util.Objects;

/**
 * Immutable rigid-body transform in three-dimensional space.
 *
 * <p>A point is transformed according to:</p>
 *
 * <pre>
 * transformed = rotation * point + translation
 * </pre>
 *
 * <p>The rotation matrix is stored internally in row-major order.
 * Public constructors validate externally supplied matrices as proper
 * rotations: approximately orthonormal with determinant {@code +1}.
 * Internally composed and inverted transforms bypass that strict
 * validation to avoid rejecting mathematically valid results because
 * of accumulated floating-point error.</p>
 */
public final class RigidTransform {

    private static final double ORTHONORMAL_TOLERANCE = 1.0e-6;
    private static final double DETERMINANT_TOLERANCE = 1.0e-6;

    private final double r00;
    private final double r01;
    private final double r02;

    private final double r10;
    private final double r11;
    private final double r12;

    private final double r20;
    private final double r21;
    private final double r22;

    private final Point3D translation;

    /**
     * Creates a transform from individual row-major rotation-matrix values
     * and a translation vector.
     *
     * @throws IllegalArgumentException if any value is non-finite or the
     *                                  matrix is not a proper rotation
     */
    public RigidTransform(
            double r00,
            double r01,
            double r02,
            double r10,
            double r11,
            double r12,
            double r20,
            double r21,
            double r22,
            Point3D translation
    ) {
        this(
                r00,
                r01,
                r02,
                r10,
                r11,
                r12,
                r20,
                r21,
                r22,
                translation,
                true
        );
    }

    /**
     * Creates a transform from a 3-by-3 row-major rotation matrix and a
     * translation vector.
     *
     * @throws IllegalArgumentException if the matrix is malformed, contains
     *                                  non-finite values, or is not a proper
     *                                  rotation
     */
    public RigidTransform(
            double[][] rotation,
            Point3D translation
    ) {
        this(
                matrixValue(rotation, 0, 0),
                matrixValue(rotation, 0, 1),
                matrixValue(rotation, 0, 2),

                matrixValue(rotation, 1, 0),
                matrixValue(rotation, 1, 1),
                matrixValue(rotation, 1, 2),

                matrixValue(rotation, 2, 0),
                matrixValue(rotation, 2, 1),
                matrixValue(rotation, 2, 2),

                translation,
                true
        );
    }

    /**
     * Internal constructor used for both validated external construction and
     * trusted numerical operations such as composition and inversion.
     */
    private RigidTransform(
            double r00,
            double r01,
            double r02,
            double r10,
            double r11,
            double r12,
            double r20,
            double r21,
            double r22,
            Point3D translation,
            boolean validateRotation
    ) {
        requireFinite(r00, "r00");
        requireFinite(r01, "r01");
        requireFinite(r02, "r02");

        requireFinite(r10, "r10");
        requireFinite(r11, "r11");
        requireFinite(r12, "r12");

        requireFinite(r20, "r20");
        requireFinite(r21, "r21");
        requireFinite(r22, "r22");

        this.translation = requireFinitePoint(
                Objects.requireNonNull(
                        translation,
                        "translation"
                ),
                "translation"
        );

        this.r00 = r00;
        this.r01 = r01;
        this.r02 = r02;

        this.r10 = r10;
        this.r11 = r11;
        this.r12 = r12;

        this.r20 = r20;
        this.r21 = r21;
        this.r22 = r22;

        if (validateRotation) {
            validateRotation();
        }
    }

    /**
     * Returns the identity transform.
     */
    public static RigidTransform identity() {
        return new RigidTransform(
                1.0, 0.0, 0.0,
                0.0, 1.0, 0.0,
                0.0, 0.0, 1.0,
                new Point3D(0.0, 0.0, 0.0),
                false
        );
    }

    /**
     * Creates a translation-only transform.
     */
    public static RigidTransform translation(
            Point3D translation
    ) {
        return new RigidTransform(
                1.0, 0.0, 0.0,
                0.0, 1.0, 0.0,
                0.0, 0.0, 1.0,
                translation,
                false
        );
    }

    /**
     * Creates a translation-only transform.
     */
    public static RigidTransform translation(
            double x,
            double y,
            double z
    ) {
        return translation(
                new Point3D(x, y, z)
        );
    }

    /**
     * Applies this transform to one point.
     */
    public Point3D apply(Point3D point) {
        Point3D required = requireFinitePoint(
                Objects.requireNonNull(point, "point"),
                "point"
        );

        double x = required.x();
        double y = required.y();
        double z = required.z();

        return new Point3D(
                r00 * x
                        + r01 * y
                        + r02 * z
                        + translation.x(),

                r10 * x
                        + r11 * y
                        + r12 * z
                        + translation.y(),

                r20 * x
                        + r21 * y
                        + r22 * z
                        + translation.z()
        );
    }

    /**
     * Applies this transform to every point while preserving input order.
     */
    public List<Point3D> apply(List<Point3D> points) {
        Objects.requireNonNull(points, "points");

        return points.stream()
                .map(point -> apply(
                        Objects.requireNonNull(
                                point,
                                "points must not contain null"
                        )
                ))
                .toList();
    }

    /**
     * Returns a transform that applies this transform first and
     * {@code next} second.
     *
     * <pre>
     * result.apply(point) == next.apply(this.apply(point))
     * </pre>
     */
    public RigidTransform andThen(RigidTransform next) {
        Objects.requireNonNull(next, "next");

        /*
         * Combined rotation:
         *
         * Rcombined = Rnext * Rthis
         */
        double c00 =
                next.r00 * r00
                        + next.r01 * r10
                        + next.r02 * r20;

        double c01 =
                next.r00 * r01
                        + next.r01 * r11
                        + next.r02 * r21;

        double c02 =
                next.r00 * r02
                        + next.r01 * r12
                        + next.r02 * r22;

        double c10 =
                next.r10 * r00
                        + next.r11 * r10
                        + next.r12 * r20;

        double c11 =
                next.r10 * r01
                        + next.r11 * r11
                        + next.r12 * r21;

        double c12 =
                next.r10 * r02
                        + next.r11 * r12
                        + next.r12 * r22;

        double c20 =
                next.r20 * r00
                        + next.r21 * r10
                        + next.r22 * r20;

        double c21 =
                next.r20 * r01
                        + next.r21 * r11
                        + next.r22 * r21;

        double c22 =
                next.r20 * r02
                        + next.r21 * r12
                        + next.r22 * r22;

        /*
         * Combined translation:
         *
         * tcombined = Rnext * tthis + tnext
         */
        Point3D combinedTranslation =
                next.apply(translation);

        return new RigidTransform(
                c00, c01, c02,
                c10, c11, c12,
                c20, c21, c22,
                combinedTranslation,
                false
        );
    }

    /**
     * Returns a transform that applies {@code previous} first and this
     * transform second.
     *
     * <pre>
     * result.apply(point) == this.apply(previous.apply(point))
     * </pre>
     */
    public RigidTransform compose(RigidTransform previous) {
        Objects.requireNonNull(previous, "previous");
        return previous.andThen(this);
    }

    /**
     * Returns the inverse of this transform.
     *
     * <p>For a rigid transform, the inverse rotation is the transpose of the
     * original rotation, and the inverse translation is
     * {@code -(R^T * t)}.</p>
     */
    public RigidTransform inverse() {
        double inverseR00 = r00;
        double inverseR01 = r10;
        double inverseR02 = r20;

        double inverseR10 = r01;
        double inverseR11 = r11;
        double inverseR12 = r21;

        double inverseR20 = r02;
        double inverseR21 = r12;
        double inverseR22 = r22;

        double tx = translation.x();
        double ty = translation.y();
        double tz = translation.z();

        Point3D inverseTranslation = new Point3D(
                -(inverseR00 * tx
                        + inverseR01 * ty
                        + inverseR02 * tz),

                -(inverseR10 * tx
                        + inverseR11 * ty
                        + inverseR12 * tz),

                -(inverseR20 * tx
                        + inverseR21 * ty
                        + inverseR22 * tz)
        );

        return new RigidTransform(
                inverseR00,
                inverseR01,
                inverseR02,

                inverseR10,
                inverseR11,
                inverseR12,

                inverseR20,
                inverseR21,
                inverseR22,

                inverseTranslation,
                false
        );
    }

    /**
     * Returns a defensive copy of the row-major rotation matrix.
     */
    public double[][] rotation() {
        return new double[][]{
                {r00, r01, r02},
                {r10, r11, r12},
                {r20, r21, r22}
        };
    }

    /**
     * Returns the immutable translation vector.
     */
    public Point3D translation() {
        return translation;
    }

    /**
     * Returns the determinant of the rotation matrix.
     */
    public double determinant() {
        return r00 * (r11 * r22 - r12 * r21)
                - r01 * (r10 * r22 - r12 * r20)
                + r02 * (r10 * r21 - r11 * r20);
    }

    /**
     * Returns whether this transform is approximately the identity transform.
     */
    public boolean isIdentity(double tolerance) {
        requireTolerance(tolerance);

        return approximatelyEqual(r00, 1.0, tolerance)
                && approximatelyEqual(r01, 0.0, tolerance)
                && approximatelyEqual(r02, 0.0, tolerance)

                && approximatelyEqual(r10, 0.0, tolerance)
                && approximatelyEqual(r11, 1.0, tolerance)
                && approximatelyEqual(r12, 0.0, tolerance)

                && approximatelyEqual(r20, 0.0, tolerance)
                && approximatelyEqual(r21, 0.0, tolerance)
                && approximatelyEqual(r22, 1.0, tolerance)

                && approximatelyEqual(
                translation.x(),
                0.0,
                tolerance
        )
                && approximatelyEqual(
                translation.y(),
                0.0,
                tolerance
        )
                && approximatelyEqual(
                translation.z(),
                0.0,
                tolerance
        );
    }

    /**
     * Numerically compares this transform with another transform.
     *
     * <p>This method is suitable for geometry tests and numerical
     * calculations. {@link #equals(Object)} intentionally uses exact value
     * semantics.</p>
     */
    public boolean approximatelyEquals(
            RigidTransform other,
            double tolerance
    ) {
        Objects.requireNonNull(other, "other");
        requireTolerance(tolerance);

        return approximatelyEqual(r00, other.r00, tolerance)
                && approximatelyEqual(r01, other.r01, tolerance)
                && approximatelyEqual(r02, other.r02, tolerance)

                && approximatelyEqual(r10, other.r10, tolerance)
                && approximatelyEqual(r11, other.r11, tolerance)
                && approximatelyEqual(r12, other.r12, tolerance)

                && approximatelyEqual(r20, other.r20, tolerance)
                && approximatelyEqual(r21, other.r21, tolerance)
                && approximatelyEqual(r22, other.r22, tolerance)

                && approximatelyEqual(
                translation.x(),
                other.translation.x(),
                tolerance
        )
                && approximatelyEqual(
                translation.y(),
                other.translation.y(),
                tolerance
        )
                && approximatelyEqual(
                translation.z(),
                other.translation.z(),
                tolerance
        );
    }

    /**
     * Exact value equality.
     *
     * <p>Use {@link #approximatelyEquals(RigidTransform, double)} for
     * numerically equivalent transforms produced through floating-point
     * calculations.</p>
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof RigidTransform other)) {
            return false;
        }

        return Double.compare(r00, other.r00) == 0
                && Double.compare(r01, other.r01) == 0
                && Double.compare(r02, other.r02) == 0

                && Double.compare(r10, other.r10) == 0
                && Double.compare(r11, other.r11) == 0
                && Double.compare(r12, other.r12) == 0

                && Double.compare(r20, other.r20) == 0
                && Double.compare(r21, other.r21) == 0
                && Double.compare(r22, other.r22) == 0

                && Double.compare(
                translation.x(),
                other.translation.x()
        ) == 0
                && Double.compare(
                translation.y(),
                other.translation.y()
        ) == 0
                && Double.compare(
                translation.z(),
                other.translation.z()
        ) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                r00,
                r01,
                r02,
                r10,
                r11,
                r12,
                r20,
                r21,
                r22,
                translation.x(),
                translation.y(),
                translation.z()
        );
    }

    @Override
    public String toString() {
        return "RigidTransform{"
                + "rotation=[["
                + r00 + ", " + r01 + ", " + r02
                + "], ["
                + r10 + ", " + r11 + ", " + r12
                + "], ["
                + r20 + ", " + r21 + ", " + r22
                + "]], translation="
                + translation
                + '}';
    }

    private void validateRotation() {
        requireUnitVector(
                r00,
                r01,
                r02,
                "rotation row 0"
        );

        requireUnitVector(
                r10,
                r11,
                r12,
                "rotation row 1"
        );

        requireUnitVector(
                r20,
                r21,
                r22,
                "rotation row 2"
        );

        requireOrthogonal(
                r00,
                r01,
                r02,
                r10,
                r11,
                r12,
                "rotation rows 0 and 1"
        );

        requireOrthogonal(
                r00,
                r01,
                r02,
                r20,
                r21,
                r22,
                "rotation rows 0 and 2"
        );

        requireOrthogonal(
                r10,
                r11,
                r12,
                r20,
                r21,
                r22,
                "rotation rows 1 and 2"
        );

        double determinant = determinant();

        if (Math.abs(determinant - 1.0)
                > DETERMINANT_TOLERANCE) {
            throw new IllegalArgumentException(
                    "Rotation matrix determinant must be approximately "
                            + "+1, but was "
                            + determinant
            );
        }
    }

    private static void requireUnitVector(
            double x,
            double y,
            double z,
            String name
    ) {
        double squaredLength =
                x * x + y * y + z * z;

        if (Math.abs(squaredLength - 1.0)
                > ORTHONORMAL_TOLERANCE) {
            throw new IllegalArgumentException(
                    name
                            + " must have unit length, but squared "
                            + "length was "
                            + squaredLength
            );
        }
    }

    private static void requireOrthogonal(
            double firstX,
            double firstY,
            double firstZ,
            double secondX,
            double secondY,
            double secondZ,
            String name
    ) {
        double dot =
                firstX * secondX
                        + firstY * secondY
                        + firstZ * secondZ;

        if (Math.abs(dot) > ORTHONORMAL_TOLERANCE) {
            throw new IllegalArgumentException(
                    name
                            + " must be orthogonal, but dot product was "
                            + dot
            );
        }
    }

    private static double matrixValue(
            double[][] matrix,
            int row,
            int column
    ) {
        validateMatrixShape(matrix);
        return matrix[row][column];
    }

    private static void validateMatrixShape(double[][] matrix) {
        Objects.requireNonNull(matrix, "rotation");

        if (matrix.length != 3) {
            throw new IllegalArgumentException(
                    "rotation must contain exactly 3 rows"
            );
        }

        for (int index = 0; index < matrix.length; index++) {
            if (matrix[index] == null
                    || matrix[index].length != 3) {
                throw new IllegalArgumentException(
                        "rotation row "
                                + index
                                + " must contain exactly 3 values"
                );
            }
        }
    }

    private static Point3D requireFinitePoint(
            Point3D point,
            String name
    ) {
        if (!Double.isFinite(point.x())
                || !Double.isFinite(point.y())
                || !Double.isFinite(point.z())) {
            throw new IllegalArgumentException(
                    name
                            + " must contain finite coordinates: "
                            + point
            );
        }

        return point;
    }

    private static void requireFinite(
            double value,
            String name
    ) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    name + " must be finite"
            );
        }
    }

    private static void requireTolerance(double tolerance) {
        if (!Double.isFinite(tolerance)
                || tolerance < 0.0) {
            throw new IllegalArgumentException(
                    "tolerance must be finite and non-negative"
            );
        }
    }

    private static boolean approximatelyEqual(
            double first,
            double second,
            double tolerance
    ) {
        return Math.abs(first - second) <= tolerance;
    }
}