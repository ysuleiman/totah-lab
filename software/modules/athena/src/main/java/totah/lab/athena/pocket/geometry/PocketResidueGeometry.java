package totah.lab.athena.pocket.geometry;

import totah.lab.gaia.geometry.BoundingBox;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Objects;

public record PocketResidueGeometry(
        BoundingBox bounds,
        Point3D centroid,
        List<Residue> resolvedResidues,
        List<ResidueId> unresolvedResidues) {

    public PocketResidueGeometry {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(centroid, "centroid");
        resolvedResidues = List.copyOf(Objects.requireNonNull(
                resolvedResidues,
                "resolvedResidues"));
        unresolvedResidues = List.copyOf(Objects.requireNonNull(
                unresolvedResidues,
                "unresolvedResidues"));
    }
}
