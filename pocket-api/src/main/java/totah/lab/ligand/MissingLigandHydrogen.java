package totah.lab.ligand;

import totah.lab.chemistry.BondOrder;
import totah.lab.protein.Point3D;

import java.util.Objects;

public record MissingLigandHydrogen(
        String ccdAtomId,
        int parentAtomIndex,
        BondOrder bondOrder,
        int formalCharge,
        boolean aromatic,
        boolean leavingAtom,
        Point3D modelPosition,
        Point3D idealPosition) {

    public MissingLigandHydrogen {
        Objects.requireNonNull(ccdAtomId, "ccdAtomId is null");
        Objects.requireNonNull(bondOrder, "bondOrder is null");
        if (ccdAtomId.isBlank()) {
            throw new IllegalArgumentException("ccdAtomId is blank");
        }
        if (parentAtomIndex < 0) {
            throw new IllegalArgumentException("parentAtomIndex must be non-negative");
        }
        if (bondOrder != BondOrder.SINGLE) {
            throw new IllegalArgumentException("Hydrogen must use a single bond");
        }
    }
}
