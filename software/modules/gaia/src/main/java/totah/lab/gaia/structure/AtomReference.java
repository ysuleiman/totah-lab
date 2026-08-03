package totah.lab.gaia.structure;

import java.util.Objects;

/** Stable atom identity within a structure. */
public record AtomReference(
        String chainId,
        int residueNumber,
        char insertionCode,
        String atomName) implements Comparable<AtomReference> {

    public AtomReference {
        chainId = requireText(chainId, "chainId");
        insertionCode = normalizeInsertionCode(insertionCode);
        atomName = requireText(atomName, "atomName");
    }

    public static char normalizeInsertionCode(char insertionCode) {
        return insertionCode == '\0' || Character.isWhitespace(insertionCode)
                ? ' '
                : insertionCode;
    }

    @Override
    public int compareTo(AtomReference other) {
        Objects.requireNonNull(other, "other");
        int comparison = chainId.compareTo(other.chainId);
        if (comparison != 0) return comparison;
        comparison = Integer.compare(residueNumber, other.residueNumber);
        if (comparison != 0) return comparison;
        comparison = Character.compare(insertionCode, other.insertionCode);
        if (comparison != 0) return comparison;
        return atomName.compareTo(other.atomName);
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
