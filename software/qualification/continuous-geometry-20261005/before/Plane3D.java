package totah.lab.gaia.geometry;

import java.util.List;
import java.util.Objects;

/**
 * A plane fitted to a set of points by least squares, primarily used to
 * describe the ring planes of aromatic systems for pi-interaction
 * detection.
 *
 * <p>The plane is defined by the centroid (arithmetic mean) of the
 * fitted points and a unit normal. The normal is the eigenvector of the
 * 3&times;3 covariance matrix of the points with the smallest
 * eigenvalue, computed by cyclic Jacobi iteration (deterministic, no
 * external dependencies).
 *
 * <p>Because a plane has no intrinsic orientation, the normal carries a
 * canonical sign: it is flipped, if necessary, so that its first
 * component whose absolute value exceeds {@value #SIGN_EPSILON} is
 * positive. This makes the normal deterministic for a given point set
 * regardless of which of the two mathematically valid orientations the
 * eigensolver happens to return.
 *
 * <p>Instances are immutable.
 */
public final class Plane3D {

    /**
     * Components of the normal with an absolute value at or below this
     * threshold are treated as zero for the canonical sign rule.
     */
    private static final double SIGN_EPSILON = 1.0e-12;

    /**
     * A point set is rejected as coincident when the largest covariance
     * eigenvalue falls below this absolute threshold, i.e. the points
     * span less than roughly 1e-6 length units in every direction.
     */
    private static final double COINCIDENT_EPSILON = 1.0e-12;

    /**
     * A point set is rejected as collinear when the ratio of the
     * second-largest to the largest covariance eigenvalue falls below
     * this threshold, i.e. the points span no meaningful width
     * perpendicular to their dominant axis.
     */
    private static final double COLLINEAR_EIGENVALUE_RATIO = 1.0e-10;

    private static final int MIN_POINTS = 3;
    private static final int JACOBI_MAX_SWEEPS = 50;
    private static final double JACOBI_OFF_TOLERANCE = 1.0e-30;

    private final Point3D centroid;
    private final Vector3D normal;

    private Plane3D(
            Point3D centroid,
            Vector3D normal) {

        this.centroid = centroid;
        this.normal = normal;
    }

    /**
     * Fits a least-squares plane to {@code points}. The fitted plane
     * minimizes the sum of squared orthogonal distances of the points
     * to the plane.
     *
     * @param points at least three non-collinear points
     * @return the fitted plane
     * @throws NullPointerException     if {@code points} or any element
     *                                  is null
     * @throws IllegalArgumentException if fewer than three points are
     *                                  given, or the points are
     *                                  coincident or (near-)collinear
     *                                  so that no unique plane exists
     */
    public static Plane3D fit(List<Point3D> points) {
        Objects.requireNonNull(points, "points");

        if (points.size() < MIN_POINTS) {
            throw new IllegalArgumentException(
                    "At least " + MIN_POINTS + " points are required to"
                            + " fit a plane, got " + points.size() + ".");
        }

        Point3D centroid = centroidOf(points);

        /*
         * Covariance matrix of the points relative to their centroid.
         * The smallest eigenvector of this symmetric matrix is the
         * plane normal.
         */
        double[][] covariance = new double[3][3];
        for (Point3D point : points) {
            Objects.requireNonNull(point, "points must not contain null");

            double dx = point.x() - centroid.x();
            double dy = point.y() - centroid.y();
            double dz = point.z() - centroid.z();

            covariance[0][0] += dx * dx;
            covariance[0][1] += dx * dy;
            covariance[0][2] += dx * dz;
            covariance[1][1] += dy * dy;
            covariance[1][2] += dy * dz;
            covariance[2][2] += dz * dz;
        }
        covariance[1][0] = covariance[0][1];
        covariance[2][0] = covariance[0][2];
        covariance[2][1] = covariance[1][2];

        double[][] eigenvectors = new double[3][3];
        jacobiEigen(covariance, eigenvectors);

        double largest = Math.max(
                covariance[0][0],
                Math.max(covariance[1][1], covariance[2][2]));
        if (largest < COINCIDENT_EPSILON) {
            throw new IllegalArgumentException(
                    "Cannot fit a plane to coincident points: the point"
                            + " set spans no meaningful extent.");
        }

        int smallestIndex = indexOfSmallestDiagonal(covariance);
        double middle = covariance[0][0]
                + covariance[1][1]
                + covariance[2][2]
                - largest
                - covariance[smallestIndex][smallestIndex];
        if (middle < COLLINEAR_EIGENVALUE_RATIO * largest) {
            throw new IllegalArgumentException(
                    "Cannot fit a plane to (near-)collinear points: the"
                            + " point set spans no meaningful width"
                            + " perpendicular to its dominant axis.");
        }

        Vector3D normal = canonicalSign(new Vector3D(
                eigenvectors[0][smallestIndex],
                eigenvectors[1][smallestIndex],
                eigenvectors[2][smallestIndex]).normalize());

        return new Plane3D(centroid, normal);
    }

    /**
     * Returns the centroid (arithmetic mean) of the fitted points,
     * which lies on the plane.
     */
    public Point3D centroid() {
        return centroid;
    }

