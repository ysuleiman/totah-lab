package totah.lab.prometheus.identity;

import java.util.Objects;

/**
 * Canonical identity of a single atom of a molecule.
 *
 * <p>{@code canonicalIndex} is the 1-based canonical serial of the atom within the
 * molecule and is the only stable identifier across artifacts.
 *
 * <p>IMPORTANT: the number embedded in {@code label} is NOT necessarily the canonical
 * index. Labels come from source files and follow whatever numbering the file used;
 * e.g. TSL's atom labeled "C9" is canonical serial 10. Never parse a canonical index
 * out of a label.
 */
public record CanonicalAtomId(
        int canonicalIndex,
        String label,
        String elementSymbol) {

    public CanonicalAtomId {
        if (canonicalIndex < 1) {
            throw new IllegalArgumentException("canonicalIndex must be >= 1, got " + canonicalIndex);
        }
        requireNonBlank(label, "label");
        requireNonBlank(elementSymbol, "elementSymbol");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
        return value;
    }
}
