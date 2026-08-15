package totah.lab.prometheus.identity;

import java.util.Objects;

/**
 * Canonical identity of a molecule: a stable id, a human-readable name,
 * and its molecular formula. All fields must be non-blank.
 */
public record MoleculeIdentity(
        String moleculeId,
        String displayName,
        String molecularFormula) {

    public MoleculeIdentity {
        requireNonBlank(moleculeId, "moleculeId");
        requireNonBlank(displayName, "displayName");
        requireNonBlank(molecularFormula, "molecularFormula");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
        return value;
    }
}
