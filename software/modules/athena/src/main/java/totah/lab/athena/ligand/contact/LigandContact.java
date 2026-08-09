package totah.lab.athena.ligand.contact;

import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.ResidueId;

public record LigandContact(
        Atom ligandAtom,
        Atom receptorAtom,
        ResidueId residue,
        double distance,
        ContactType type
) {
    public LigandContact {
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException(
                    "distance must be finite and non-negative"
            );
        }
    }
}
