package totah.lab.euclid.spatial;

import java.util.List;
import java.util.Objects;

/**
 * Optimal rigid superposition (rotation plus translation) of one point
 * set onto another, minimizing the root-mean-square deviation.
 *
 * <p>Uses the Theobald quaternion characteristic polynomial (QCP)
 * method: the optimal rotation is the eigenvector of the largest
 * eigenvalue of the symmetric 4x4 key matrix built from the
 * coordinate correlation matrix, and that eigenvalue is found by
 * Newton iteration on the characteristic polynomial (coefficients via
 * Faddeev-LeVerrier). Deterministic, no external libraries.
 *
 * <p>Coordinates are plain {@code double[3]} arrays so that euclid
 * stays free of dependencies on other modules. The transform returned
 * by {@link #fit(List, List)} maps the <i>mobile</i> set onto the
 * <i>reference</i> set:
 * {@code reference[i] &asymp; R * mobile[i] + t}.
 *
 * <p>Degenerate inputs are handled gracefully: a single point yields a
 * pure translation, and coincident or otherwise rank-deficient sets
 * fall back to the identity rotation.
 */
public final class RigidSuperposition {

    private static final double NEWTON_TOLERANCE = 1.0e-12;
    private static final int NEWTON_MAX_ITERATIONS = 64;
    private static final double EIGENVALUE_PERTURBATION = 1.0e-10;
    private static final double EIGENVECTOR_EPSILON = 1.0e-24;

    private final double[][] rotation;
    private final double[] translation;
    private final double rmsd;
    private final int pointCount;

    private RigidSuperposition(
            double[][] rotation,
            double[] translation,
            double rmsd,
            int pointCount) {

        this.rotation = copy(rotation);
        this.translation = translation.clone();
        this.rmsd = rmsd;
        this.pointCount = pointCount;
    }

    /**
     * Computes the optimal rigid transform mapping {@code mobile} onto
     * {@code reference}.
     *
     * @param reference fixed coordinate set, one {@code double[3]} per
     *                  point
     * @param mobile coordinate set to be superimposed, same length as
     *               {@code reference}
     * @return the optimal rotation, translation and resulting RMSD
     * @throws IllegalArgumentException if the sets are empty, have
     *         different sizes, or contain malformed coordinates
     */
    public static RigidSuperposition fit(
            List<double[]> reference,
            List<double[]> mobile) {

        validate(reference, "reference");
        validate(mobile, "mobile");
        if (reference.size() != mobile.size()) {
            throw new IllegalArgumentException(
                    "reference and mobile must have the same size: "
                            + reference.size() + " vs " + mobile.size());
        }

        int n = reference.size();
        double[] centroidReference = centroid(reference);
        double[] centroidMobile = centroid(mobile);

        if (n == 1) {
            return new RigidSuperposition(
                    identity(),
                    subtract(centroidReference, centroidMobile),
                    0.0,
                    n);
        }

        /*
         * Correlation matrix of the centered sets, and the squared
         * norms needed for the RMSD identity.
         */
        double[][] correlation = new double[3][3];
        double squaredNormReference = 0.0;
        double squaredNormMobile = 0.0;
        for (int i = 0; i < n; i++) {
            double rx = reference.get(i)[0] - centroidReference[0];
            double ry = reference.get(i)[1] - centroidReference[1];
            double rz = reference.get(i)[2] - centroidReference[2];
            double mx = mobile.get(i)[0] - centroidMobile[0];
            double my = mobile.get(i)[1] - centroidMobile[1];
            double mz = mobile.get(i)[2] - centroidMobile[2];

            squaredNormReference += rx * rx + ry * ry + rz * rz;
            squaredNormMobile += mx * mx + my * my + mz * mz;

            correlation[0][0] += mx * rx;
            correlation[0][1] += mx * ry;
            correlation[0][2] += mx * rz;
            correlation[1][0] += my * rx;
            correlation[1][1] += my * ry;
            correlation[1][2] += my * rz;
            correlation[2][0] += mz * rx;
            correlation[2][1] += mz * ry;
            correlation[2][2] += mz * rz;
        }

        double[][] key = keyMatrix(correlation);
        double largestEigenvalue =
                largestEigenvalue(
                        key,
                        (squaredNormReference + squaredNormMobile) / 2.0);

        double[] quaternion = rotationQuaternion(key, largestEigenvalue);
        double[][] rotation = rotationMatrix(quaternion);

        double[] rotatedCentroid = multiply(rotation, centroidMobile);
        double[] translation = subtract(centroidReference, rotatedCentroid);

        double squaredDeviation =
                squaredNormReference + squaredNormMobile
                        - 2.0 * largestEigenvalue;
        double rmsd = Math.sqrt(Math.max(0.0, squaredDeviation) / n);

        return new RigidSuperposition(rotation, translation, rmsd, n);
    }

