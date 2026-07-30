package totah.lab.web.service;

import com.lowagie.text.DocumentException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public final class StructureReportPdfService {

    private static final float[] CONTACT_COLUMNS =
            {130, 76, 86, 90, 82};
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, uuuu HH:mm z");

    public byte[] render(StructureReportService.StructureReport report)
            throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             PdfReportDocument pdf = new PdfReportDocument(output)) {
            writeCover(pdf, report);
            writePocketDefinition(pdf, report);
            writeLigandStory(pdf, report);
            writeEvidenceAppendix(pdf, report);
            pdf.finish();
            return output.toByteArray();
        } catch (DocumentException exception) {
            throw new IOException(
                    "Cannot render structure report PDF",
                    exception
            );
        }
    }

    public String filename(StructureReportService.StructureReport report) {
        String accession = report.uniProtId() == null
                ? "structure-" + report.structureId()
                : report.uniProtId();
        return accession + "-pocket-report.pdf";
    }

    private void writeCover(
            PdfReportDocument pdf,
            StructureReportService.StructureReport report
    ) throws IOException, DocumentException {
        pdf.title(report.title());
        pdf.paragraph(identityLine(report));
        pdf.callout(
                "Purpose",
                "This report describes the selected computational pocket, "
                        + "the residues that define it, and the ligand-contact "
                        + "evidence that supports or extends its boundary."
        );
        if (report.chosenPocket() != null) {
            StructureReportService.PocketSummary pocket =
                    report.chosenPocket();
            pdf.metrics(List.of(
                    metric("Chosen pocket",
                            pocket.source() + " " + pocket.pocketNumber()),
                    metric("Residues", String.valueOf(pocket.residueCount())),
                    metric("Volume", decimal(pocket.volume(), " A3")),
                    metric("Druggability",
                            decimal(pocket.druggabilityScore(), ""))
            ));
        }
        pdf.sectionTitle("Executive interpretation", "What the evidence says");
        pdf.paragraph(report.narrative());
        pdf.paragraph(crossLigandStory(report.ligandEvidence()));
        pdf.callout(
                "Boundary rule",
                "The original fpocket membership is preserved. BioHub "
                        + "contacts outside fpocket are reported as evidence, "
                        + "not silently added to the pocket."
        );
        pdf.paragraph(
                "Generated from current database state on "
                        + DATE_FORMAT.format(
                        report.generatedAt().atZone(
                                ZoneId.systemDefault()
                        )
                ) + "."
        );
    }

    private void writePocketDefinition(
            PdfReportDocument pdf,
            StructureReportService.StructureReport report
    ) throws IOException, DocumentException {
        pdf.newPage();
        pdf.sectionTitle(
                "Pocket definition",
                "The residues that define the chosen pocket"
        );
        if (report.chosenPocket() == null) {
            pdf.paragraph("No chosen pocket is stored for this structure.");
            return;
        }
        StructureReportService.PocketSummary pocket = report.chosenPocket();
        pdf.paragraph(
                pocket.source() + " " + pocket.pocketNumber()
                        + " contains " + pocket.residueCount()
                        + " ordered residues. The list below uses chain, "
                        + "residue number, three-letter name, and one-letter "
                        + "sequence code so every entry can be traced back to "
                        + "the structure."
        );
        pdf.metrics(List.of(
                metric("Score", decimal(pocket.score(), "")),
                metric("Druggability",
                        decimal(pocket.druggabilityScore(), "")),
                metric("Volume", decimal(pocket.volume(), " A3"))
        ));
        List<String> residues = report.chosenPocketResidues().stream()
                .map(residue -> residue.chain()
                        + ":" + residue.residueNumber()
                        + " " + residue.residueName()
                        + " (" + residue.oneLetterCode() + ")")
                .toList();
        pdf.residueSequence(residues);
    }

    private void writeLigandStory(
            PdfReportDocument pdf,
            StructureReportService.StructureReport report
    ) throws IOException, DocumentException {
        for (StructureReportService.LigandEvidence evidence
                : report.ligandEvidence()) {
            pdf.newPage();
            pdf.sectionTitle(
                    "Ligand-conditioned evidence",
                    evidence.ligandCcd() + " and the pocket boundary"
            );
            pdf.paragraph(ligandNarrative(evidence));
            pdf.metrics(List.of(
                    metric("Strong <= "
                                    + oneDecimal(evidence.strongContactCutoff())
                                    + " A",
                            String.valueOf(evidence.strongContactCount())),
                    metric(oneDecimal(evidence.strongContactCutoff())
                                    + "-" + oneDecimal(
                                    evidence.directContactCutoff()
                            ) + " A",
                            String.valueOf(evidence.nearContactCount())),
                    metric("Direct contacts",
                            String.valueOf(evidence.directContactCount())),
                    metric("Outside fpocket",
                            String.valueOf(
                                    evidence.outsideDirectContactCount()
                            ))
            ));
            List<StructureReportService.ContactResidue> outside =
                    evidence.residues().stream()
                            .filter(
                                    StructureReportService.ContactResidue
                                            ::directContact
                            )
                            .filter(residue ->
                                    !residue.chosenPocketMember()
                            )
                            .toList();
            if (outside.isEmpty()) {
                pdf.callout(
                        "Boundary observation",
                        "Every direct contact is already represented by the "
                                + "chosen fpocket."
                );
            } else {
                pdf.callout(
                        "Contacts outside fpocket",
                        outside.stream()
                                .map(this::contactSentence)
                                .collect(Collectors.joining(" "))
                );
            }
            writeContactTable(pdf, evidence, true);
        }
    }

    private void writeEvidenceAppendix(
            PdfReportDocument pdf,
            StructureReportService.StructureReport report
    ) throws IOException, DocumentException {
        pdf.newPage();
        pdf.sectionTitle(
                "Evidence appendix",
                "Complete ligand proximity records"
        );
        pdf.paragraph(
                "Strong contacts are within 4.0 A. Near contacts are greater "
                        + "than 4.0 A and within 4.5 A. Context residues are "
                        + "greater than 4.5 A and within 6.0 A. Distances are "
                        + "minimum heavy-atom distances in the BioHub "
                        + "predicted protein-ligand complex."
        );
        for (int index = 0;
             index < report.ligandEvidence().size();
             index++) {
            StructureReportService.LigandEvidence evidence =
                    report.ligandEvidence().get(index);
            if (index > 0) {
                pdf.newPage();
            }
            pdf.sectionTitle(
                    evidence.ligandCcd(),
                    evidence.ligandCcd() + " complete residue evidence"
            );
            writeContactTable(pdf, evidence, false);
        }
    }

    private void writeContactTable(
            PdfReportDocument pdf,
            StructureReportService.LigandEvidence evidence,
            boolean directOnly
    ) throws IOException, DocumentException {
        List<StructureReportService.ContactResidue> residues =
                directOnly
                        ? evidence.residues().stream()
                                .filter(
                                        StructureReportService.ContactResidue
                                                ::directContact
                                )
                                .toList()
                        : evidence.residues();
        pdf.tableHeader(
                CONTACT_COLUMNS,
                "Residue",
                "Sequence",
                "Distance",
                "Class",
                "fpocket"
        );
        for (StructureReportService.ContactResidue residue : residues) {
            pdf.tableRow(
                    CONTACT_COLUMNS,
                    residue.directContact()
                            && !residue.chosenPocketMember(),
                    residue.chain() + ":" + residue.residueNumber()
                            + " " + residue.residueName(),
                    residue.oneLetterCode() + residue.residueNumber(),
                    String.format(
                            Locale.ROOT,
                            "%.2f A",
                            residue.minimumDistance()
                    ),
                    residue.classification(),
                    residue.chosenPocketMember() ? "Yes" : "No"
            );
        }
        pdf.finishTable();
    }

    private String ligandNarrative(
            StructureReportService.LigandEvidence evidence
    ) {
        return evidence.ligandCcd() + " places "
                + evidence.directContactCount()
                + " residues within the 4.5 A direct-contact boundary. "
                + evidence.strongContactCount()
                + " are strong contacts within 4.0 A, and "
                + evidence.nearContactCount()
                + " are near contacts between 4.0 and 4.5 A. "
                + evidence.directChosenPocketOverlapCount()
                + " direct contacts support the chosen fpocket, while "
                + evidence.outsideDirectContactCount()
                + " direct contacts fall outside its stored membership. "
                + "The interface pTM is "
                + decimal(evidence.interfacePtm(), "") + ".";
    }

    private String crossLigandStory(
            List<StructureReportService.LigandEvidence> evidence
    ) {
        if (evidence.isEmpty()) {
            return "No ligand-conditioned BioHub evidence is available.";
        }
        Map<String, Integer> outsideFrequency = new LinkedHashMap<>();
        Map<String, StructureReportService.ContactResidue> residuesByKey =
                new LinkedHashMap<>();
        for (StructureReportService.LigandEvidence ligand : evidence) {
            Set<String> outsideKeys = ligand.residues().stream()
                    .filter(
                            StructureReportService.ContactResidue
                                    ::directContact
                    )
                    .filter(residue -> !residue.chosenPocketMember())
                    .map(this::residueKey)
                    .collect(Collectors.toSet());
            for (StructureReportService.ContactResidue residue
                    : ligand.residues()) {
                String key = residueKey(residue);
                residuesByKey.putIfAbsent(key, residue);
            }
            for (String key : outsideKeys) {
                outsideFrequency.merge(key, 1, Integer::sum);
            }
        }
        List<String> shared = new ArrayList<>();
        List<String> ligandSpecific = new ArrayList<>();
        for (Map.Entry<String, Integer> entry
                : outsideFrequency.entrySet()) {
            StructureReportService.ContactResidue residue =
                    residuesByKey.get(entry.getKey());
            String label = residue.chain() + ":"
                    + residue.residueNumber() + " "
                    + residue.residueName() + " ("
                    + residue.oneLetterCode() + ")";
            if (entry.getValue() == evidence.size()) {
                shared.add(label);
            } else {
                ligandSpecific.add(label);
            }
        }
        StringBuilder story = new StringBuilder();
        if (!shared.isEmpty()) {
            story.append("Across all ligands, ")
                    .append(String.join(", ", shared))
                    .append(" consistently contact the predicted ligand ")
                    .append("outside the chosen fpocket.");
        }
        if (!ligandSpecific.isEmpty()) {
            if (!story.isEmpty()) {
                story.append(' ');
            }
            story.append("Ligand-specific boundary contacts are ")
                    .append(String.join(", ", ligandSpecific))
                    .append('.');
        }
        return story.isEmpty()
                ? "All direct ligand contacts are contained in the chosen "
                        + "fpocket across the available ligand evidence."
                : story.toString();
    }

    private String contactSentence(
            StructureReportService.ContactResidue residue
    ) {
        return residue.chain() + ":" + residue.residueNumber()
                + " " + residue.residueName()
                + " (" + residue.oneLetterCode() + residue.residueNumber()
                + ") is " + oneDecimal(residue.minimumDistance())
                + " A from the ligand and is classified as "
                + residue.classification().toLowerCase(Locale.ROOT) + ".";
    }

    private String residueKey(
            StructureReportService.ContactResidue residue
    ) {
        return residue.chain() + ":" + residue.residueNumber();
    }

    private String identityLine(
            StructureReportService.StructureReport report
    ) {
        List<String> parts = new ArrayList<>();
        if (report.geneName() != null) {
            parts.add(report.geneName());
        }
        if (report.uniProtId() != null) {
            parts.add("UniProt " + report.uniProtId());
        }
        parts.add("Structure " + report.structureId());
        return String.join("  /  ", parts);
    }

    private PdfReportDocument.Metric metric(String label, String value) {
        return new PdfReportDocument.Metric(label, value);
    }

    private String decimal(Double value, String suffix) {
        return value == null
                ? "-"
                : String.format(Locale.ROOT, "%.3f%s", value, suffix);
    }

    private String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
