package totah.lab.mettl7.triage;

import java.util.Objects;

public record EvidenceObservation(
        String evidenceType,
        String source,
        Confidence confidence,
        EvidenceClass evidenceClass,
        EvidenceTiming timing,
        String provenanceId) {
    public EvidenceObservation {
        requireText(evidenceType, "evidenceType");
        requireText(source, "source");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(evidenceClass, "evidenceClass");
        Objects.requireNonNull(timing, "timing");
        requireText(provenanceId, "provenanceId");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
