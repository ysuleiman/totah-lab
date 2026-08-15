package totah.lab.prometheus.store;

import java.util.Objects;

import totah.lab.prometheus.identity.CanonicalHashing;

/**
 * Immutable identity of one source import. A changed source fingerprint,
 * importer version, or store schema creates a new compiled generation rather
 * than mutating previously compiled evidence.
 */
public record EvidenceImportDescriptor(
        String sourceId,
        String sourceFingerprint,
        String importerId,
        String importerVersion,
        int schemaVersion) {

    public EvidenceImportDescriptor {
        requireNonBlank(sourceId, "sourceId");
        requireNonBlank(sourceFingerprint, "sourceFingerprint");
        requireNonBlank(importerId, "importerId");
        requireNonBlank(importerVersion, "importerVersion");
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion must be >= 1");
        }
    }

    public String generationId() {
        return CanonicalHashing.sha256Hex(String.join("\n",
                "sourceId=" + sourceId,
                "sourceFingerprint=" + sourceFingerprint,
                "importerId=" + importerId,
                "importerVersion=" + importerVersion,
                "schemaVersion=" + schemaVersion));
    }

    private static void requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
    }
}
