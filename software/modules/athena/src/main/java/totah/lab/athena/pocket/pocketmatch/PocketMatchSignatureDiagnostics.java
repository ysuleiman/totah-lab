package totah.lab.athena.pocket.pocketmatch;

/**
 * Diagnostic counters produced while building a PocketMatch signature.
 * Skipped residues are counted explicitly instead of silently dropping
 * unrepresentable input.
 *
 * <p>Part of Athena's independent, clean-room implementation of the
 * PocketMatch representation described by Yeturu &amp; Chandra (2008);
 * see the package documentation for the full citation and provenance.</p>
 */
public record PocketMatchSignatureDiagnostics(
        int inputResidueCount,
        int representedResidueCount,
        int generatedPointCount,
        int skippedResidueCount,
        int totalDistanceCount
) {

    /**
     * Placeholder used when a signature was loaded from a persisted form
     * that does not retain build diagnostics.
     */
    public static final PocketMatchSignatureDiagnostics NOT_TRACKED =
            new PocketMatchSignatureDiagnostics(0, 0, 0, 0, 0);

    public PocketMatchSignatureDiagnostics {
        if (inputResidueCount < 0
                || representedResidueCount < 0
                || generatedPointCount < 0
                || skippedResidueCount < 0
                || totalDistanceCount < 0) {
            throw new IllegalArgumentException(
                    "diagnostic counts must be non-negative"
            );
        }
    }
}
