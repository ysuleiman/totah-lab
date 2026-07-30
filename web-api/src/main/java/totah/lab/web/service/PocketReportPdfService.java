package totah.lab.web.service;

import com.lowagie.text.DocumentException;
import org.springframework.stereotype.Service;
import totah.lab.report.model.PocketReport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public final class PocketReportPdfService {

    private static final float[] RESIDUE_COLUMNS =
            {80, 60, 60, 65, 75, 120};

    public byte[] render(
            PocketReportApplicationService.PocketReportDocument document,
            long runId
    ) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             PdfReportDocument pdf = new PdfReportDocument(output)) {
            PocketReport report = document.report();
            pdf.title(report.data().pocketName() + " report");
            writeIdentity(pdf, report, runId);
            pdf.callout(
                    "Executive summary",
                    document.narrative().executiveSummary()
            );
            writeHeadlineMetrics(pdf, report);
            writeKeyObservations(pdf, report);
            writeConclusion(pdf);
            pdf.newPage();
            writeMetrics(pdf, report);
            pdf.newPage();
            writeCysteines(pdf, report);
            writeLimitations(pdf, document);
            writeResidueTable(pdf, report);
            writeEvidence(pdf, report);
            pdf.finish();
            return output.toByteArray();
        } catch (DocumentException exception) {
            throw new IOException("Cannot render pocket report PDF", exception);
        }
    }

    private void writeHeadlineMetrics(
            PdfReportDocument pdf,
            PocketReport report
    ) throws DocumentException {
        Map<String, Object> geometry = report.data().geometry();
        Map<String, Object> residues = report.data().residues();
        Map<String, Object> docking = report.data().docking();
        pdf.sectionTitle("Top metrics", "Pocket and docking scope");
        pdf.metrics(List.of(
                metric("Pocket cavity volume", decimal(
                        geometry.get("estimatedVolumeAngstrom3"),
                        " cubic angstroms"
                )),
                metric("Pocket residues",
                        value(residues, "totalResidues")),
                metric("Docked ligands",
                        value(docking, "totalLigandCount")),
                metric("Docked poses",
                        value(docking, "totalPoseCount"))
        ));
    }

    public String filename(
            PocketReportApplicationService.PocketReportDocument document,
            long runId
    ) {
        return "pocket-" + document.report().data().pocketId()
                + "-run-" + runId + "-report.pdf";
    }

    private void writeIdentity(
            PdfReportDocument pdf,
            PocketReport report,
            long runId
    ) throws DocumentException {
        Map<String, Object> geometry = report.data().geometry();
        String sourcePocket = wholeNumber(
                geometry.get("sourcePocketNumber")
        );
        pdf.sectionTitle("Pocket overview", "Identity");
        pdf.metrics(List.of(
                metric(
                        report.data().source() + " source pocket",
                        sourcePocket
                ),
                metric(
                        "Internal pocket ID",
                        wholeNumber(geometry.getOrDefault(
                                "internalPocketId",
                                report.data().pocketId()
                        ))
                ),
                metric("Docking run", String.valueOf(runId))
        ));
    }

    private void writeMetrics(
            PdfReportDocument pdf,
            PocketReport report
    ) throws DocumentException {
        Map<String, Object> geometry = report.data().geometry();
        Map<String, Object> residues = report.data().residues();
        Map<String, Object> docking = report.data().docking();
        pdf.sectionTitle("Geometry", "Cavity and analysis-region geometry");
        Map<String, Object> box = object(geometry, "boundingBox");
        Map<String, Object> dimensions = object(box, "sizeAngstrom");
        pdf.metrics(List.of(
                metric("Pocket cavity volume", decimal(
                        geometry.get("estimatedVolumeAngstrom3"),
                        " cubic angstroms"
                )),
                metric("Residues", value(residues, "totalResidues")),
                metric("Bounding-box dimensions", dimensions(dimensions)),
                metric("Bounding-box volume", decimal(
                        geometry.get("boundingBoxVolumeAngstrom3"),
                        " cubic angstroms"
                ))
        ));
        pdf.paragraph(
                "The bounding box describes the residue-heavy-atom analysis "
                        + "region; it is not the source-reported cavity volume. "
                        + "Hydrogen atoms are excluded."
        );
        pdf.metrics(List.of(
                metric("Mean centroid distance", decimal(
                        geometry.get("meanCentroidDistanceAngstrom"),
                        " angstroms"
                )),
                metric("95th-percentile distance", decimal(
                        geometry.get("percentile95CentroidDistanceAngstrom"),
                        " angstroms"
                )),
                metric("Maximum pairwise span", decimal(
                        geometry.get("maximumPairwiseSpanAngstrom"),
                        " angstroms"
                ))
        ));
        writeComposition(pdf, residues);
        writeDockingScope(pdf, docking);
    }

    private void writeComposition(
            PdfReportDocument pdf,
            Map<String, Object> residues
    ) throws DocumentException {
        Map<String, Object> counts = object(residues, "categoryCounts");
        pdf.sectionTitle("Residue composition", "Overlapping categories");
        pdf.paragraph(
                "Categories overlap; for example, an aromatic residue may "
                        + "also be classified as hydrophobic."
        );
        pdf.metrics(List.of(
                metric("Hydrophobic", value(counts, "HYDROPHOBIC")),
                metric("Aromatic", value(counts, "AROMATIC")),
                metric("Polar", value(counts, "POLAR"))
        ));
        pdf.metrics(List.of(
                metric("Positively charged",
                        value(counts, "POSITIVELY_CHARGED")),
                metric("Negatively charged",
                        value(counts, "NEGATIVELY_CHARGED")),
                metric("Cysteine", value(counts, "CYSTEINE"))
        ));
    }

    private void writeDockingScope(
            PdfReportDocument pdf,
            Map<String, Object> docking
    ) throws DocumentException {
        long ligands = longValue(docking, "totalLigandCount");
        long poses = longValue(docking, "totalPoseCount");
        long filteredLigands =
                longValue(docking, "scoreFilteredLigandCount");
        long filteredPoses =
                longValue(docking, "scoreFilteredPoseCount");
        pdf.sectionTitle("Docking scope", "Denominators and score filter");
        List<PdfReportDocument.Metric> scope = new java.util.ArrayList<>();
        scope.add(metric("Docked ligands", String.valueOf(ligands)));
        if (poses == ligands && ligands > 0) {
            scope.add(metric("Pose sampling", "1 pose per ligand"));
        } else {
            scope.add(metric("Poses", String.valueOf(poses)));
        }
        scope.add(metric(
                "Filtered ligands",
                filteredLigands + " ("
                        + percentRatio(filteredLigands, ligands) + ")"
        ));
        scope.add(metric(
                "Filtered poses",
                filteredPoses + " ("
                        + percentRatio(filteredPoses, poses) + ")"
        ));
        scope.add(metric(
                "Score threshold",
                "< " + decimal(
                        docking.get("contactScoreThreshold"),
                        ""
                )
        ));
        pdf.metrics(scope);
        pdf.paragraph(filterValidationStatement(docking));
    }

    private void writeKeyObservations(
            PdfReportDocument pdf,
            PocketReport report
    ) throws DocumentException {
        List<Map<String, Object>> residues =
                rows(report.data().docking(), "residues");
        pdf.sectionTitle("Key observations", "Prioritized docking signals");
        pdf.paragraph(
                roleSummary(residues, "CORE_CONTACT",
                        "Core-contact residues")
                        + " [D-001]"
        );
        pdf.paragraph(
                roleSummary(residues, "STRONGLY_ENRICHED",
                        "Strongly enriched residues")
                        + " [H-002]"
        );
        Object meaningful = report.data().hotspots().get(
                "meaningfulScoreFilteredContactIncrease"
        );
        if (Boolean.FALSE.equals(meaningful)) {
            pdf.paragraph(
                    "No residue showed a meaningful increase in contact "
                            + "frequency after score filtering. [H-003]"
            );
        } else {
            Map<String, Object> leader = object(
                    report.data().hotspots(),
                    "scoreFilteredContactIncreaseLeader"
            );
            pdf.paragraph(
                    "Largest meaningful filtered-contact increase: "
                            + residueLabel(leader) + " ("
                            + percent(leader.get("contactFractionIncrease"))
                            + "). [H-003]"
            );
        }
    }

    private void writeCysteines(
            PdfReportDocument pdf,
            PocketReport report
    ) throws DocumentException {
        List<Map<String, Object>> cysteines = rows(
                report.data().docking(),
                "residues"
        ).stream().filter(row -> "CYS".equals(row.get("residueName")))
                .toList();
        pdf.sectionTitle("Pocket cysteines", "Descriptive contact evidence");
        if (cysteines.isEmpty()) {
            pdf.paragraph(
                    "No cysteine is assigned to the source-defined pocket."
            );
            return;
        }
        pdf.paragraph(
                "Cysteine identity alone is not evidence of covalent "
                        + "reactivity or ligandability."
        );
        for (Map<String, Object> residue : cysteines) {
            pdf.callout(
                    residueLabel(residue),
                    "Ligand contact fraction: "
                            + percent(residue.get(
                                    "contactingLigandFraction"))
                            + ". Minimum observed heavy-atom distance: "
                            + decimal(
                                    residue.get("closestDistance"),
                                    " angstroms"
                            )
                            + ". Classification: "
                            + formattedRoles(residue)
                            + "."
            );
        }
    }

    private void writeConclusion(PdfReportDocument pdf)
            throws DocumentException {
        pdf.callout(
                "Conclusion",
                "Core-contact residues define commonly sampled pocket walls. "
                        + "Variable or enriched contacts are candidates for "
                        + "follow-up analysis. This report does not assign "
                        + "biological roles or selectivity without comparative "
                        + "or experimental evidence."
        );
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
                        + "score threshold. Enrichment ratio equals the "
                        + "score-filtered contact fraction divided by the "
                        + "overall contact fraction. Enrichment is N/A for "
                        + "zero-contact residues and marked low-confidence "
                        + "when the filtered ligand denominator is small."
        );
        pdf.tableHeader(
                RESIDUE_COLUMNS,
                "Residue",
                "Ligands",
                "Filtered",
                "Enrichment",
                "Min atom distance",
                "Roles"
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
                    decimal(residue.get("closestDistance"), " A"),
                    formattedRoles(residue)
            );
        }
        pdf.finishTable();
        pdf.paragraph(
                "Minimum atom distance is the minimum observed receptor-ligand "
                        + "atom distance in the stored contact data. Hydrogen "
                        + "atoms are excluded by the contact-generation "
                        + "pipeline; receptor pocket atoms and docked ligand "
                        + "heavy atoms are considered."
        );
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

    private String formattedRoles(Map<String, Object> residue) {
        return stringList(residue, "roles").stream()
                .map(role -> role.replace('_', ' ')
                        .toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.joining(", "));
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

    private String wholeNumber(Object value) {
        return value instanceof Number number
                ? String.valueOf(number.longValue())
                : "N/A";
    }

    private long longValue(Map<String, Object> values, String name) {
        Object value = values.get(name);
        return value instanceof Number number ? number.longValue() : 0;
    }

    private String percentRatio(long value, long total) {
        return total == 0
                ? "N/A"
                : String.format(
                        Locale.ROOT,
                        "%.1f%%",
                        100.0 * value / total
                );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(
            Map<String, Object> values,
            String name
    ) {
        Object value = values.get(name);
        return value instanceof Map<?, ?>
                ? (Map<String, Object>) value
                : Map.of();
    }

    private String dimensions(Map<String, Object> values) {
        return decimal(values.get("x"), "")
                + " x " + decimal(values.get("y"), "")
                + " x " + decimal(values.get("z"), "")
                + " angstroms";
    }

    private String filterValidationStatement(Map<String, Object> docking) {
        return switch (String.valueOf(docking.get(
                "filterValidationStatus"
        ))) {
            case "ALL_LIGANDS_PASS_THRESHOLD" ->
                    "All docked ligands pass the score threshold; filtered "
                            + "and overall percentages are therefore expected "
                            + "to be identical.";
            case "FILTERED_AND_OVERALL_FRACTIONS_IDENTICAL" ->
                    "Only a subset passes the score threshold, but every "
                            + "reported filtered fraction equals its overall "
                            + "fraction. This should be reviewed as a possible "
                            + "data or aggregation issue.";
            default ->
                    "The score filter retains a subset of the run and uses "
                            + "the filtered ligand and pose denominators shown "
                            + "above.";
        };
    }

    private String roleSummary(
            List<Map<String, Object>> rows,
            String role,
            String label
    ) {
        List<String> members = rows.stream()
                .filter(row -> stringList(row, "roles").contains(role))
                .map(this::residueLabel)
                .toList();
        return label + ": " + (members.isEmpty()
                ? "none under the configured thresholds"
                : String.join(", ", members));
    }

    private String residueLabel(Map<String, Object> residue) {
        return residue.get("chain") + ":" + residue.get("residueName")
                + residue.get("residueNumber");
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(
            Map<String, Object> values,
            String name
    ) {
        Object value = values.get(name);
        return value instanceof List<?>
                ? (List<String>) value
                : List.of();
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
