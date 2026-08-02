package totah.lab.hephaestus.flexibility;

import java.util.List;
import java.util.Objects;

public record RigidFragment(
        String id,
        List<AtomReference> atoms,
        AtomReference anchor,
        String parentFragmentId) {
    public RigidFragment {
        id = Objects.requireNonNull(id, "id").trim();
        atoms = List.copyOf(atoms);
        Objects.requireNonNull(anchor, "anchor");
        if (id.isEmpty() || atoms.isEmpty() || !atoms.contains(anchor)) {
            throw new IllegalArgumentException("Fragment must have an ID and contain its anchor.");
        }
    }
}
