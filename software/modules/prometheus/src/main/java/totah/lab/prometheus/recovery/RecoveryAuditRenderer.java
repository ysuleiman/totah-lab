package totah.lab.prometheus.recovery;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Deterministic publication-facing renderer for field recovery outcomes. */
public final class RecoveryAuditRenderer {

    public void write(Path outputDirectory, RecoveryAuditReport report) throws IOException {
        Files.createDirectories(outputDirectory);
        Files.writeString(outputDirectory.resolve("AUTHORITATIVE_FIELD_RECOVERY.csv"),
                csv(report), StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("AUTHORITATIVE_FIELD_RECOVERY_SUMMARY.md"),
                markdown(report), StandardCharsets.UTF_8);
    }

    private static String csv(RecoveryAuditReport report) {
        List<String> rows = new ArrayList<>();
        rows.add("evidence_hash,field_name,historical_value,recovered_value,classification,source_path,source_sha256,locator,extraction_method,rationale,discrepancy");
        report.entries().stream()
                .sorted(Comparator.comparing(RecoveryAuditEntry::evidenceHash)
                        .thenComparing(RecoveryAuditEntry::fieldName))
                .forEach(entry -> {
                    if (entry.recovery().provenance().isEmpty()) {
                        rows.add(row(entry, null));
                    } else {
                        entry.recovery().provenance().forEach(source -> rows.add(row(entry, source)));
                    }
                });
        return String.join("\n", rows) + "\n";
    }

    private static String row(RecoveryAuditEntry entry, FieldSourceProvenance source) {
        return csvFields(
                entry.evidenceHash(),
                entry.fieldName(),
                entry.historicalValue(),
                entry.recovery().value().orElse(""),
                entry.recovery().classification().name(),
                source == null ? "" : source.sourcePath(),
                source == null ? "" : source.sha256(),
                source == null ? "" : source.locator(),
                source == null ? "" : source.extractionMethod(),
                entry.recovery().rationale(),
                entry.discrepancy().orElse(""));
    }

    private static String markdown(RecoveryAuditReport report) {
        StringBuilder out = new StringBuilder("# Authoritative field recovery\n\n")
                .append("Source canonical generation: `").append(report.sourceGenerationId())
                .append("`\n\n")
                .append("Fields audited: ").append(report.entries().size()).append("\n\n")
                .append("Historical discrepancies: ").append(report.discrepancyCount()).append("\n\n")
                .append("## Recovery classification\n\n");
        report.countsByClassification().entrySet().stream()
                .sorted(MapEntryComparator.INSTANCE)
                .forEach(entry -> out.append("- ").append(entry.getKey()).append(": ")
                        .append(entry.getValue()).append("\n"));
        return out.toString();
    }

    private static String csvFields(String... values) {
        return java.util.Arrays.stream(values)
                .map(value -> '"' + value.replace("\"", "\"\"") + '"')
                .collect(java.util.stream.Collectors.joining(","));
    }

    private enum MapEntryComparator implements Comparator<Map.Entry<RecoveryClassification, Long>> {
        INSTANCE;

        @Override
        public int compare(
                Map.Entry<RecoveryClassification, Long> left,
                Map.Entry<RecoveryClassification, Long> right) {
            return left.getKey().compareTo(right.getKey());
        }
    }
}
