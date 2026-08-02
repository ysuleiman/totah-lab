package totah.lab.athena.pocket.geometry;

import totah.lab.gaia.geometry.BoundingBox;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Objects;

public record PocketGeometryResult(
        BoundingBox bounds,
        Point3D centroid,
        PocketGeometryBasis basis,
        List<ResidueId> unresolvedResidues) {

    public PocketGeometryResult {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(centroid, "centroid");
        Objects.requireNonNull(basis, "basis");
        unresolvedResidues = List.copyOf(Objects.requireNonNull(
                unresolvedResidues,
                "unresolvedResidues"));
    }
}
