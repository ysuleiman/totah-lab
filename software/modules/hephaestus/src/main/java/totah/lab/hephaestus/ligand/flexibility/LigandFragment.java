package totah.lab.hephaestus.ligand.flexibility;

import java.util.List;
import java.util.Objects;

public record LigandFragment(
        String id,
        List<Integer> atomIndices,
        String parentFragmentId,
        Integer parentAtomIndex,
        Integer childAtomIndex) {

    public LigandFragment {
        Objects.requireNonNull(id, "id");
        atomIndices = List.copyOf(atomIndices);
        if (atomIndices.isEmpty()) {
            throw new IllegalArgumentException("A ligand fragment must contain atoms.");
        }
        boolean root = parentFragmentId == null;
        if (root != (parentAtomIndex == null && childAtomIndex == null)) {
            throw new IllegalArgumentException("Only the root fragment may omit branch endpoints.");
        }
    }
}
