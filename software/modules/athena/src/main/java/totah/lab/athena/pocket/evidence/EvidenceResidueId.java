package totah.lab.athena.pocket.evidence;

import java.util.Objects;

/** Stable residue identity within one structure model. */
public record EvidenceResidueId(
        String chainId,
        int modelNumber,
        String residueId,
        String insertionCode
) {
    public EvidenceResidueId {
        chainId = requireText(chainId, "chainId");
        residueId = requireText(residueId, "residueId");
        if (modelNumber < 1) {
            throw new IllegalArgumentException("modelNumber must be positive");
        }
        insertionCode = insertionCode == null || insertionCode.isBlank()
                ? null : insertionCode.trim();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
