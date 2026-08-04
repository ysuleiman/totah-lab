package totah.lab.web.service;

import totah.lab.gaia.geometry.BoundingBox;
import totah.lab.gaia.geometry.Point3D;

import java.util.List;

/**
 * Identity and full point cloud of one pocket for inspection UI
 * consumption. Points come from the pocket-atom coordinates via
 * {@code PocketGeometryLoader}; centroid and bounds are computed by
 * Athena's {@code PocketPointCloud}. {@code alphaSpheres} carries the
 * persisted fpocket alpha spheres (with radii) when the basis is
 * ALPHA_SPHERES and is empty otherwise — radii are never fabricated.
 */
public record PocketGeometryView(
        long pocketId,
        Long structureId,
        String sourceAccession,
        Integer pocketNumber,
        int pointCount,
        Point3D centroid,
        BoundingBox bounds,
        String basis,
        List<Point3D> points,
        List<AlphaSphereView> alphaSpheres
) {
}
