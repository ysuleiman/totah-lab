package totah.lab.prometheus.comparison;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/** An already-computed candidate metric; Prometheus does not recreate its value. */
public record CandidateMetricObservation(
        ModelReference model,
        MetricDimension dimension,
        String metricId,
        String unit,
        String protocolKey,
        String validationDefinitionChecksum,
        ObservationState state,
        OptionalDouble value,
        List<String> provenanceReferences,
        String reason) {

    public CandidateMetricObservation {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(dimension, "dimension");
        metricId = requireNonBlank(metricId, "metricId");
        unit = requireNonBlank(unit, "unit");
        protocolKey = requireNonBlank(protocolKey, "protocolKey");
        validationDefinitionChecksum = requireNonBlank(
                validationDefinitionChecksum, "validationDefinitionChecksum");
        Objects.requireNonNull(state, "state");
        value = Objects.requireNonNull(value, "value");
        provenanceReferences = List.copyOf(
                Objects.requireNonNull(provenanceReferences, "provenanceReferences"));
        if (provenanceReferences.stream().anyMatch(item -> item == null || item.isBlank())) {
            throw new IllegalArgumentException("provenance references must be non-blank");
        }
        Objects.requireNonNull(reason, "reason");
        if (state == ObservationState.EVALUATED && value.isEmpty()) {
            throw new IllegalArgumentException("evaluated observation requires an existing value");
        }
        if (state == ObservationState.UNEVALUATED && value.isPresent()) {
            throw new IllegalArgumentException("unevaluated observation must not carry a value");
        }
        if (state == ObservationState.UNEVALUATED && reason.isBlank()) {
            throw new IllegalArgumentException("unevaluated observation requires a reason");
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
