package totah.lab.prometheus.recovery;

import java.util.EnumMap;
import java.util.List;
import java.util.LinkedHashSet;
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
        var keys = new LinkedHashSet<String>();
        var duplicates = new LinkedHashSet<String>();
        entries.stream().map(entry -> entry.evidenceHash() + "\u0000" + entry.fieldName())
                .forEach(key -> { if (!keys.add(key)) duplicates.add(key.replace('\u0000', ':')); });
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("duplicate evidence-field recovery entries: " + duplicates);
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