    /**
     * Returns the unit normal of the plane with canonical sign: the
     * first component whose absolute value exceeds
     * {@value #SIGN_EPSILON} is positive.
     */
    public Vector3D normal() {
        return normal;
    }

    /**
     * Returns the signed perpendicular distance from {@code point} to
     * this plane. The distance is positive when the point lies on the
     * side toward which {@link #normal()} points, negative on the
     * opposite side, and zero for points on the plane.
     */
    public double distanceTo(Point3D point) {
        Objects.requireNonNull(point, "point");

        return centroid.vectorTo(point).dot(normal);
    }

    /**
     * Returns the absolute (unsigned) perpendicular distance from
     * {@code point} to this plane.
     */
    public double absoluteDistanceTo(Point3D point) {
        return Math.abs(distanceTo(point));
    }

    /**
     * Returns the orthogonal projection of {@code point} onto this
     * plane.
     */
    public Point3D project(Point3D point) {
        return point.subtract(normal.scale(distanceTo(point)));
    }

    /**
     * Returns the acute angle between this plane and {@code other} in
     * degrees, in the range [0, 90]. Parallel planes report 0 and
     * perpendicular planes report 90; because planes are unoriented,
     * an obtuse angle between the normals is folded back via
     * min(&theta;, 180&deg; - &theta;).
     */
    public double angleToDegrees(Plane3D other) {
        Objects.requireNonNull(other, "other");

        double cosine = Math.min(1.0, Math.abs(normal.dot(other.normal)));
        return Math.toDegrees(Math.acos(cosine));
    }

    @Override
    public String toString() {
        return String.format(
                "Plane3D[centroid=%s, normal=%s]",
                centroid,
                normal);
    }

    private static Point3D centroidOf(List<Point3D> points) {
        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;

        for (Point3D point : points) {
            Objects.requireNonNull(point, "points must not contain null");

            sumX += point.x();
            sumY += point.y();
            sumZ += point.z();
        }

        int count = points.size();
        return new Point3D(
                sumX / count,
                sumY / count,
                sumZ / count);
    }

    private static Vector3D canonicalSign(Vector3D vector) {
        if (Math.abs(vector.x()) > SIGN_EPSILON) {
            return vector.x() > 0.0 ? vector : vector.negate();
        }
        if (Math.abs(vector.y()) > SIGN_EPSILON) {
            return vector.y() > 0.0 ? vector : vector.negate();
        }
        return vector.z() > 0.0 ? vector : vector.negate();
    }

    private static int indexOfSmallestDiagonal(double[][] matrix) {
        int index = 0;
        for (int i = 1; i < 3; i++) {
            if (matrix[i][i] < matrix[index][index]) {
                index = i;
            }
        }
        return index;
    }

    /*
     * Cyclic Jacobi iteration for the eigenproblem of a symmetric
     * 3x3 matrix. On return, the diagonal of {@code matrix} holds the
     * eigenvalues and the columns of {@code eigenvectors} hold the
     * corresponding eigenvectors. Deterministic: fixed sweep order, no
     * pivoting choices that depend on floating-point comparisons beyond
     * the (deterministic) rotation angle.
     */
    private static void jacobiEigen(
            double[][] matrix,
            double[][] eigenvectors) {

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                eigenvectors[i][j] = (i == j) ? 1.0 : 0.0;
            }
        }

        for (int sweep = 0; sweep < JACOBI_MAX_SWEEPS; sweep++) {
            double offDiagonal =
                    matrix[0][1] * matrix[0][1]
                            + matrix[0][2] * matrix[0][2]
                            + matrix[1][2] * matrix[1][2];
            if (offDiagonal < JACOBI_OFF_TOLERANCE) {
                return;
            }

            for (int p = 0; p < 2; p++) {
                for (int q = p + 1; q < 3; q++) {
                    jacobiRotate(matrix, eigenvectors, p, q);
                }
            }
        }
    }

    private static void jacobiRotate(
            double[][] matrix,
            double[][] eigenvectors,
            int p,
            int q) {

        if (matrix[p][q] == 0.0) {
            return;
        }

        double theta =
                (matrix[q][q] - matrix[p][p]) / (2.0 * matrix[p][q]);
        double t = (theta >= 0.0 ? 1.0 : -1.0)
                / (Math.abs(theta) + Math.sqrt(theta * theta + 1.0));
        double cosine = 1.0 / Math.sqrt(t * t + 1.0);
        double sine = t * cosine;

        for (int k = 0; k < 3; k++) {
            double mkp = matrix[k][p];
            double mkq = matrix[k][q];
            matrix[k][p] = cosine * mkp - sine * mkq;
            matrix[k][q] = sine * mkp + cosine * mkq;
        }
        for (int k = 0; k < 3; k++) {
            double mpk = matrix[p][k];
            double mqk = matrix[q][k];
            matrix[p][k] = cosine * mpk - sine * mqk;
            matrix[q][k] = sine * mpk + cosine * mqk;
        }
        for (int k = 0; k < 3; k++) {
            double vkp = eigenvectors[k][p];
            double vkq = eigenvectors[k][q];
            eigenvectors[k][p] = cosine * vkp - sine * vkq;
            eigenvectors[k][q] = sine * vkp + cosine * vkq;
        }
    }
}
