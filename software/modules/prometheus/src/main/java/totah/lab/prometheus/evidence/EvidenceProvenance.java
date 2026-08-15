package totah.lab.prometheus.evidence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Where an evidence record came from: source artifact path and checksum,
 * ingestion time, the evidence hashes it was derived from, and a free-form note
 * (may be empty, never null).
 */
public record EvidenceProvenance(
        String sourcePath,
        String sha256,
        Instant ingestedAt,
        List<String> derivedFromEvidenceHashes,
        String note) {

    public EvidenceProvenance {
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(ingestedAt, "ingestedAt");
        derivedFromEvidenceHashes = List.copyOf(
                Objects.requireNonNull(derivedFromEvidenceHashes, "derivedFromEvidenceHashes"));
        Objects.requireNonNull(note, "note");
    }
}
