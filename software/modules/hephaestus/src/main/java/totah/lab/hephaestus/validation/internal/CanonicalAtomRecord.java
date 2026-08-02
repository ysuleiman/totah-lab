package totah.lab.hephaestus.validation.internal;

import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.hephaestus.flexibility.AtomReference;

public record CanonicalAtomRecord(
        int index,
        AtomReference reference,
        String residueName,
        Residue residue,
        Atom atom) {
}
