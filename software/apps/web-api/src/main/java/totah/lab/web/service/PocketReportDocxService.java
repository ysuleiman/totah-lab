package totah.lab.web.service;

import org.springframework.stereotype.Service;
import totah.lab.report.model.PocketReport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public final class PocketReportDocxService {

    private static final int[] RESIDUE_COLUMNS =
            {1200, 1000, 1000, 1250, 1500, 3410};

    public byte[] render(
            PocketReportApplicationService.PocketReportDocument source,
            long runId
    ) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             DocxReportDocument document =
                     new DocxReportDocument(output)) {
            PocketReport report = source.report();
            document.title(report.data().pocketName() + " report");
            writeIdentity(document, report, runId);
            document.headingOne("Executive summary");
            document.paragraph(source.narrative().executiveSummary());
            writeTopMetrics(document, report);
            writeKeyObservations(document, report);
            writeConclusion(document);

            document.pageBreak();
            writeGeometry(document, report);
            writeComposition(document, report);
            writeDockingScope(document, report);
            writeCysteines(document, report);
            writeLimitations(document, source);

            document.pageBreak();
            writeResidueTable(document, report);
            document.pageBreak();
            writeEvidence(document, report);
            document.finish();
            return output.toByteArray();
        }
    }

    public String filename(
            PocketReportApplicationService.PocketReportDocument document,
            long runId
    ) {
        return "pocket-" + document.report().data().pocketId()
                + "-run-" + runId + "-report.docx";
    }

    private void writeIdentity(
            DocxReportDocument document,
            PocketReport report,
            long runId
    ) {
        Map<String, Object> geometry = report.data().geometry();
        document.metadata(
                report.data().source() + " source pocket",
                wholeNumber(geometry.get("sourcePocketNumber"))
        );
        document.metadata(
                "Internal pocket ID",
                wholeNumber(geometry.getOrDefault(
                        "internalPocketId",
                        report.data().pocketId()
                ))
        );
        document.metadata("Docking run", String.valueOf(runId));
    }

    private void writeTopMetrics(
            DocxReportDocument document,
            PocketReport report
    ) {
        Map<String, Object> geometry = report.data().geometry();
        Map<String, Object> residues = report.data().residues();
        Map<String, Object> docking = report.data().docking();
        document.headingOne("Top metrics");
        document.labeledParagraph(
                "Pocket cavity volume",
                decimal(
                        geometry.get("estimatedVolumeAngstrom3"),
                        " cubic angstroms"
                )
        );
        document.labeledParagraph(
                "Pocket residues",
                value(residues, "totalResidues")
        );
        document.labeledParagraph(
                "Docked ligands",
                value(docking, "totalLigandCount")
        );
        document.labeledParagraph(
                "Docked poses",
                value(docking, "totalPoseCount")
        );
    }

    private void writeKeyObservations(
            DocxReportDocument document,
            PocketReport report
    ) {
        List<Map<String, Object>> residues =
                rows(report.data().docking(), "residues");
        document.headingOne("Key observations");
        document.paragraph(
                roleSummary(residues, "CORE_CONTACT",
                        "Core-contact residues")
                        + " [D-001]"
        );
        document.paragraph(
                roleSummary(residues, "STRONGLY_ENRICHED",
                        "Strongly enriched residues")
                        + " [H-002]"
        );
        Object meaningful = report.data().hotspots().get(
                "meaningfulScoreFilteredContactIncrease"
        );
        if (Boolean.FALSE.equals(meaningful)) {
            document.paragraph(
                    "No residue showed a meaningful increase in contact "
                            + "frequency after score filtering. [H-003]"
            );
            return;
        }
        Map<String, Object> leader = object(
                report.data().hotspots(),
                "scoreFilteredContactIncreaseLeader"
        );
        document.paragraph(
                "Largest meaningful filtered-contact increase: "
                        + residueLabel(leader) + " ("
                        + percent(leader.get("contactFractionIncrease"))
                        + "). [H-003]"
        );
    }

    private void writeConclusion(DocxReportDocument document) {
        document.headingOne("Conclusion");
        document.paragraph(
                "Core-contact residues define commonly sampled pocket walls. "
                        + "Variable or enriched contacts are candidates for "
                        + "follow-up analysis. This report does not assign "
                        + "biological roles or selectivity without comparative "
                        + "or experimental evidence."
        );
    }

    private void writeGeometry(
            DocxReportDocument document,
            PocketReport report
    ) {
        Map<String, Object> geometry = report.data().geometry();
        Map<String, Object> box = object(geometry, "boundingBox");
        Map<String, Object> dimensions = object(box, "sizeAngstrom");
        document.headingOne("Geometry");
        document.labeledParagraph(
                "Pocket cavity volume",
                decimal(
                        geometry.get("estimatedVolumeAngstrom3"),
                        " cubic angstroms"
                )
        );
        document.labeledParagraph(
                "Bounding-box dimensions",
                dimensions(dimensions)
        );
        document.labeledParagraph(
                "Bounding-box volume",
                decimal(
                        geometry.get("boundingBoxVolumeAngstrom3"),
                        " cubic angstroms"
                )
        );
        document.paragraph(
                "The bounding box describes the residue-heavy-atom analysis "
                        + "region; it is not the source-reported cavity volume. "
                        + "Hydrogen atoms are excluded."
        );
        document.labeledParagraph(
                "Mean centroid distance",
                decimal(
                        geometry.get("meanCentroidDistanceAngstrom"),
                        " angstroms"
                )
        );
        document.labeledParagraph(
                "95th-percentile centroid distance",
                decimal(
                        geometry.get("percentile95CentroidDistanceAngstrom"),
                        " angstroms"
                )
        );
        document.labeledParagraph(
                "Maximum pairwise span",
                decimal(
                        geometry.get("maximumPairwiseSpanAngstrom"),
                        " angstroms"
                )
        );
    }

    private void writeComposition(
            DocxReportDocument document,
            PocketReport report
    ) {
        Map<String, Object> counts = object(
                report.data().residues(),
                "categoryCounts"
        );
        document.headingOne("Residue composition");
        document.paragraph(
                "Categories overlap; for example, an aromatic residue may "
                        + "also be classified as hydrophobic."
        );
        document.table(
                new int[]{3200, 3080, 3080},
                List.of("Category", "Count", "Interpretation"),
                List.of(
                        compositionRow(
                                "Hydrophobic",
                                counts,
                                "HYDROPHOBIC"
                        ),
                        compositionRow("Aromatic", counts, "AROMATIC"),
                        compositionRow("Polar", counts, "POLAR"),
                        compositionRow(
                                "Positively charged",
                                counts,
                                "POSITIVELY_CHARGED"
                        ),
                        compositionRow(
                                "Negatively charged",
                                counts,
                                "NEGATIVELY_CHARGED"
                        ),
                        compositionRow("Cysteine", counts, "CYSTEINE")
                )
        );
    }

    private List<String> compositionRow(
            String label,
            Map<String, Object> counts,
            String key
    ) {
        return List.of(label, value(counts, key), "Overlapping category");
    }

    private void writeDockingScope(
            DocxReportDocument document,
            PocketReport report
    ) {
        Map<String, Object> docking = report.data().docking();
        long ligands = longValue(docking, "totalLigandCount");
        long poses = longValue(docking, "totalPoseCount");
        long filteredLigands =
                longValue(docking, "scoreFilteredLigandCount");
        long filteredPoses =
                longValue(docking, "scoreFilteredPoseCount");
        document.headingOne("Docking scope");
        document.labeledParagraph(
                "Docked ligands",
                String.valueOf(ligands)
        );
        document.labeledParagraph(
                poses == ligands && ligands > 0 ? "Pose sampling" : "Poses",
                poses == ligands && ligands > 0
                        ? "1 pose per ligand"
                        : String.valueOf(poses)
        );
        document.labeledParagraph(
                "Filtered ligands",
                filteredLigands + " ("
                        + percentRatio(filteredLigands, ligands) + ")"
        );
        document.labeledParagraph(
                "Filtered poses",
                filteredPoses + " ("
                        + percentRatio(filteredPoses, poses) + ")"
        );
        document.labeledParagraph(
                "Score threshold",
                "< " + decimal(
                        docking.get("contactScoreThreshold"),
                        ""
                )
        );
        document.paragraph(filterValidationStatement(docking));
    }

    private void writeCysteines(
            DocxReportDocument document,
            PocketReport report
    ) {
        List<Map<String, Object>> cysteines = rows(
                report.data().docking(),
                "residues"
        ).stream().filter(row -> "CYS".equals(row.get("residueName")))
                .toList();
        document.headingOne("Pocket cysteines");
        if (cysteines.isEmpty()) {
            document.paragraph(
                    "No cysteine is assigned to the source-defined pocket."
            );
            return;
        }
        document.paragraph(
                "Cysteine identity alone is not evidence of covalent "
                        + "reactivity or ligandability."
        );
        for (Map<String, Object> residue : cysteines) {
            document.headingTwo(residueLabel(residue));
            document.paragraph(
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

    private void writeLimitations(
            DocxReportDocument document,
            PocketReportApplicationService.PocketReportDocument source
    ) {
        document.headingOne("Limitations");
        document.paragraph(source.narrative().limitations());
    }

    private void writeResidueTable(
            DocxReportDocument document,
            PocketReport report
    ) {
        document.headingOne("Residue interaction landscape");
        document.paragraph(
                "Ligand and pose percentages use their respective complete "
                        + "run denominators. Filtered values use the stored "
                        + "score threshold. Enrichment ratio equals the "
                        + "score-filtered contact fraction divided by the "
                        + "overall contact fraction. Enrichment is N/A for "
                        + "zero-contact residues and marked low-confidence "
                        + "when the filtered ligand denominator is small."
        );
        List<List<String>> data = new ArrayList<>();
        for (Map<String, Object> residue :
                rows(report.data().docking(), "residues")) {
            data.add(List.of(
                    residueLabel(residue),
                    percent(residue.get("contactingLigandFraction")),
                    percent(residue.get(
                            "scoreFilteredContactingLigandFraction")),
                    decimal(residue.get("enrichmentRatio"), "x"),
                    decimal(residue.get("closestDistance"), " A"),
                    formattedRoles(residue)
            ));
        }
        document.table(
                RESIDUE_COLUMNS,
                List.of(
                        "Residue",
                        "Ligands",
                        "Filtered",
                        "Ratio",
                        "Min atom distance",
                        "Roles"
                ),
                data
        );
        document.paragraph(
                "Minimum atom distance is the minimum observed receptor-ligand "
                        + "atom distance in the stored contact data. Hydrogen "
                        + "atoms are excluded by the contact-generation "
                        + "pipeline; receptor pocket atoms and docked ligand "
                        + "heavy atoms are considered."
        );
    }

    private void writeEvidence(
            DocxReportDocument document,
            PocketReport report
    ) {
        document.headingOne("Evidence appendix");
        for (var evidence : report.evidence()) {
            document.compactLabeledParagraph(
                    "[" + evidence.id() + "]",
                    evidence.statement()
            );
        }
    }

    private String roleSummary(
            List<Map<String, Object>> residues,
            String role,
            String label
    ) {
        String matching = residues.stream()
                .filter(row -> stringList(row, "roles").contains(role))
                .map(this::residueLabel)
                .collect(Collectors.joining(", "));
        return label + ": " + (matching.isBlank()
                ? "none under the configured thresholds"
                : matching);
    }

    private String formattedRoles(Map<String, Object> residue) {
        return stringList(residue, "roles").stream()
                .map(role -> role.replace('_', ' ')
                        .toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
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
                    "Filtered and overall contact percentages are identical "
                            + "in the current stored aggregate.";
            default ->
                    "Filtered percentages use only ligands and poses passing "
                            + "the stored score threshold.";
        };
    }

    private String residueLabel(Map<String, Object> row) {
        if (row.isEmpty()) {
            return "N/A";
        }
        return row.getOrDefault("chain", "?") + ":"
                + row.getOrDefault("residueName", "?")
                + row.getOrDefault("residueNumber", "?");
    }

    private String dimensions(Map<String, Object> dimensions) {
        if (dimensions.isEmpty()) {
            return "N/A";
        }
        return decimal(dimensions.get("x"), "")
                + " x " + decimal(dimensions.get("y"), "")
                + " x " + decimal(dimensions.get("z"), "")
                + " angstroms";
    }

    private String percent(Object value) {
        return value instanceof Number number
                ? String.format(
                        Locale.ROOT,
                        "%.1f%%",
                        number.doubleValue() * 100
                )
                : "N/A";
    }

    private String decimal(Object value, String suffix) {
        return value instanceof Number number
                ? String.format(
                        Locale.ROOT,
                        "%.3f%s",
                        number.doubleValue(),
                        suffix
                )
                : "N/A";
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

    private List<String> stringList(
            Map<String, Object> values,
            String name
    ) {
        Object value = values.get(name);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }
}
