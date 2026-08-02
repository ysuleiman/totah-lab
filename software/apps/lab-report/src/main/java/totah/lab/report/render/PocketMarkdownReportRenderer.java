package totah.lab.report.render;

import totah.lab.report.evidence.ReportEvidence;
import totah.lab.report.model.CompletePocketReport;
import totah.lab.report.model.NarrativeFinding;

import java.util.StringJoiner;

public final class PocketMarkdownReportRenderer
        implements PocketReportRenderer<String> {

    @Override
    public String render(CompletePocketReport complete) {
        StringBuilder output = new StringBuilder();
        var report = complete.report();
        output.append("# Pocket ")
                .append(report.data().pocketName())
                .append("\n\n")
                .append("- Source: ")
                .append(report.data().source())
                .append("\n- Pocket ID: ")
                .append(report.data().pocketId())
                .append("\n\n");

        complete.narrative().ifPresent(narrative -> {
            output.append("## Executive summary\n\n")
                    .append(narrative.executiveSummary())
                    .append("\n\n## Findings\n\n");
            for (NarrativeFinding finding : narrative.findings()) {
                output.append("- ")
                        .append(finding.statement())
                        .append(" ")
                        .append(citations(finding))
                        .append("\n");
            }
            output.append("\n## Limitations\n\n")
                    .append(narrative.limitations())
                    .append("\n\n## Conclusions\n\n")
                    .append(narrative.conclusions())
                    .append("\n\n");
        });

        output.append("## Evidence\n\n");
        for (ReportEvidence evidence : report.evidence()) {
            output.append("- **[")
                    .append(evidence.id())
                    .append("]** ")
                    .append(evidence.statement())
                    .append("\n");
        }
        return output.toString();
    }

    private String citations(NarrativeFinding finding) {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        finding.evidenceIds().forEach(joiner::add);
        return joiner.toString();
    }
}
