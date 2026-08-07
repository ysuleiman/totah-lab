package totah.lab.daedalus.docking;

import totah.lab.gaia.geometry.Point3D;

import java.util.List;
import java.util.Objects;

/**
 * An AutoDock Vina search box derived from pocket geometry.
 *
 * <p>Formula: the center is the component-wise centroid of the pocket
 * points (alpha-sphere centers, or pocket atom coordinates when no
 * spheres exist). The size of each axis is the extent of the points on
 * that axis — expanded by the sphere radius on each side when spheres
 * are used — plus {@code 2 * padding}.</p>
 */
public record PocketGridBox(Point3D center, Point3D size) {

    public PocketGridBox {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(size, "size");
    }

    /**
     * Derives the box from pocket points.
     *
     * @param points sphere centers or atom coordinates; must not be
     *               empty
     * @param radii  per-point radii (alpha spheres), or {@code null}
     *               for atoms (zero radius)
     * @param padding padding added on each side of every axis
     */
    public static PocketGridBox fromPoints(
            List<Point3D> points,
            List<Double> radii,
            double padding
    ) {
        Objects.requireNonNull(points, "points");
        if (points.isEmpty()) {
            throw new IllegalArgumentException(
                    "A grid box needs at least one pocket point");
        }
        if (radii != null && radii.size() != points.size()) {
            throw new IllegalArgumentException(
                    "radii must match points one-to-one");
        }
        if (!Double.isFinite(padding) || padding < 0.0) {
            throw new IllegalArgumentException(
                    "padding must be finite and non-negative");
        }

        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (int index = 0; index < points.size(); index++) {
            Point3D point = points.get(index);
            double radius = radii == null ? 0.0 : radii.get(index);

            sumX += point.x();
            sumY += point.y();
            sumZ += point.z();

            minX = Math.min(minX, point.x() - radius);
            minY = Math.min(minY, point.y() - radius);
            minZ = Math.min(minZ, point.z() - radius);
            maxX = Math.max(maxX, point.x() + radius);
            maxY = Math.max(maxY, point.y() + radius);
            maxZ = Math.max(maxZ, point.z() + radius);
        }

        int count = points.size();
        return new PocketGridBox(
                new Point3D(
                        sumX / count,
                        sumY / count,
                        sumZ / count
                ),
                new Point3D(
                        maxX - minX + 2.0 * padding,
                        maxY - minY + 2.0 * padding,
                        maxZ - minZ + 2.0 * padding
                )
        );
    }

    /**
     * The box as vina options (default exhaustiveness, no seed).
     */
    public VinaDockingOptions toVinaOptions() {
        return VinaDockingOptions.ofBox(
                center.x(),
                center.y(),
                center.z(),
                size.x(),
                size.y(),
                size.z()
        );
    }
}
