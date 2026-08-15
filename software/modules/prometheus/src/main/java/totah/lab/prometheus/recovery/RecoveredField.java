package totah.lab.prometheus.recovery;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A recovered or unresolved value with its explicit recovery basis. */
public record RecoveredField<T>(
        String fieldName,
        Optional<T> value,
        RecoveryClassification classification,
        List<FieldSourceProvenance> provenance,
        String rationale) {

    public RecoveredField {
        Objects.requireNonNull(fieldName, "fieldName");
        if (fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName must be non-blank");
        }
        value = Objects.requireNonNull(value, "value");
        Objects.requireNonNull(classification, "classification");
        provenance = List.copyOf(Objects.requireNonNull(provenance, "provenance"));
        Objects.requireNonNull(rationale, "rationale");
        if (rationale.isBlank()) {
            throw new IllegalArgumentException("rationale must be non-blank");
        }
        if (classification == RecoveryClassification.GENUINELY_UNRECOVERABLE) {
            if (value.isPresent() || !provenance.isEmpty()) {
                throw new IllegalArgumentException(
                        "genuinely unrecoverable fields cannot carry a value or source provenance");
            }
        } else if (value.isEmpty() || provenance.isEmpty()) {
            throw new IllegalArgumentException(
                    "recoverable/derivable fields require both a value and source provenance");
        }
    }

    public static <T> RecoveredField<T> unrecoverable(String fieldName, String rationale) {
        return new RecoveredField<>(fieldName, Optional.empty(),
                RecoveryClassification.GENUINELY_UNRECOVERABLE, List.of(), rationale);
    }
}
