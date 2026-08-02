package totah.lab.hephaestus.ligand.topology;

import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.geometry.Point3D;

import java.util.Objects;

public record MissingLigandHydrogen(
        String atomName,
        int parentAtomIndex,
        BondOrder bondOrder,
        int formalCharge,
        boolean aromatic,
        boolean leavingAtom,
        Point3D modelPosition,
        Point3D idealPosition) {

    public MissingLigandHydrogen {
        Objects.requireNonNull(atomName, "atomName");
        atomName = atomName.trim();
        if (atomName.isEmpty()) {
            throw new IllegalArgumentException("atomName must not be blank.");
        }
        if (parentAtomIndex < 0) {
            throw new IllegalArgumentException("parentAtomIndex must not be negative.");
        }
        Objects.requireNonNull(bondOrder, "bondOrder");
    }
}
