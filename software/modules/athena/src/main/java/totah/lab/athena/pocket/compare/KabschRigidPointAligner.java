package totah.lab.athena.pocket.compare;


import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;

import java.util.List;
import java.util.Objects;

/**
 * Computes the least-squares rigid-body transform that maps a source point set
 * onto a corresponding target point set using the Kabsch algorithm.
 *
 * <p>The input lists must have the same size. Point at index {@code i} in the
 * source list corresponds to point at index {@code i} in the target list.</p>
 *
 * <p>The resulting rotation is always proper: reflections are rejected by
 * enforcing a positive determinant.</p>
 */
public final class KabschRigidPointAligner
        implements RigidPointAligner {

    private static final int DIMENSIONS = 3;
    private static final int MINIMUM_POINT_COUNT = 3;

    @Override
    public RigidTransform align(
            List<Point3D> source,
            List<Point3D> target
    ) {
        validateCorrespondences(source, target);

        Point3D sourceCentroid = centroid(source);
        Point3D targetCentroid = centroid(target);

        RealMatrix covariance = covariance(
                source,
                target,
                sourceCentroid,
                targetCentroid
        );

        SingularValueDecomposition svd =
                new SingularValueDecomposition(covariance);

        RealMatrix u = svd.getU();
        RealMatrix v = svd.getV();

        /*
         * For H = X^T Y, the row-vector Kabsch rotation is V U^T.
         *
         * RigidTransform.apply() uses column vectors:
         *
         *     transformed = R * point + translation
         *
         * so the corresponding column-vector rotation is also V U^T
         * with the covariance construction used below.
         */
        RealMatrix rotation = v.multiply(u.transpose());

        /*
         * If det(R) < 0, the SVD solution contains a reflection.
         * Flip the final column of V and recompute the rotation.
         */
        if (determinant3x3(rotation) < 0.0) {
            RealMatrix correctedV = v.copy();

            for (int row = 0; row < DIMENSIONS; row++) {
                correctedV.setEntry(
                        row,
                        DIMENSIONS - 1,
                        -correctedV.getEntry(
                                row,
                                DIMENSIONS - 1
                        )
                );
            }

            rotation = correctedV.multiply(u.transpose());
        }

        requireProperRotation(rotation);

        Point3D rotatedSourceCentroid =
                applyRotation(rotation, sourceCentroid);

        Point3D translation = new Point3D(
                targetCentroid.x() - rotatedSourceCentroid.x(),
                targetCentroid.y() - rotatedSourceCentroid.y(),
                targetCentroid.z() - rotatedSourceCentroid.z()
        );

        return new RigidTransform(
                rotation.getData(),
                translation
        );
    }

    /**
     * Builds the 3x3 cross-covariance matrix:
     *
     *     H = sum((source_i - sourceCentroid)
     *             (target_i - targetCentroid)^T)
     */
    private RealMatrix covariance(
            List<Point3D> source,
            List<Point3D> target,
            Point3D sourceCentroid,
            Point3D targetCentroid
    ) {
        double[][] values = new double[DIMENSIONS][DIMENSIONS];

        for (int index = 0; index < source.size(); index++) {
            Point3D sourcePoint = source.get(index);
            Point3D targetPoint = target.get(index);

            double sx = sourcePoint.x() - sourceCentroid.x();
            double sy = sourcePoint.y() - sourceCentroid.y();
            double sz = sourcePoint.z() - sourceCentroid.z();

            double tx = targetPoint.x() - targetCentroid.x();
            double ty = targetPoint.y() - targetCentroid.y();
            double tz = targetPoint.z() - targetCentroid.z();

            values[0][0] += sx * tx;
            values[0][1] += sx * ty;
            values[0][2] += sx * tz;

            values[1][0] += sy * tx;
            values[1][1] += sy * ty;
            values[1][2] += sy * tz;

            values[2][0] += sz * tx;
            values[2][1] += sz * ty;
            values[2][2] += sz * tz;
        }

        return new Array2DRowRealMatrix(values, false);
    }

    private Point3D centroid(List<Point3D> points) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;

        for (Point3D point : points) {
            x += point.x();
            y += point.y();
            z += point.z();
        }

        double count = points.size();

        return new Point3D(
                x / count,
                y / count,
                z / count
        );
    }

    private Point3D applyRotation(
            RealMatrix rotation,
            Point3D point
    ) {
        double x =
                rotation.getEntry(0, 0) * point.x()
                        + rotation.getEntry(0, 1) * point.y()
                        + rotation.getEntry(0, 2) * point.z();

        double y =
                rotation.getEntry(1, 0) * point.x()
                        + rotation.getEntry(1, 1) * point.y()
                        + rotation.getEntry(1, 2) * point.z();

        double z =
                rotation.getEntry(2, 0) * point.x()
                        + rotation.getEntry(2, 1) * point.y()
                        + rotation.getEntry(2, 2) * point.z();

        return new Point3D(x, y, z);
    }

    private void validateCorrespondences(
            List<Point3D> source,
            List<Point3D> target
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");

        if (source.size() != target.size()) {
            throw new IllegalArgumentException(
                    "Source and target must contain the same number "
                            + "of corresponding points: source="
                            + source.size()
                            + ", target="
                            + target.size()
            );
        }

        if (source.size() < MINIMUM_POINT_COUNT) {
            throw new IllegalArgumentException(
                    "Kabsch alignment requires at least "
                            + MINIMUM_POINT_COUNT
                            + " point correspondences"
            );
        }

        for (int index = 0; index < source.size(); index++) {
            requireFinitePoint(
                    source.get(index),
                    "source[" + index + "]"
            );
            requireFinitePoint(
                    target.get(index),
                    "target[" + index + "]"
            );
        }

        requireNonDegenerate(source, "source");
        requireNonDegenerate(target, "target");
    }

    /**
     * Rejects a point collection in which every point is effectively
     * coincident. Collinear points are allowed, although their rotation around
     * the line may be underdetermined.
     */
    private void requireNonDegenerate(
            List<Point3D> points,
            String name
    ) {
        Point3D first = points.getFirst();

        boolean distinct = points.stream()
                .skip(1)
                .anyMatch(point ->
                        first.distanceSquared(point) > 1.0e-16
                );

        if (!distinct) {
            throw new IllegalArgumentException(
                    name + " points are geometrically degenerate"
            );
        }
    }

    private void requireFinitePoint(
            Point3D point,
            String name
    ) {
        Objects.requireNonNull(point, name);

        if (!Double.isFinite(point.x())
                || !Double.isFinite(point.y())
                || !Double.isFinite(point.z())) {
            throw new IllegalArgumentException(
                    name + " must contain finite coordinates: " + point
            );
        }
    }

    private void requireProperRotation(RealMatrix rotation) {
        double determinant = determinant3x3(rotation);

        if (!Double.isFinite(determinant)
                || Math.abs(determinant - 1.0) > 1.0e-8) {
            throw new IllegalStateException(
                    "Kabsch produced an invalid rotation; determinant="
                            + determinant
            );
        }

        RealMatrix orthogonality =
                rotation.transpose().multiply(rotation);

        RealMatrix identity =
                MatrixUtils.createRealIdentityMatrix(DIMENSIONS);

        double maximumError = 0.0;

        for (int row = 0; row < DIMENSIONS; row++) {
            for (int column = 0; column < DIMENSIONS; column++) {
                maximumError = Math.max(
                        maximumError,
                        Math.abs(
                                orthogonality.getEntry(row, column)
                                        - identity.getEntry(row, column)
                        )
                );
            }
        }

        if (maximumError > 1.0e-8) {
            throw new IllegalStateException(
                    "Kabsch produced a non-orthogonal rotation; "
                            + "maximum error="
                            + maximumError
            );
        }
    }

    private double determinant3x3(RealMatrix matrix) {
        if (matrix.getRowDimension() != DIMENSIONS
                || matrix.getColumnDimension() != DIMENSIONS) {
            throw new IllegalArgumentException(
                    "Expected a 3x3 matrix"
            );
        }

        double a = matrix.getEntry(0, 0);
        double b = matrix.getEntry(0, 1);
        double c = matrix.getEntry(0, 2);

        double d = matrix.getEntry(1, 0);
        double e = matrix.getEntry(1, 1);
        double f = matrix.getEntry(1, 2);

        double g = matrix.getEntry(2, 0);
        double h = matrix.getEntry(2, 1);
        double i = matrix.getEntry(2, 2);

        return a * (e * i - f * h)
                - b * (d * i - f * g)
                + c * (d * h - e * g);
    }
}
