package totah.lab.prometheus.recovery;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable complete audit; every originally unresolved field appears once. */
public record RecoveryAuditReport(
        String sourceGenerationId,
        List<RecoveryAuditEntry> entries) {

    public RecoveryAuditReport {
        Objects.requireNonNull(sourceGenerationId, "sourceGenerationId");
        if (sourceGenerationId.isBlank()) {
            throw new IllegalArgumentException("sourceGenerationId must be non-blank");
        }
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        long distinct = entries.stream()
                .map(entry -> entry.evidenceHash() + "\u0000" + entry.fieldName())
                .distinct().count();
        if (distinct != entries.size()) {
            throw new IllegalArgumentException("duplicate evidence-field recovery entries");
        }
    }

    public Map<RecoveryClassification, Long> countsByClassification() {
        Map<RecoveryClassification, Long> counts = new EnumMap<>(RecoveryClassification.class);
        entries.forEach(entry -> counts.merge(entry.recovery().classification(), 1L, Long::sum));
        return Map.copyOf(counts);
    }

    public long discrepancyCount() {
        return entries.stream().filter(entry -> entry.discrepancy().isPresent()).count();
    }
}
