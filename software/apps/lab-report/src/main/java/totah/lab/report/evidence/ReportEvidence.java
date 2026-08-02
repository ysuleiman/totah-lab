package totah.lab.report.evidence;

import java.util.Map;
import java.util.Objects;

public record ReportEvidence(
        String id,
        EvidenceCategory category,
        String statement,
        Map<String, Double> metrics
) {
    public ReportEvidence {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(statement, "statement");
        metrics = Map.copyOf(metrics);
    }
}
