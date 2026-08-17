package totah.lab.gaia.graph;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.ResidueId;

import java.util.Objects;

record IndexedAtom(
        int ordinal,
        ResidueId residueId,
        AtomReference reference,
        Point3D position) {

    IndexedAtom {
        Objects.requireNonNull(residueId, "residueId");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(position, "position");
    }
}