    /**
     * RMSD between the two sets after optimal rigid superposition.
     */
    public static double rmsd(
            List<double[]> reference,
            List<double[]> mobile) {

        return fit(reference, mobile).rmsd();
    }

    /**
     * Optimal 3x3 rotation matrix (defensive copy) mapping mobile
     * coordinates onto the reference frame.
     */
    public double[][] rotation() {
        return copy(rotation);
    }

    /**
     * Optimal translation vector (defensive copy), applied after the
     * rotation.
     */
    public double[] translation() {
        return translation.clone();
    }

    /**
     * RMSD between the sets after applying this transform.
     */
    public double rmsd() {
        return rmsd;
    }

    /**
     * Number of points used in the fit.
     */
    public int pointCount() {
        return pointCount;
    }

    /**
     * Applies this transform ({@code R * point + t}) to a mobile
     * coordinate.
     */
    public double[] apply(double[] point) {
        validatePoint(point, "point");
        double[] rotated = multiply(rotation, point);
        return new double[] {
                rotated[0] + translation[0],
                rotated[1] + translation[1],
                rotated[2] + translation[2]
        };
    }

    /*
     * Symmetric 4x4 key matrix of the quaternion (Horn/Theobald)
     * formulation, built from the 3x3 correlation matrix.
     */
    private static double[][] keyMatrix(double[][] a) {
        double sxx = a[0][0];
        double sxy = a[0][1];
        double sxz = a[0][2];
        double syx = a[1][0];
        double syy = a[1][1];
        double syz = a[1][2];
        double szx = a[2][0];
        double szy = a[2][1];
        double szz = a[2][2];

        double[][] k = new double[4][4];
        k[0][0] = sxx + syy + szz;
        k[0][1] = syz - szy;
        k[0][2] = szx - sxz;
        k[0][3] = sxy - syx;
        k[1][0] = k[0][1];
        k[1][1] = sxx - syy - szz;
        k[1][2] = sxy + syx;
        k[1][3] = szx + sxz;
        k[2][0] = k[0][2];
        k[2][1] = k[1][2];
        k[2][2] = -sxx + syy - szz;
        k[2][3] = syz + szy;
        k[3][0] = k[0][3];
        k[3][1] = k[1][3];
        k[3][2] = k[2][3];
        k[3][3] = -sxx - syy + szz;
        return k;
    }

    /*
     * Largest eigenvalue of the symmetric key matrix via Newton
     * iteration on its characteristic polynomial, starting from the
     * analytic upper bound (G_reference + G_mobile) / 2, which
     * converges monotonically onto the largest root.
     */
    private static double largestEigenvalue(
            double[][] key,
            double upperBound) {

        double[] coefficients = characteristicCoefficients(key);

        double x = upperBound;
        for (int iteration = 0; iteration < NEWTON_MAX_ITERATIONS; iteration++) {
            double value = evaluate(coefficients, x);

            /*
             * Converged (including degenerate starts where the upper
             * bound is already the root, e.g. two-point fits).
             */
            double scale = Math.max(1.0, x * x * x * x);
            if (Math.abs(value) < NEWTON_TOLERANCE * scale) {
                break;
            }

            double derivative = evaluateDerivative(coefficients, x);
            if (!Double.isFinite(derivative) || derivative == 0.0) {
                break;
            }

            double step = value / derivative;
            x -= step;

            if (Math.abs(step) < NEWTON_TOLERANCE * (1.0 + Math.abs(x))) {
                break;
            }
        }
        return x;
    }

