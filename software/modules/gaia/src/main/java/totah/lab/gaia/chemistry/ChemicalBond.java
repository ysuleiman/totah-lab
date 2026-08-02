package totah.lab.gaia.chemistry;

import java.util.Objects;

public record ChemicalBond(
        int atomIndexA,
        int atomIndexB,
        BondOrder order,
        boolean aromatic) {

    public ChemicalBond {
        if (atomIndexA < 0 || atomIndexB < 0) {
            throw new IllegalArgumentException("Chemical bond atom indices must be non-negative");
        }
        if (atomIndexA == atomIndexB) {
            throw new IllegalArgumentException("Chemical bond cannot connect an atom to itself");
        }
        Objects.requireNonNull(order, "order is null");
    }
}
