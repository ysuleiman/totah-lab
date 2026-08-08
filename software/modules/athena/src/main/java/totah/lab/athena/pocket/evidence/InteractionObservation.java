package totah.lab.athena.pocket.evidence;

import java.util.Objects;

/** One versioned atom-pair interaction observation. */
public record InteractionObservation(
        LigandOccurrenceId ligand,
        EvidenceResidueId residue,
        EvidenceAtomId ligandAtom,
        EvidenceAtomId proteinAtom,
        InteractionType type,
        double distanceAngstrom,
        EvidenceMethod detectionMethod
) {
    public InteractionObservation {
        Objects.requireNonNull(ligand, "ligand");
        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(ligandAtom, "ligandAtom");
        Objects.requireNonNull(proteinAtom, "proteinAtom");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(detectionMethod, "detectionMethod");
        if (!Double.isFinite(distanceAngstrom) || distanceAngstrom < 0.0) {
            throw new IllegalArgumentException(
                    "distanceAngstrom must be finite and non-negative");
        }
    }
}
