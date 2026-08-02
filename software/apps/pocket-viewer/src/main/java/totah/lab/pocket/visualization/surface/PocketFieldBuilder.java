package totah.lab.pocket.visualization.surface;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.AlphaSphere;

import java.util.List;
import java.util.Objects;

public final class PocketFieldBuilder {
    private PocketFieldBuilder() {
    }

    public static PocketField fromAlphaSpheres(
            List<AlphaSphere> spheres,
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
        for (AlphaSphere sphere : spheres) {
            minX = Math.min(minX, sphere.center().x() - sphere.radius());
            minY = Math.min(minY, sphere.center().y() - sphere.radius());
            minZ = Math.min(minZ, sphere.center().z() - sphere.radius());
            maxX = Math.max(maxX, sphere.center().x() + sphere.radius());
            maxY = Math.max(maxY, sphere.center().y() + sphere.radius());
            maxZ = Math.max(maxZ, sphere.center().z() + sphere.radius());
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
                    for (AlphaSphere sphere : spheres) {
                        double dx = pointX - sphere.center().x();
                        double dy = pointY - sphere.center().y();
                        double dz = pointZ - sphere.center().z();
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
