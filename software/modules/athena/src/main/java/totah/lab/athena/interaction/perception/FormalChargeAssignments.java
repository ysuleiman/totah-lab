package totah.lab.athena.interaction.perception;

import totah.lab.gaia.structure.AtomReference;

import java.util.Map;
import java.util.Objects;

/** Checksum-bound, per-atom formal charges supplied by an upstream chemical model. */
public record FormalChargeAssignments(Map<AtomReference, Integer> charges) {
    public static final FormalChargeAssignments EMPTY = new FormalChargeAssignments(Map.of());

    public FormalChargeAssignments {
        charges = Map.copyOf(Objects.requireNonNull(charges, "charges"));
    }

    public int charge(AtomReference atom) {
        return charges.getOrDefault(atom, 0);
    }
}
