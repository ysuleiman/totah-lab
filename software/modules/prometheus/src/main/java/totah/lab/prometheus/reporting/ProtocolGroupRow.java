package totah.lab.prometheus.reporting;

import java.util.List;
import java.util.Objects;

/** Explicitly supplied protocol grouping; the renderer performs no grouping inference. */
public record ProtocolGroupRow(
        String groupId,
        String protocolKey,
        List<String> calculationTypes,
        List<String> evidenceHashes,
        String comparabilityNote) {

    public ProtocolGroupRow {
        groupId = requireNonBlank(groupId, "groupId");
        protocolKey = requireNonBlank(protocolKey, "protocolKey");
        calculationTypes = List.copyOf(Objects.requireNonNull(calculationTypes, "calculationTypes"));
        evidenceHashes = List.copyOf(Objects.requireNonNull(evidenceHashes, "evidenceHashes"));
        Objects.requireNonNull(comparabilityNote, "comparabilityNote");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
        return value;
    }
}
