package totah.lab.hephaestus.ligand.topology;

import java.util.Objects;

public record LigandAtomProperties(
        String ccdAtomId,
        int formalCharge,
        boolean aromatic,
        boolean leavingAtom) {

    public LigandAtomProperties {
        Objects.requireNonNull(ccdAtomId, "ccdAtomId");
        ccdAtomId = ccdAtomId.trim();
        if (ccdAtomId.isEmpty()) {
            throw new IllegalArgumentException("ccdAtomId must not be blank.");
        }
    }
}
