package totah.lab.gaia.graph;

import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Objects;

/** Atom-pair distances establishing proximity between two residues. */
public record ResidueAtomProximity(
        ResidueId first,
        ResidueId second,
        List<AtomPairDistance> atomPairs) {

    public ResidueAtomProximity {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (ResidueIds.compare(first, second) >= 0) {
            throw new IllegalArgumentException(
                    "Residue endpoints must be distinct and canonical");
        }
        atomPairs = List.copyOf(
                Objects.requireNonNull(atomPairs, "atomPairs"));
        if (atomPairs.isEmpty()) {
            throw new IllegalArgumentException(
                    "A residue proximity requires at least one atom pair");
        }
    }
}
