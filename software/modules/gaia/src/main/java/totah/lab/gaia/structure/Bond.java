package totah.lab.gaia.structure;

import totah.lab.gaia.chemistry.BondOrder;

import java.util.Objects;

/** Undirected molecular connectivity between stable structure atom references. */
public record Bond(AtomReference atom1, AtomReference atom2, BondOrder order) {

    public Bond {
        Objects.requireNonNull(atom1, "atom1");
        Objects.requireNonNull(atom2, "atom2");
        Objects.requireNonNull(order, "order");
        if (atom1.equals(atom2)) {
            throw new IllegalArgumentException("A bond cannot connect an atom to itself.");
        }
        if (atom1.compareTo(atom2) > 0) {
            AtomReference first = atom1;
            atom1 = atom2;
            atom2 = first;
        }
    }
}
