package totah.lab.prometheus.inventory;

import java.util.Objects;

/** One directly observed provenance omission on one evidence record. */
public record ProvenanceGap(
        EvidenceDimension dimension,
        String evidenceHash,
        String moleculeId,
        ProvenanceGapType type,
        String detail) {

    public ProvenanceGap {
        Objects.requireNonNull(dimension, "dimension");
        evidenceHash = requireNonBlank(evidenceHash, "evidenceHash");
        moleculeId = requireNonBlank(moleculeId, "moleculeId");
        Objects.requireNonNull(type, "type");
        detail = requireNonBlank(detail, "detail");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
        return value;
    }
}
