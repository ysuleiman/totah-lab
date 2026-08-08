package totah.lab.athena.pocket.evidence;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Exact source and extraction identity for a pocket evidence aggregate. */
public record EvidenceProvenance(
        String sourceProvider,
        String sourceIdentifier,
        String sourceVersion,
        EvidenceMethod extractionMethod,
        Instant extractedAt,
        Map<String, String> sourceAttributes
) {
    public EvidenceProvenance {
        sourceProvider = requireText(sourceProvider, "sourceProvider");
        sourceIdentifier = requireText(sourceIdentifier, "sourceIdentifier");
        sourceVersion = requireText(sourceVersion, "sourceVersion");
        Objects.requireNonNull(extractionMethod, "extractionMethod");
        Objects.requireNonNull(extractedAt, "extractedAt");
        sourceAttributes = Map.copyOf(new TreeMap<>(
                Objects.requireNonNull(sourceAttributes, "sourceAttributes")));
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
