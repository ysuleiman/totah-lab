package totah.lab.athena.pocket.geometry;

import totah.lab.gaia.geometry.BoundingBox;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;

import java.util.Objects;

public final class AlphaSpherePocketGeometry
        implements PocketGeometryStrategy {

    @Override
    public BoundingBox bounds(Structure structure, Pocket pocket) {
        Objects.requireNonNull(structure, "structure");
        return PocketGeometry.alphaSphereBounds(spheres(pocket), true);
    }

    @Override
    public Point3D centroid(Structure structure, Pocket pocket) {
        Objects.requireNonNull(structure, "structure");
        return PocketGeometry.alphaSphereCentroid(spheres(pocket));
    }

    @Override
    public PocketGeometryBasis basis() {
        return PocketGeometryBasis.ALPHA_SPHERES;
    }

    private static AlphaSphereSet spheres(Pocket pocket) {
        Objects.requireNonNull(pocket, "pocket");
        return pocket.alphaSphereSet()
                .filter(set -> !set.spheres().isEmpty())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pocket has no alpha spheres: " + pocket.id()));
    }
}
