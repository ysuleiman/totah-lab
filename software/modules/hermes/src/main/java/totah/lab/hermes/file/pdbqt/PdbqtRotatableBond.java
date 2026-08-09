package totah.lab.hermes.file.pdbqt;

import java.util.Objects;

public record PdbqtRotatableBond(
        int parentAtomIndex,
        int childAtomIndex,
        String parentFragmentId,
        String childFragmentId) {
    public PdbqtRotatableBond {
        Objects.requireNonNull(parentFragmentId, "parentFragmentId");
        Objects.requireNonNull(childFragmentId, "childFragmentId");
    }
}
