package totah.lab.hephaestus.flexibility;

import totah.lab.gaia.structure.ResidueId;

import java.util.Objects;

/** atomIndex is zero-based in canonical Chain -> Residue -> Atom order. */
public record AtomReference(
        ResidueId residue,
        String atomName,
        int atomIndex) {
    public AtomReference {
        Objects.requireNonNull(residue, "residue");
        atomName = Objects.requireNonNull(atomName, "atomName").trim();
        if (atomName.isEmpty()) throw new IllegalArgumentException("atomName must not be blank.");
        if (atomIndex < 0) throw new IllegalArgumentException("atomIndex must not be negative.");
    }
}
