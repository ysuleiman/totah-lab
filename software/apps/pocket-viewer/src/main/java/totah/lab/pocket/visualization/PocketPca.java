package totah.lab.pocket.visualization;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.correlation.Covariance;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.Pocket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class PocketPca {

    private PocketPca() {
    }

    /**
     * Calculates the natural orientation of a pocket using the centers of its
     * alpha spheres.
     *
     * PC1: longest direction
     * PC2: second-longest direction
     * PC3: shortest direction, commonly useful as a slice normal
     */
    public static Orientation calculate(Pocket pocket) {
        Objects.requireNonNull(pocket, "pocket");

        List<AlphaSphere> spheres = pocket.alphaSphereSet()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pocket has no alpha spheres"))
                .spheres();

        if (spheres.size() < 3) {
            throw new IllegalArgumentException(
                    "At least three alpha spheres are required for PCA");
        }

        double[][] coordinates = new double[spheres.size()][3];

        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;

        for (int i = 0; i < spheres.size(); i++) {
            AlphaSphere sphere = spheres.get(i);

            coordinates[i][0] = sphere.center().x();
            coordinates[i][1] = sphere.center().y();
            coordinates[i][2] = sphere.center().z();

            sumX += sphere.center().x();
            sumY += sphere.center().y();
            sumZ += sphere.center().z();
        }

        Point3D center = new Point3D(
                sumX / spheres.size(),
                sumY / spheres.size(),
                sumZ / spheres.size());

        /*
         * Covariance centers the data internally, so the original coordinate
         * matrix can be supplied directly.
         */
        RealMatrix coordinateMatrix =
                new Array2DRowRealMatrix(coordinates, false);

        RealMatrix covariance =
                new Covariance(coordinateMatrix)
                        .getCovarianceMatrix();

        EigenDecomposition decomposition =
                new EigenDecomposition(covariance);

        List<EigenPair> eigenPairs = new ArrayList<>(3);

        for (int i = 0; i < 3; i++) {
            Vector3D vector = normalize(new Vector3D(
                    decomposition.getEigenvector(i).getEntry(0),
                    decomposition.getEigenvector(i).getEntry(1),
                    decomposition.getEigenvector(i).getEntry(2)));

            eigenPairs.add(new EigenPair(
                    decomposition.getRealEigenvalue(i),
                    vector));
        }

        eigenPairs.sort(
                Comparator.comparingDouble(EigenPair::value)
                        .reversed());

        Vector3D pc1 = eigenPairs.get(0).vector();
        Vector3D pc2 = eigenPairs.get(1).vector();

        /*
         * Recalculate PC3 using a cross product so the three axes form a
         * consistent right-handed coordinate system.
         */
        Vector3D pc3 = normalize(cross(pc1, pc2));
        pc2 = normalize(cross(pc3, pc1));

        return new Orientation(
                center,
                pc1,
                pc2,
                pc3,
                eigenPairs.get(0).value(),
                eigenPairs.get(1).value(),
                eigenPairs.get(2).value());
    }

    public static Vector3D normalize(Vector3D vector) {
        double length = Math.sqrt(
                vector.x() * vector.x()
                        + vector.y() * vector.y()
                        + vector.z() * vector.z());

        if (length == 0.0) {
            throw new IllegalArgumentException(
                    "Cannot normalize a zero-length vector");
        }

        return new Vector3D(
                vector.x() / length,
                vector.y() / length,
                vector.z() / length);
    }

    public static Vector3D cross(Vector3D first, Vector3D second) {
        return new Vector3D(
                first.y() * second.z()
                        - first.z() * second.y(),

                first.z() * second.x()
                        - first.x() * second.z(),

                first.x() * second.y()
                        - first.y() * second.x());
    }

    public record Vector3D(
            double x,
            double y,
            double z) {
    }

    public record Orientation(
            Point3D center,
            Vector3D pc1,
            Vector3D pc2,
            Vector3D pc3,
            double eigenvalue1,
            double eigenvalue2,
            double eigenvalue3) {

        public PocketProjection.SlicePlane topPlane() {
            return new PocketProjection.SlicePlane(
                    center,
                    pc1,
                    pc2,
                    pc3);
        }

        public PocketProjection.SlicePlane sidePlane() {
            return new PocketProjection.SlicePlane(
                    center,
                    pc1,
                    pc3,
                    pc2);
        }

        public PocketProjection.SlicePlane endPlane() {
            return new PocketProjection.SlicePlane(
                    center,
                    pc2,
                    pc3,
                    pc1);
        }
    }

    private record EigenPair(
            double value,
            Vector3D vector) {
    }
}
