package totah.lab.web.service;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.report.evidence.EvidenceCategory;
import totah.lab.report.evidence.ReportEvidence;
import totah.lab.report.model.PocketReport;
import totah.lab.report.model.PocketReportData;
import totah.lab.report.narrative.PocketNarrative;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PocketReportDocxServiceTest {

    @Test
    void rendersEditableGoogleDocsCompatibleReport() throws Exception {
        var source =
                new PocketReportApplicationService.PocketReportDocument(
                        report(),
                        new PocketNarrative(
                                "Pocket summary.",
                                List.of(),
                                "Docking limitations.",
                                "Descriptive conclusion."
                        )
                );
        PocketReportDocxService service = new PocketReportDocxService();

        byte[] docx = service.render(source, 7);

        assertThat(new String(
                docx,
                0,
                2,
                StandardCharsets.US_ASCII
        )).isEqualTo("PK");
        try (XWPFDocument document = new XWPFDocument(
                new ByteArrayInputStream(docx));
             XWPFWordExtractor extractor =
                     new XWPFWordExtractor(document)) {
            assertThat(extractor.getText())
                    .contains("FPOCKET pocket 2 report")
                    .contains("Internal pocket ID: 1")
                    .contains("Docking run: 7")
                    .contains("Executive summary")
                    .contains("Residue interaction landscape")
                    .contains("[D-001]");
            assertThat(document.getTables()).hasSize(2);
        }
        assertThat(service.filename(source, 7))
                .isEqualTo("pocket-1-run-7-report.docx");
    }

    private PocketReport report() {
        Map<String, Object> dockingResidue =
                new java.util.LinkedHashMap<>();
        dockingResidue.put("chain", "A");
        dockingResidue.put("residueNumber", 103);
        dockingResidue.put("residueName", "PHE");
        dockingResidue.put("contactingLigandFraction", 0.82);
        dockingResidue.put(
                "scoreFilteredContactingLigandFraction",
                0.91
        );
        dockingResidue.put("enrichmentRatio", 1.31);
        dockingResidue.put("closestDistance", 3.6);
        dockingResidue.put("roles", List.of("FREQUENT_CONTACT"));
        return new PocketReport(
                new PocketReportData(
                        1,
                        "FPOCKET pocket 2",
                        PocketSource.FPOCKET,
                        Map.of(
                                "estimatedVolumeAngstrom3", 1578.3,
                                "sourcePocketNumber", 2,
                                "internalPocketId", 1
                        ),
                        Map.of(
                                "totalResidues", 1,
                                "categoryCounts", Map.of(
                                        "HYDROPHOBIC", 1,
                                        "AROMATIC", 1,
                                        "POLAR", 0,
                                        "POSITIVELY_CHARGED", 0,
                                        "NEGATIVELY_CHARGED", 0,
                                        "CYSTEINE", 0
                                )
                        ),
                        Map.of(
                                "runId", 7,
                                "totalLigandCount", 1000,
                                "totalPoseCount", 2000,
                                "scoreFilteredLigandCount", 500,
                                "scoreFilteredPoseCount", 900,
                                "contactScoreThreshold", -5.0,
                                "residues", List.of(dockingResidue)
                        ),
                        Map.of(
                                "meaningfulScoreFilteredContactIncrease",
                                false
                        )
                ),
                List.of(new ReportEvidence(
                        "D-001",
                        EvidenceCategory.DOCKING,
                        "Docking evidence statement.",
                        Map.of()
                ))
        );
    }
}
