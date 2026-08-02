package totah.lab.hephaestus.flexibility;

import java.util.Objects;

public record RotatableBond(
        AtomReference parentAtom,
        AtomReference childAtom,
        String parentFragmentId,
        String childFragmentId) {
    public RotatableBond {
        Objects.requireNonNull(parentAtom, "parentAtom");
        Objects.requireNonNull(childAtom, "childAtom");
        Objects.requireNonNull(parentFragmentId, "parentFragmentId");
        Objects.requireNonNull(childFragmentId, "childFragmentId");
        if (!parentAtom.residue().equals(childAtom.residue())) {
            throw new IllegalArgumentException("Rotatable bond must be residue-local.");
        }
    }
}
