package totah.lab.report.model;

import totah.lab.report.evidence.ReportEvidence;

import java.util.List;
import java.util.Objects;

public record PocketReport(
        PocketReportData data,
        List<ReportEvidence> evidence
) {
    public PocketReport {
        Objects.requireNonNull(data, "data");
        evidence = List.copyOf(evidence);
    }
}
