package totah.lab.web.service;

import com.lowagie.text.DocumentException;
import org.springframework.stereotype.Service;
import totah.lab.report.model.NarrativeFinding;
import totah.lab.report.model.PocketReport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public final class PocketReportPdfService {

    private static final float[] RESIDUE_COLUMNS =
            {110, 80, 80, 80, 80};

    public byte[] render(
            PocketReportApplicationService.PocketReportDocument document,
            long runId
    ) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             PdfReportDocument pdf = new PdfReportDocument(output)) {
            PocketReport report = document.report();
            pdf.title(report.data().pocketName() + " report");
            pdf.paragraph("Pocket " + report.data().pocketId()
                    + " / docking run " + runId
                    + " / source " + report.data().source() + ".");
            pdf.callout(
                    "Executive summary",
                    document.narrative().executiveSummary()
            );
            writeMetrics(pdf, report);
            writeFindings(pdf, document);
            writeResidueTable(pdf, report);
            writeLimitations(pdf, document);
            writeEvidence(pdf, report);
            pdf.finish();
            return output.toByteArray();
        } catch (DocumentException exception) {
            throw new IOException("Cannot render pocket report PDF", exception);
        }
    }

    public String filename(
            PocketReportApplicationService.PocketReportDocument document,
            long runId
    ) {
        return "pocket-" + document.report().data().pocketId()
                + "-run-" + runId + "-report.pdf";
    }

    private void writeMetrics(
            PdfReportDocument pdf,
            PocketReport report
    ) throws DocumentException {
        Map<String, Object> geometry = report.data().geometry();
        Map<String, Object> residues = report.data().residues();
        Map<String, Object> docking = report.data().docking();
        pdf.sectionTitle("Pocket overview", "Measured pocket and run scope");
        pdf.metrics(List.of(
                metric("Residues", value(residues, "totalResidues")),
                metric("Volume", decimal(
                        geometry.get("estimatedVolumeAngstrom3"), " A3")),
                metric("Ligands", value(docking, "totalLigandCount")),
                metric("Poses", value(docking, "totalPoseCount"))
        ));
    }

    private void writeFindings(
            PdfReportDocument pdf,
            PocketReportApplicationService.PocketReportDocument document
    ) throws DocumentException {
        pdf.sectionTitle("Findings", "Evidence-linked observations");
        for (NarrativeFinding finding : document.narrative().findings()) {
            pdf.paragraph(finding.statement() + " ["
                    + String.join(", ", finding.evidenceIds()) + "]");
        }
        pdf.callout("Conclusion", document.narrative().conclusions());
    }

    private void writeResidueTable(
            PdfReportDocument pdf,
            PocketReport report
    ) throws DocumentException {
        pdf.newPage();
        pdf.sectionTitle(
                "Residue interaction landscape",
                "Pocket residues in docking run "
                        + value(report.data().docking(), "runId")
        );
        pdf.paragraph(
                "Ligand and pose percentages use their respective complete "
                        + "run denominators. Filtered values use the stored "
                        + "score threshold."
        );
        pdf.tableHeader(
                RESIDUE_COLUMNS,
                "Residue",
                "Ligands",
                "Filtered",
                "Enrichment",
                "Closest"
        );
        for (Map<String, Object> residue :
                rows(report.data().docking(), "residues")) {
            pdf.tableRow(
                    RESIDUE_COLUMNS,
                    false,
                    residue.get("chain") + ":"
                            + residue.get("residueName")
                            + residue.get("residueNumber"),
                    percent(residue.get("contactingLigandFraction")),
                    percent(residue.get(
                            "scoreFilteredContactingLigandFraction")),
                    decimal(residue.get("enrichmentRatio"), "x"),
                    decimal(residue.get("closestDistance"), " A")
            );
        }
        pdf.finishTable();
    }

    private void writeLimitations(
            PdfReportDocument pdf,
            PocketReportApplicationService.PocketReportDocument document
    ) throws DocumentException {
        pdf.sectionTitle("Limitations", "How to interpret this report");
        pdf.paragraph(document.narrative().limitations());
    }

    private void writeEvidence(
            PdfReportDocument pdf,
            PocketReport report
    ) throws DocumentException {
        pdf.newPage();
        pdf.sectionTitle("Evidence appendix", "Traceable report facts");
        for (var evidence : report.evidence()) {
            pdf.paragraph("[" + evidence.id() + "] "
                    + evidence.statement());
        }
    }

    private PdfReportDocument.Metric metric(String label, String value) {
        return new PdfReportDocument.Metric(label, value);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(
            Map<String, Object> values,
            String name
    ) {
        Object rows = values.get(name);
        return rows instanceof List<?>
                ? (List<Map<String, Object>>) rows
                : List.of();
    }

    private String value(Map<String, Object> values, String name) {
        return String.valueOf(values.getOrDefault(name, "N/A"));
    }

    private String percent(Object value) {
        return value instanceof Number number
                ? String.format(
                        Locale.ROOT,
                        "%.1f%%",
                        number.doubleValue() * 100.0)
                : "N/A";
    }

    private String decimal(Object value, String suffix) {
        return value instanceof Number number
                ? String.format(
                        Locale.ROOT,
                        "%.3f%s",
                        number.doubleValue(),
                        suffix)
                : "N/A";
    }
}
