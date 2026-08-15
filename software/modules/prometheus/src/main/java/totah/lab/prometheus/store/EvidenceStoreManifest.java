package totah.lab.prometheus.store;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Publication-auditable manifest for one immutable compiled evidence generation. */
public record EvidenceStoreManifest(
        EvidenceImportDescriptor importDescriptor,
        Instant compiledAt,
        int quantumCount,
        int classicalCount,
        Map<String, String> recordSha256) {

    public EvidenceStoreManifest {
        Objects.requireNonNull(importDescriptor, "importDescriptor");
        Objects.requireNonNull(compiledAt, "compiledAt");
        if (quantumCount < 0 || classicalCount < 0) {
            throw new IllegalArgumentException("evidence counts must be non-negative");
        }
        recordSha256 = Map.copyOf(Objects.requireNonNull(recordSha256, "recordSha256"));
        if (recordSha256.size() != quantumCount + classicalCount) {
            throw new IllegalArgumentException("checksum count does not match evidence counts");
        }
    }
}
