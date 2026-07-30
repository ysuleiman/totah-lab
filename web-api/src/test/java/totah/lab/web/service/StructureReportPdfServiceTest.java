package totah.lab.web.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StructureReportPdfServiceTest {

    @Test
    void rendersNarrativeAndResidueEvidenceAsPdf() throws Exception {
        StructureReportPdfService service =
                new StructureReportPdfService();
        StructureReportService.StructureReport report = report();

        byte[] pdf = service.render(report);

        assertThat(pdf).startsWith("%PDF".getBytes());
        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(4);
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page));
            }
            assertThat(text)
                    .contains("What the evidence says")
                    .contains("A:100 ASN (N100)")
                    .contains("A:125 ALA")
                    .contains("Complete ligand proximity records");
        } finally {
            reader.close();
        }
        assertThat(service.filename(report))
                .isEqualTo("Q6UX53-pocket-report.pdf");
    }

    private StructureReportService.StructureReport report() {
        List<StructureReportService.ReportResidue> pocketResidues =
                List.of(
                        pocketResidue(76, "GLU", "E"),
                        pocketResidue(100, "ASN", "N"),
                        pocketResidue(125, "ALA", "A")
                );
        StructureReportService.LigandEvidence sam = ligand(
                "SAM",
                List.of(
                        contact(76, "GLU", "E", 4.42, "NEAR", false),
                        contact(100, "ASN", "N", 3.89, "STRONG", false),
                        contact(125, "ALA", "A", 4.47, "NEAR", false)
                ),
                1,
                2
        );
        StructureReportService.LigandEvidence sah = ligand(
                "SAH",
                List.of(
                        contact(100, "ASN", "N", 3.92, "STRONG", false),
                        contact(125, "ALA", "A", 4.47, "NEAR", false)
                ),
                1,
                1
        );
        return new StructureReportService.StructureReport(
                2,
                "Thiol S-methyltransferase TMT1B structure report",
                Instant.parse("2026-07-29T17:00:00Z"),
                "Q6UX53",
                "METTL7B",
                "Thiol S-methyltransferase TMT1B",
                new StructureReportService.PocketSummary(
                        1,
                        "FPOCKET",
                        2,
                        0.003,
                        0.832,
                        1690.538,
                        pocketResidues.size()
                ),
                pocketResidues,
                List.of(sam, sah),
                "The chosen site is FPOCKET 2. "
                        + "The original pocket membership is unchanged."
        );
    }

    private StructureReportService.LigandEvidence ligand(
            String ligand,
            List<StructureReportService.ContactResidue> residues,
            int strong,
            int near
    ) {
        return new StructureReportService.LigandEvidence(
                ligand,
                "esmfold2-fast",
                0.94,
                0.98,
                4.0,
                4.5,
                6.0,
                strong,
                near,
                residues.size(),
                residues.size(),
                0,
                residues.size(),
                residues
        );
    }

    private StructureReportService.ContactResidue contact(
            int number,
            String name,
            String code,
            double distance,
            String classification,
            boolean chosen
    ) {
        return new StructureReportService.ContactResidue(
                number,
                "A",
                number,
                name,
                code,
                distance,
                6,
                classification,
                true,
                chosen
        );
    }

    private StructureReportService.ReportResidue pocketResidue(
            int number,
            String name,
            String code
    ) {
        return new StructureReportService.ReportResidue(
                number,
                "A",
                number,
                "",
                name,
                code
        );
    }
}
