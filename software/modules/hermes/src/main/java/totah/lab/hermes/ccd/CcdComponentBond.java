package totah.lab.hermes.ccd;

import totah.lab.gaia.chemistry.BondOrder;

import java.util.Objects;

/** One bond definition reported by the Chemical Component Dictionary. */
public record CcdComponentBond(
        String atomIdA,
        String atomIdB,
        BondOrder order,
        boolean aromatic) {

    public CcdComponentBond {
        atomIdA = requireText(atomIdA, "atomIdA");
        atomIdB = requireText(atomIdB, "atomIdB");
        Objects.requireNonNull(order, "order");
        if (atomIdA.equalsIgnoreCase(atomIdB)) {
            throw new IllegalArgumentException("A CCD bond cannot connect an atom to itself.");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return normalized;
    }
}
