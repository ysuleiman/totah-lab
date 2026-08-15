package totah.lab.prometheus.comparison;

import java.util.List;
import java.util.Objects;

/** Immutable reference to a frozen candidate and its provenance. */
public record ModelReference(
        String candidateId,
        String candidateChecksum,
        List<String> provenanceReferences) {

    public ModelReference {
        candidateId = requireNonBlank(candidateId, "candidateId");
        candidateChecksum = requireNonBlank(candidateChecksum, "candidateChecksum");
        provenanceReferences = List.copyOf(
                Objects.requireNonNull(provenanceReferences, "provenanceReferences"));
        if (provenanceReferences.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("provenance references must be non-blank");
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
        return value;
    }
}
