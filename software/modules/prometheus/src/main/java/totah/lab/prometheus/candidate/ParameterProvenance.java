package totah.lab.prometheus.candidate;

import java.util.List;
import java.util.Objects;

/**
 * Provenance of a single derived parameter: how it was obtained, from which
 * evidence, and where it stands in validation. {@code literatureReference} may
 * be the literal string {@code "none"} when no literature source applies.
 */
public record ParameterProvenance(
        String derivationMethod,
        List<String> sourceEvidenceHashes,
        String developmentDatasetId,
        String algorithmVersion,
        String literatureReference,
        String candidateLineageId,
        ValidationStatus validationStatus) {

    public ParameterProvenance {
        requireNonBlank(derivationMethod, "derivationMethod");
        sourceEvidenceHashes = List.copyOf(
                Objects.requireNonNull(sourceEvidenceHashes, "sourceEvidenceHashes"));
        requireNonBlank(algorithmVersion, "algorithmVersion");
        Objects.requireNonNull(developmentDatasetId, "developmentDatasetId");
        Objects.requireNonNull(literatureReference, "literatureReference");
        requireNonBlank(candidateLineageId, "candidateLineageId");
        Objects.requireNonNull(validationStatus, "validationStatus");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
        return value;
    }
}