    /*
     * Coefficients {c3, c2, c1, c0} of the monic characteristic
     * polynomial x^4 + c3 x^3 + c2 x^2 + c1 x + c0 of a 4x4 matrix,
     * via the Faddeev-LeVerrier algorithm.
     */
    private static double[] characteristicCoefficients(double[][] m) {
        double c3 = -trace(m);

        double[][] b2 = multiply4(m, addDiagonal(m, c3));
        double c2 = -trace(b2) / 2.0;

        double[][] b3 = multiply4(m, addDiagonal(b2, c2));
        double c1 = -trace(b3) / 3.0;

        double[][] b4 = multiply4(m, addDiagonal(b3, c1));
        double c0 = -trace(b4) / 4.0;

        return new double[] {c3, c2, c1, c0};
    }

    private static double evaluate(double[] coefficients, double x) {
        return (((x + coefficients[0]) * x + coefficients[1]) * x
                + coefficients[2]) * x + coefficients[3];
    }

    private static double evaluateDerivative(double[] coefficients, double x) {
        return ((4.0 * x + 3.0 * coefficients[0]) * x
                + 2.0 * coefficients[1]) * x + coefficients[2];
    }

    /*
     * Unit quaternion (w, x, y, z) of the optimal rotation: the
     * eigenvector of the key matrix for the largest eigenvalue. For a
     * simple eigenvalue the adjugate of (K - lambda*I) is a non-zero
     * multiple of q q^T, so any of its rows yields q. If the adjugate
     * vanishes (degenerate eigenvalue, e.g. two-point fits), the
     * eigenvalue is perturbed slightly inward and any vector of the
     * degenerate eigenspace is accepted; rank-deficient matrices
     * (e.g. all points coincident) fall back to identity.
     */
    private static double[] rotationQuaternion(
            double[][] key,
            double largestEigenvalue) {

        double[] quaternion =
                adjugateEigenvector(key, largestEigenvalue);
        if (quaternion != null) {
            return quaternion;
        }

        double perturbed =
                largestEigenvalue
                        - EIGENVALUE_PERTURBATION
                                * Math.max(1.0, Math.abs(largestEigenvalue));
        quaternion = adjugateEigenvector(key, perturbed);
        if (quaternion != null) {
            return quaternion;
        }

        return new double[] {1.0, 0.0, 0.0, 0.0};
    }

    private static double[] adjugateEigenvector(
            double[][] key,
            double eigenvalue) {

        double[][] shifted = addDiagonal(key, -eigenvalue);
        double[][] adjugate = adjugate4(shifted);

        int bestRow = 0;
        double bestNormSquared = -1.0;
        for (int row = 0; row < 4; row++) {
            double normSquared = 0.0;
            for (int column = 0; column < 4; column++) {
                normSquared += adjugate[row][column] * adjugate[row][column];
            }
            if (normSquared > bestNormSquared) {
                bestNormSquared = normSquared;
                bestRow = row;
            }
        }

        if (bestNormSquared < EIGENVECTOR_EPSILON) {
            return null;
        }

        double norm = Math.sqrt(bestNormSquared);
        double[] quaternion = new double[4];
        for (int i = 0; i < 4; i++) {
            quaternion[i] = adjugate[bestRow][i] / norm;
        }
        return quaternion;
    }

    private static double[][] rotationMatrix(double[] q) {
        double w = q[0];
        double x = q[1];
        double y = q[2];
        double z = q[3];

        return new double[][] {
                {1.0 - 2.0 * (y * y + z * z), 2.0 * (x * y - w * z), 2.0 * (x * z + w * y)},
                {2.0 * (x * y + w * z), 1.0 - 2.0 * (x * x + z * z), 2.0 * (y * z - w * x)},
                {2.0 * (x * z - w * y), 2.0 * (y * z + w * x), 1.0 - 2.0 * (x * x + y * y)}
        };
    }

