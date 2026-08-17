package totah.lab.athena.fragment;

import java.util.Objects;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.Vector3D;

public record SpatialAttachmentVector(int fragmentAtomIndex, Point3D origin, Vector3D direction) {
    public SpatialAttachmentVector {
        if (fragmentAtomIndex < 0) throw new IllegalArgumentException("fragmentAtomIndex must be non-negative");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(direction, "direction");
        if (direction.isZero()) throw new IllegalArgumentException("direction must be non-zero");
    }
}
