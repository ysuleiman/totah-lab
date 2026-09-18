package totah.lab.aether.model;

import java.util.Objects;

/** Explicit fragment identity and ordered real nuclei at the frozen complex geometry. */
public record MolecularFragment(String id, QuantumSystem system) {
    public MolecularFragment {
        Objects.requireNonNull(id);Objects.requireNonNull(system);
        if(id.isBlank())throw new IllegalArgumentException("Fragment identity must not be blank");
        if(system.multiplicity()!=1)throw new IllegalArgumentException("Interaction V1 requires closed-shell singlet fragments");
    }
}
