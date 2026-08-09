package totah.lab.hermes.file.pdbqt;

import java.util.List;
import java.util.Objects;

public record PdbqtFragment(
        String fragmentId,
        List<PdbqtAtomReference> atoms,
        int anchorAtomIndex,
        String parentFragmentId) {
    public PdbqtFragment {
        Objects.requireNonNull(fragmentId, "fragmentId"); atoms = List.copyOf(atoms);
        if (atoms.isEmpty()) throw new IllegalArgumentException("Fragment atoms must not be empty.");
    }
}
