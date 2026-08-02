package totah.lab.report.analysis;

import totah.lab.report.evidence.ReportEvidence;

import java.util.List;
import java.util.Map;

public record PocketAnalysisResult(
        Map<String, Object> values,
        List<ReportEvidence> evidence
) {
    public PocketAnalysisResult {
        values = Map.copyOf(values);
        evidence = List.copyOf(evidence);
    }

    public static PocketAnalysisResult empty() {
        return new PocketAnalysisResult(Map.of(), List.of());
    }
}
