package totah.lab.hephaestus.charge;

import java.util.Objects;

public record AssignedCharge(
        int atomIndex,
        String chainId,
        int residueNumber,
        Character insertionCode,
        String atomName,
        double charge,
        String amberType,
        String provenance) {

    public AssignedCharge {
        if (atomIndex < 0) {
            throw new IllegalArgumentException("atomIndex must not be negative.");
        }
        Objects.requireNonNull(chainId, "chainId");
        Objects.requireNonNull(atomName, "atomName");
        Objects.requireNonNull(provenance, "provenance");
        if (!Double.isFinite(charge)) {
            throw new IllegalArgumentException("charge must be finite.");
        }
    }
}
