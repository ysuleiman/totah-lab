package totah.lab.athena.pocket.architecture;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.Vector3D;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Principal component analysis of a 3D point set: the centroid and the
 * eigenvectors/eigenvalues of the covariance matrix, sorted by
 * descending eigenvalue. Axis signs are canonicalized (the largest
 * absolute component of each axis is positive) so results are
 * deterministic; eigenvector sign remains a convention, so downstream
 * consumers compare axis DIRECTIONS up to sign (acute angles).
 */
public record PrincipalComponents(
        Point3D centroid,
        List<Vector3D> axes,
        List<Double> eigenvalues
) {

    public PrincipalComponents {
        Objects.requireNonNull(centroid, "centroid");
        axes = List.copyOf(Objects.requireNonNull(axes, "axes"));
        eigenvalues = List.copyOf(
                Objects.requireNonNull(eigenvalues, "eigenvalues")
        );

        if (axes.size() != 3 || eigenvalues.size() != 3) {
            throw new IllegalArgumentException(
                    "Exactly 3 axes and eigenvalues are required"
            );
        }
    }

    /**
     * Computes the principal components of {@code points}.
     *
     * @throws IllegalArgumentException if fewer than 3 points are given
     */
    public static PrincipalComponents of(List<Point3D> points) {
        Objects.requireNonNull(points, "points");

        if (points.size() < 3) {
            throw new IllegalArgumentException(
                    "PCA requires at least 3 points: " + points.size()
            );
        }

        Point3D centroid = centroid(points);

        double xx = 0.0;
        double xy = 0.0;
        double xz = 0.0;
        double yy = 0.0;
        double yz = 0.0;
        double zz = 0.0;

        for (Point3D point : points) {
            double dx = point.x() - centroid.x();
            double dy = point.y() - centroid.y();
            double dz = point.z() - centroid.z();

            xx += dx * dx;
            xy += dx * dy;
            xz += dx * dz;
            yy += dy * dy;
            yz += dy * dz;
            zz += dz * dz;
        }

        double n = points.size();

        RealMatrix covariance = new Array2DRowRealMatrix(new double[][]{
                {xx / n, xy / n, xz / n},
                {xy / n, yy / n, yz / n},
                {xz / n, yz / n, zz / n}
        });

        EigenDecomposition decomposition =
                new EigenDecomposition(covariance);

        List<Integer> order = new ArrayList<>(List.of(0, 1, 2));
        order.sort(Comparator.comparingDouble(
                (Integer index) -> decomposition.getRealEigenvalue(index)
        ).reversed());

        List<Vector3D> axes = new ArrayList<>(3);
        List<Double> eigenvalues = new ArrayList<>(3);

        for (int index : order) {
            RealVector eigenvector =
                    decomposition.getEigenvector(index);
            axes.add(canonicalSign(new Vector3D(
                    eigenvector.getEntry(0),
                    eigenvector.getEntry(1),
                    eigenvector.getEntry(2)
            )));
            eigenvalues.add(decomposition.getRealEigenvalue(index));
        }

        return new PrincipalComponents(centroid, axes, eigenvalues);
    }

    /**
     * Signed projection of {@code point} onto axis {@code axisIndex},
     * relative to the centroid.
     */
    public double projection(Point3D point, int axisIndex) {
        Objects.requireNonNull(point, "point");

        return point.vectorFrom(centroid).dot(axes.get(axisIndex));
    }

    /**
     * Signed distance between two projections along an axis: the
     * offset of {@code point} from {@code reference} decomposed onto
     * axis {@code axisIndex}.
     */
    public double offsetAlong(
            Point3D point,
            Point3D reference,
            int axisIndex
    ) {
        return point.vectorFrom(reference).dot(axes.get(axisIndex));
    }

    private static Point3D centroid(List<Point3D> points) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;

        for (Point3D point : points) {
            x += point.x();
            y += point.y();
            z += point.z();
        }

        double n = points.size();

        return new Point3D(x / n, y / n, z / n);
    }

    private static Vector3D canonicalSign(Vector3D axis) {
        double absX = Math.abs(axis.x());
        double absY = Math.abs(axis.y());
        double absZ = Math.abs(axis.z());

        double dominant = axis.x();

        if (absY >= absX && absY >= absZ) {
            dominant = axis.y();
        } else if (absZ >= absX && absZ >= absY) {
            dominant = axis.z();
        }

        return dominant < 0.0 ? axis.negate() : axis;
    }
}
