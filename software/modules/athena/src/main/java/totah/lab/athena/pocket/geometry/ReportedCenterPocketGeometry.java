package totah.lab.athena.pocket.geometry;

import totah.lab.gaia.geometry.BoundingBox;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;

import java.util.Objects;

/**
 * Fallback geometry for pockets that carry only a reported center: no
 * alpha spheres and no structure-resolvable residue heavy atoms.
 */
public final class ReportedCenterPocketGeometry
        implements PocketGeometryStrategy {

    @Override
    public BoundingBox bounds(Structure structure, Pocket pocket) {
        Objects.requireNonNull(structure, "structure");
        Point3D center = center(pocket);
        return new BoundingBox(center, center);
    }

    @Override
    public Point3D centroid(Structure structure, Pocket pocket) {
        Objects.requireNonNull(structure, "structure");
        return center(pocket);
    }

    @Override
    public PocketGeometryBasis basis() {
        return PocketGeometryBasis.REPORTED_CENTER;
    }

    private static Point3D center(Pocket pocket) {
        return Objects.requireNonNull(pocket, "pocket").center();
    }
}