    private static double[][] adjugate4(double[][] m) {
        double[][] adjugate = new double[4][4];
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                double sign = ((row + column) % 2 == 0) ? 1.0 : -1.0;
                adjugate[column][row] = sign * minor3(m, row, column);
            }
        }
        return adjugate;
    }

    /*
     * Determinant of the 3x3 minor obtained by deleting the given row
     * and column from a 4x4 matrix.
     */
    private static double minor3(double[][] m, int skipRow, int skipColumn) {
        double[] values = new double[9];
        int index = 0;
        for (int row = 0; row < 4; row++) {
            if (row == skipRow) {
                continue;
            }
            for (int column = 0; column < 4; column++) {
                if (column != skipColumn) {
                    values[index++] = m[row][column];
                }
            }
        }
        return values[0] * (values[4] * values[8] - values[5] * values[7])
                - values[1] * (values[3] * values[8] - values[5] * values[6])
                + values[2] * (values[3] * values[7] - values[4] * values[6]);
    }

    private static double[][] multiply4(double[][] a, double[][] b) {
        double[][] result = new double[4][4];
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                double sum = 0.0;
                for (int k = 0; k < 4; k++) {
                    sum += a[row][k] * b[k][column];
                }
                result[row][column] = sum;
            }
        }
        return result;
    }

    private static double[][] addDiagonal(double[][] m, double value) {
        double[][] result = new double[4][4];
        for (int row = 0; row < 4; row++) {
            System.arraycopy(m[row], 0, result[row], 0, 4);
            result[row][row] += value;
        }
        return result;
    }

    private static double trace(double[][] m) {
        double sum = 0.0;
        for (int i = 0; i < m.length; i++) {
            sum += m[i][i];
        }
        return sum;
    }

    private static double[] centroid(List<double[]> points) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (double[] point : points) {
            x += point[0];
            y += point[1];
            z += point[2];
        }
        int n = points.size();
        return new double[] {x / n, y / n, z / n};
    }

    private static double[] multiply(double[][] matrix, double[] vector) {
        return new double[] {
                matrix[0][0] * vector[0] + matrix[0][1] * vector[1] + matrix[0][2] * vector[2],
                matrix[1][0] * vector[0] + matrix[1][1] * vector[1] + matrix[1][2] * vector[2],
                matrix[2][0] * vector[0] + matrix[2][1] * vector[1] + matrix[2][2] * vector[2]
        };
    }

    private static double[] subtract(double[] first, double[] second) {
        return new double[] {
                first[0] - second[0],
                first[1] - second[1],
                first[2] - second[2]
        };
    }

    private static double[][] identity() {
        return new double[][] {
                {1.0, 0.0, 0.0},
                {0.0, 1.0, 0.0},
                {0.0, 0.0, 1.0}
        };
    }

    private static double[][] copy(double[][] matrix) {
        double[][] result = new double[matrix.length][];
        for (int row = 0; row < matrix.length; row++) {
            result[row] = matrix[row].clone();
        }
        return result;
    }

    private static void validate(List<double[]> points, String name) {
        Objects.requireNonNull(points, name);
        if (points.isEmpty()) {
            throw new IllegalArgumentException(
                    name + " must contain at least one point");
        }
        for (double[] point : points) {
            validatePoint(point, name + " element");
        }
    }

    private static void validatePoint(double[] point, String name) {
        Objects.requireNonNull(point, name);
        if (point.length != 3) {
            throw new IllegalArgumentException(
                    name + " must have exactly 3 coordinates");
        }
        for (double coordinate : point) {
            if (!Double.isFinite(coordinate)) {
                throw new IllegalArgumentException(
                        name + " coordinates must be finite");
            }
        }
    }
}
