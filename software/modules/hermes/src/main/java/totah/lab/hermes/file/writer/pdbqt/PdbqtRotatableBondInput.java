package totah.lab.hermes.file.writer.pdbqt;

import java.util.Objects;

public record PdbqtRotatableBondInput(
        int parentAtomIndex,
        int childAtomIndex,
        String parentFragmentId,
        String childFragmentId) {
    public PdbqtRotatableBondInput {
        Objects.requireNonNull(parentFragmentId, "parentFragmentId");
        Objects.requireNonNull(childFragmentId, "childFragmentId");
    }
}
