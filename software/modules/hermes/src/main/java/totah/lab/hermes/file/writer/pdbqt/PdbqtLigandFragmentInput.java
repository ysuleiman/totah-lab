package totah.lab.hermes.file.writer.pdbqt;

import java.util.List;
import java.util.Objects;

public record PdbqtLigandFragmentInput(
        String id,
        List<Integer> canonicalAtomIndices,
        String parentFragmentId,
        Integer parentAtomIndex,
        Integer childAtomIndex) {

    public PdbqtLigandFragmentInput {
        Objects.requireNonNull(id, "id");
        canonicalAtomIndices = List.copyOf(canonicalAtomIndices);
        if (canonicalAtomIndices.isEmpty()) {
            throw new IllegalArgumentException("A ligand fragment must contain atoms.");
        }
    }
}
