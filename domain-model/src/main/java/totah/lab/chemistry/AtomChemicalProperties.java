package totah.lab.chemistry;

import java.util.Objects;

public record AtomChemicalProperties(
        String ccdAtomId,
        int formalCharge,
        boolean aromatic,
        boolean leavingAtom,
        Integer depositedAtomIndex) {

    public AtomChemicalProperties {
        Objects.requireNonNull(ccdAtomId, "ccdAtomId is null");
        if (ccdAtomId.isBlank()) {
            throw new IllegalArgumentException("ccdAtomId is blank");
        }
        if (depositedAtomIndex != null && depositedAtomIndex < 0) {
            throw new IllegalArgumentException("depositedAtomIndex must be non-negative");
        }
    }
}
