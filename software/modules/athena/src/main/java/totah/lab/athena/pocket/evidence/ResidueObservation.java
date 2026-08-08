package totah.lab.athena.pocket.evidence;

import java.util.List;
import java.util.Objects;

/** Source-observed pocket residue with its atoms in source order. */
public record ResidueObservation(
        EvidenceResidueId id,
        String residueName,
        List<ObservedAtom> atoms
) {
    public ResidueObservation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(residueName, "residueName");
        if (residueName.isBlank()) {
            throw new IllegalArgumentException("residueName must not be blank");
        }
        residueName = residueName.trim();
        atoms = List.copyOf(Objects.requireNonNull(atoms, "atoms"));
    }
}
