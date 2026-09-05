package totah.lab.athena.interaction.perception;

import totah.lab.gaia.structure.Atom;

import java.util.List;
import java.util.Objects;

/**
 * Result of hydrophobic atom perception: the perceived hydrophobic atoms in
 * structure traversal order plus the provenance of the perception.
 *
 * @param atoms perceived hydrophobic atoms (structure traversal order)
 * @param provenance how the perception was derived
 * @param note human-readable provenance note
 */
public record HydrophobicAtoms(
        List<Atom> atoms,
        PerceptionProvenance provenance,
        String note) {

    public HydrophobicAtoms {
        atoms = List.copyOf(Objects.requireNonNull(atoms, "atoms"));
        Objects.requireNonNull(provenance, "provenance");
        note = requireNote(note);
    }

    /** Returns {@code true} when the perception used a degraded fallback. */
    public boolean degraded() {
        return provenance.isDegraded();
    }

    static String requireNote(String note) {
        Objects.requireNonNull(note, "note");
        String normalized = note.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("note must not be blank");
        }
        return normalized;
    }
}
