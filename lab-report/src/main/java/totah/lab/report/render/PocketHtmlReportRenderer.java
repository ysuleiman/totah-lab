package totah.lab.report.render;

import totah.lab.report.model.CompletePocketReport;
import totah.lab.report.model.NarrativeFinding;

import java.util.stream.Collectors;

public final class PocketHtmlReportRenderer
        implements PocketReportRenderer<String> {

    @Override
    public String render(CompletePocketReport complete) {
        var report = complete.report();
        StringBuilder html = new StringBuilder("""
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Pocket report</title>
                </head>
                <body>
                """);
        html.append("<main><h1>Pocket ")
                .append(escape(report.data().pocketName()))
                .append("</h1><p>Source: ")
                .append(escape(report.data().source().name()))
                .append("</p>");

        complete.narrative().ifPresent(narrative -> {
            html.append("<section><h2>Executive summary</h2><p>")
                    .append(escape(narrative.executiveSummary()))
                    .append("</p></section><section><h2>Findings</h2><ul>");
            for (NarrativeFinding finding : narrative.findings()) {
                html.append("<li>")
                        .append(escape(finding.statement()))
                        .append(" <span>")
                        .append(escape(finding.evidenceIds().stream()
                                .collect(Collectors.joining(", ", "[", "]"))))
                        .append("</span></li>");
            }
            html.append("</ul></section><section><h2>Limitations</h2><p>")
                    .append(escape(narrative.limitations()))
                    .append("</p></section><section><h2>Conclusions</h2><p>")
                    .append(escape(narrative.conclusions()))
                    .append("</p></section>");
        });

        html.append("<section><h2>Evidence</h2><ul>");
        report.evidence().forEach(evidence -> html.append("<li><strong>[")
                .append(escape(evidence.id()))
                .append("]</strong> ")
                .append(escape(evidence.statement()))
                .append("</li>"));
        return html.append("</ul></section></main></body></html>")
                .toString();
    }

    private String escape(String input) {
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
