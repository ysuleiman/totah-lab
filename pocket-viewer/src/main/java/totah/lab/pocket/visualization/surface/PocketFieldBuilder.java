package totah.lab.pocket.visualization.surface;

import totah.lab.pocket.Sphere;
import totah.lab.protein.Point3D;

import java.util.List;
import java.util.Objects;

public final class PocketFieldBuilder {
    private PocketFieldBuilder() {
    }

    public static PocketField fromAlphaSpheres(
            List<Sphere> spheres,
            double spacing) {
        Objects.requireNonNull(spheres, "spheres");
        if (spheres.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one alpha sphere is required");
        }
        if (!Double.isFinite(spacing) || spacing <= 0.0) {
            throw new IllegalArgumentException(
                    "Grid spacing must be positive");
        }

        double padding = spacing * 2.0;
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Sphere sphere : spheres) {
            minX = Math.min(minX, sphere.x() - sphere.radius());
            minY = Math.min(minY, sphere.y() - sphere.radius());
            minZ = Math.min(minZ, sphere.z() - sphere.radius());
            maxX = Math.max(maxX, sphere.x() + sphere.radius());
            maxY = Math.max(maxY, sphere.y() + sphere.radius());
            maxZ = Math.max(maxZ, sphere.z() + sphere.radius());
        }

        Point3D origin = new Point3D(
                minX - padding,
                minY - padding,
                minZ - padding);
        int sizeX = gridSize(origin.x(), maxX + padding, spacing);
        int sizeY = gridSize(origin.y(), maxY + padding, spacing);
        int sizeZ = gridSize(origin.z(), maxZ + padding, spacing);
        double[] values = new double[sizeX * sizeY * sizeZ];

        for (int z = 0; z < sizeZ; z++) {
            double pointZ = origin.z() + z * spacing;
            for (int y = 0; y < sizeY; y++) {
                double pointY = origin.y() + y * spacing;
                for (int x = 0; x < sizeX; x++) {
                    double pointX = origin.x() + x * spacing;
                    double value = Double.NEGATIVE_INFINITY;
                    for (Sphere sphere : spheres) {
                        double dx = pointX - sphere.x();
                        double dy = pointY - sphere.y();
                        double dz = pointZ - sphere.z();
                        double influence = sphere.radius()
                                - Math.sqrt(dx * dx + dy * dy + dz * dz);
                        value = Math.max(value, influence);
                    }
                    values[(z * sizeY + y) * sizeX + x] = value;
                }
            }
        }
        return new PocketField(
                origin, spacing, sizeX, sizeY, sizeZ, values);
    }

    private static int gridSize(
            double min,
            double max,
            double spacing) {
        return Math.max(2, (int) Math.ceil((max - min) / spacing) + 1);
    }
}
