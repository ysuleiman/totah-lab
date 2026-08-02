package totah.lab.report.render;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.report.evidence.EvidenceCategory;
import totah.lab.report.evidence.ReportEvidence;
import totah.lab.report.model.CompletePocketReport;
import totah.lab.report.model.NarrativeFinding;
import totah.lab.report.model.PocketReport;
import totah.lab.report.model.PocketReportData;
import totah.lab.report.narrative.NarrativeProvenance;
import totah.lab.report.narrative.PocketNarrative;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PocketMarkdownReportRendererTest {

    @Test
    void rendersFindingWithEvidenceCitation() {
        PocketReport report = new PocketReport(
                new PocketReportData(
                        11,
                        "pocket11",
                        PocketSource.FPOCKET,
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of()
                ),
                List.of(new ReportEvidence(
                        "R-014",
                        EvidenceCategory.DOCKING,
                        "PHE103 contacts 82.4 percent of ligands.",
                        Map.of("ligandContactPercent", 82.4)
                ))
        );
        PocketNarrative narrative = new PocketNarrative(
                "A reproducible contact core is present.",
                List.of(new NarrativeFinding(
                        "PHE103 is frequently contacted.",
                        NarrativeFinding.FindingType.OBSERVATION,
                        NarrativeFinding.FindingConfidence.HIGH,
                        List.of("R-014")
                )),
                "Computational evidence only.",
                "Experimental validation is required."
        );
        CompletePocketReport complete = new CompletePocketReport(
                report,
                Optional.of(narrative),
                Optional.of(new NarrativeProvenance(
                        "test",
                        "test-model",
                        Instant.EPOCH,
                        "digest"
                ))
        );

        String markdown = new PocketMarkdownReportRenderer().render(complete);

        assertThat(markdown)
                .contains("# Pocket pocket11")
                .contains("PHE103 is frequently contacted. [R-014]")
                .contains("**[R-014]**");
    }
}
