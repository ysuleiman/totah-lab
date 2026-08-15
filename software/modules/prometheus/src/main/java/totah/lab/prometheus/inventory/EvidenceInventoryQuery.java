package totah.lab.prometheus.inventory;

import java.util.Objects;
import java.util.Optional;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;

/** Exact-match inventory filters. Empty fields mean that dimension is unrestricted. */
public record EvidenceInventoryQuery(
        Optional<String> moleculeId,
        Optional<String> protocolKey,
        Optional<CalculationType> calculationType,
        Optional<EvidenceAcceptanceState> acceptance) {

    public EvidenceInventoryQuery {
        moleculeId = normalized(moleculeId, "moleculeId");
        protocolKey = normalized(protocolKey, "protocolKey");
        calculationType = Objects.requireNonNull(calculationType, "calculationType");
        acceptance = Objects.requireNonNull(acceptance, "acceptance");
    }

    public static EvidenceInventoryQuery all() {
        return new EvidenceInventoryQuery(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Optional<String> normalized(Optional<String> value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        value.ifPresent(item -> {
            if (item.isBlank()) {
                throw new IllegalArgumentException(fieldName + " must be non-blank when present");
            }
        });
        return value;
    }
}
