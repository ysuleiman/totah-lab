package totah.lab.prometheus.recovery;

import java.util.Objects;
import java.util.Optional;

/** One unresolved canonical field and the outcome of authoritative recovery. */
public record RecoveryAuditEntry(
        String evidenceHash,
        String fieldName,
        String historicalValue,
        RecoveredField<String> recovery,
        Optional<String> discrepancy) {

    public RecoveryAuditEntry {
        Objects.requireNonNull(evidenceHash, "evidenceHash");
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(historicalValue, "historicalValue");
        Objects.requireNonNull(recovery, "recovery");
        discrepancy = Objects.requireNonNull(discrepancy, "discrepancy");
        if (!fieldName.equals(recovery.fieldName())) {
            throw new IllegalArgumentException("audit field does not match recovered field");
        }
    }
}
