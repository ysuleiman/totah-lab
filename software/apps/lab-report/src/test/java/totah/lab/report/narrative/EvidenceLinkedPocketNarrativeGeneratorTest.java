package totah.lab.report.narrative;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.report.evidence.EvidenceCategory;
import totah.lab.report.evidence.ReportEvidence;
import totah.lab.report.model.PocketReport;
import totah.lab.report.model.PocketReportData;
import totah.lab.report.validation.NarrativeEvidenceValidator;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceLinkedPocketNarrativeGeneratorTest {

    @Test
    void createsTraceableNarrativeWithoutRepeatingEveryResidue() {
        PocketReport report = new PocketReport(
                new PocketReportData(
                        1, "pocket", PocketSource.FPOCKET,
                        Map.of(), Map.of(), Map.of(), Map.of()
                ),
                List.of(
                        evidence("G-001", EvidenceCategory.GEOMETRY),
                        evidence("R-001",
                                EvidenceCategory.RESIDUE_COMPOSITION),
                        evidence("D-001", EvidenceCategory.DOCKING),
                        evidence("D-R-A-103", EvidenceCategory.DOCKING),
                        evidence("H-001", EvidenceCategory.HOTSPOT)
                )
        );

        PocketNarrative narrative =
                new EvidenceLinkedPocketNarrativeGenerator()
                        .generate(report);

        assertThat(narrative.findings())
                .extracting(finding -> finding.evidenceIds().getFirst())
                .containsExactly("G-001", "R-001", "D-001", "H-001");
        assertThat(narrative.limitations()).contains("do not establish");
        new NarrativeEvidenceValidator().validate(report, narrative);
    }

    private ReportEvidence evidence(
            String id,
            EvidenceCategory category
    ) {
        return new ReportEvidence(
                id,
                category,
                "Evidence " + id + ".",
                Map.of()
        );
    }
}
