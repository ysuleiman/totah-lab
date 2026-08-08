package totah.lab.athena.pocket.evidence;

import totah.lab.gaia.chemistry.BondOrder;

import java.util.Objects;

/** CCD-reported bond topology between component atom identifiers. */
public record ComponentBondDefinition(
        String atomIdA,
        String atomIdB,
        BondOrder order,
        boolean aromatic
) {
    public ComponentBondDefinition {
        atomIdA = requireText(atomIdA, "atomIdA");
        atomIdB = requireText(atomIdB, "atomIdB");
        Objects.requireNonNull(order, "order");
        if (atomIdA.equals(atomIdB)) {
            throw new IllegalArgumentException("A bond cannot connect an atom to itself");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
