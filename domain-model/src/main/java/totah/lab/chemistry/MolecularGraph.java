package totah.lab.chemistry;

import totah.lab.protein.Atom;

import java.util.List;
import java.util.Objects;

public record MolecularGraph(
        List<Atom> atoms,
        List<ChemicalBond> bonds,
        List<AtomChemicalProperties> atomProperties) {

    public MolecularGraph {
        atoms = List.copyOf(Objects.requireNonNull(atoms, "atoms is null"));
        bonds = List.copyOf(Objects.requireNonNull(bonds, "bonds is null"));
        atomProperties = List.copyOf(
                Objects.requireNonNull(atomProperties, "atomProperties is null"));
        if (atoms.size() != atomProperties.size()) {
            throw new IllegalArgumentException(
                    "Every atom must have exactly one chemical-properties entry");
        }
        for (ChemicalBond bond : bonds) {
            if (bond.atomIndexA() >= atoms.size() || bond.atomIndexB() >= atoms.size()) {
                throw new IllegalArgumentException("Chemical bond endpoint is outside atom list");
            }
        }
    }
}
