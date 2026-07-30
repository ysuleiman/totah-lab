package totah.lab.web.service;

import org.junit.jupiter.api.Test;
import totah.lab.pocket.PocketSource;
import totah.lab.report.model.PocketReport;
import totah.lab.report.model.PocketReportData;
import totah.lab.report.narrative.PocketNarrative;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PocketReportPdfServiceTest {

    @Test
    void rendersDownloadablePocketReport() throws Exception {
        var document =
                new PocketReportApplicationService.PocketReportDocument(
                        report(),
                        new PocketNarrative(
                                "Pocket summary.",
                                List.of(),
                                "Docking limitations.",
                                "Descriptive conclusion."
                        )
                );
        PocketReportPdfService service = new PocketReportPdfService();

        byte[] pdf = service.render(document, 7);

        assertThat(new String(
                pdf,
                0,
                5,
                StandardCharsets.US_ASCII
        )).isEqualTo("%PDF-");
        assertThat(service.filename(document, 7))
                .isEqualTo("pocket-1-run-7-report.pdf");
    }

    private PocketReport report() {
        Map<String, Object> dockingResidue = Map.of(
                "chain", "A",
                "residueNumber", 103,
                "residueName", "PHE",
                "contactingLigandFraction", 0.82,
                "scoreFilteredContactingLigandFraction", 0.91,
                "enrichmentRatio", 1.31,
                "closestDistance", 3.6
        );
        return new PocketReport(
                new PocketReportData(
                        1,
                        "FPOCKET pocket 2",
                        PocketSource.FPOCKET,
                        Map.of("estimatedVolumeAngstrom3", 1578.3),
                        Map.of("totalResidues", 1),
                        Map.of(
                                "runId", 7,
                                "totalLigandCount", 1000,
                                "totalPoseCount", 2000,
                                "residues", List.of(dockingResidue)
                        ),
                        Map.of()
                ),
                List.of()
        );
    }
}
