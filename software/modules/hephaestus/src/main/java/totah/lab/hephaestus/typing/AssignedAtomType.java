package totah.lab.hephaestus.typing;

import java.util.Objects;

public record AssignedAtomType(
        int atomIndex,
        String chainId,
        int residueNumber,
        Character insertionCode,
        String atomName,
        String autoDockType) {

    public AssignedAtomType {
        if (atomIndex < 0) {
            throw new IllegalArgumentException("atomIndex must not be negative.");
        }
        Objects.requireNonNull(chainId, "chainId");
        Objects.requireNonNull(atomName, "atomName");
        Objects.requireNonNull(autoDockType, "autoDockType");
    }
}
