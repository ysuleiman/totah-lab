package totah.lab.ligand;

import totah.lab.protein.Atom;

import java.util.List;
import java.util.Objects;

public record Ligand(
        String identifier,
        List<Atom> atoms
) {

    public Ligand {
        Objects.requireNonNull(identifier, "identifier");
        if (identifier.isBlank()) {
            throw new IllegalArgumentException(
                    "identifier must not be blank"
            );
        }
        atoms = List.copyOf(atoms);
        if (atoms.isEmpty()) {
            throw new IllegalArgumentException("atoms must not be empty");
        }
    }
}
