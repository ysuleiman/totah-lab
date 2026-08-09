package totah.lab.athena.ligand.interaction;

import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.ResidueId;

import java.util.Objects;

/** One atom-level, chemically typed receptor-ligand interaction. */
public record LigandInteraction(
        InteractionType type,
        ResidueId residue,
        Atom receptorAtom,
        Atom ligandAtom,
        double distance,
        Double angleDegrees,
        String basis
) {
    public LigandInteraction {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(receptorAtom, "receptorAtom");
        Objects.requireNonNull(ligandAtom, "ligandAtom");
        Objects.requireNonNull(basis, "basis");
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException(
                    "distance must be finite and non-negative");
        }
        if (angleDegrees != null && (!Double.isFinite(angleDegrees)
                || angleDegrees < 0.0 || angleDegrees > 180.0)) {
            throw new IllegalArgumentException(
                    "angleDegrees must be between 0 and 180");
        }
    }
}
