package totah.lab.protein;

import java.util.Objects;
import java.util.regex.Pattern;

public final class TargetId {

    // Full UniProt accession pattern: 6-char forms plus the 10-char forms
    // (e.g. A0A023GPI8, common for AlphaFold models)
    private static final Pattern UNIPROT_ACCESSION =
            Pattern.compile("(?:[OPQ][0-9][A-Z0-9]{3}[0-9]|[A-NR-Z][0-9](?:[A-Z][A-Z0-9]{2}[0-9]){1,2})([.-].*)?");

    private final String value;
    private final String uniProtId;  // <-- NEW: store the actual UniProt ID

    private TargetId(String value, String uniProtId) {
        this.value = value;
        this.uniProtId = uniProtId;
    }

    /**
     * Creates TargetId from a raw string (e.g., "Q9H8H3" or a filename).
     * Extracts UniProt ID automatically.
     */
    public static TargetId of(String value) {
        Objects.requireNonNull(value, "Target ID cannot be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Target ID cannot be empty");
        }

        // Extract UniProt ID from path or filename (e.g., "/Q9H8H3" or "Q9H8H3.pdb")
        String uniProtId = extractUniProtId(normalized);

        if (uniProtId == null) {
            throw new IllegalArgumentException(
                    "No valid UniProt accession found in: [" + normalized + "]");
        }

        return new TargetId(normalized, uniProtId);
    }

    /**
     * Creates TargetId directly from a known UniProt ID.
     */
    public static TargetId fromUniProt(String uniProtId) {
        Objects.requireNonNull(uniProtId, "UniProt ID cannot be null");
        String normalized = uniProtId.trim().toUpperCase();

        if (!UNIPROT_ACCESSION.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Invalid UniProt accession: [" + normalized + "]");
        }

        return new TargetId(normalized, normalized);
    }

    /**
     * Extracts UniProt ID from a path, filename, or raw string.
     * Handles: "Q9H8H3", "/Q9H8H3", "Q9H8H3.pdb", "/path/to/Q9H8H3", etc.
     * Matching is case-insensitive; the returned ID is uppercased.
     */
    private static String extractUniProtId(String input) {
        // Remove path separators and get the last component
        String[] parts = input.split("[/\\\\]");
        String lastPart = parts[parts.length - 1].toUpperCase();

        // Remove common extensions
        String withoutExt = lastPart.replaceAll("\\.(PDB|CIF|MMTF|ENT)$", "");

        // Check if it's a valid UniProt ID
        if (UNIPROT_ACCESSION.matcher(withoutExt).matches()) {
            return withoutExt;
        }

        // Try to find UniProt pattern anywhere in the string
        var matcher = UNIPROT_ACCESSION.matcher(input.toUpperCase());
        if (matcher.find()) {
            return matcher.group();
        }

        return null;
    }

    public String value() {
        return value;
    }

    /**
     * Returns the extracted/clean UniProt accession ID.
     * This is what you want for Protein.setUniProtId().
     */
    public String uniProtId() {
        return uniProtId;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TargetId other)) return false;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}