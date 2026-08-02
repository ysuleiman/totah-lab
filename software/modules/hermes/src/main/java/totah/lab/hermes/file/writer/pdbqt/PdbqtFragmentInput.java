package totah.lab.hermes.file.writer.pdbqt;

import java.util.List;
import java.util.Objects;

public record PdbqtFragmentInput(
        String fragmentId,
        List<PdbqtAtomInput> atoms,
        int anchorAtomIndex,
        String parentFragmentId) {
    public PdbqtFragmentInput {
        Objects.requireNonNull(fragmentId, "fragmentId"); atoms = List.copyOf(atoms);
        if (atoms.isEmpty()) throw new IllegalArgumentException("Fragment atoms must not be empty.");
    }
}
