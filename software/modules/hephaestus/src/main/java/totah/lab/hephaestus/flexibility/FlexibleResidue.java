package totah.lab.hephaestus.flexibility;

import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Objects;

public record FlexibleResidue(
        ResidueId residue,
        AtomReference anchorAtom,
        List<RigidFragment> fragments,
        List<RotatableBond> rotatableBonds) {
    public FlexibleResidue {
        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(anchorAtom, "anchorAtom");
        fragments = List.copyOf(fragments);
        rotatableBonds = List.copyOf(rotatableBonds);
    }
}
