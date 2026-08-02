package totah.lab.gaia.structure;

import totah.lab.gaia.chemistry.BondOrder;

import java.util.Objects;

/** General molecular connectivity between atoms in structure atom order. */
public record Bond(
        int atomIndexA,
        int atomIndexB,
        BondOrder order,
        boolean aromatic) {

    public Bond {
        if (atomIndexA < 0 || atomIndexB < 0) {
            throw new IllegalArgumentException(
                    "Bond atom indices must be non-negative.");
        }
        if (atomIndexA == atomIndexB) {
            throw new IllegalArgumentException(
                    "A bond cannot connect an atom to itself.");
        }
        Objects.requireNonNull(order, "order");
    }
}
