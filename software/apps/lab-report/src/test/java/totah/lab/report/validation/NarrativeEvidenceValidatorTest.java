package totah.lab.report.validation;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.report.evidence.EvidenceCategory;
import totah.lab.report.evidence.ReportEvidence;
import totah.lab.report.model.NarrativeFinding;
import totah.lab.report.model.PocketReport;
import totah.lab.report.model.PocketReportData;
import totah.lab.report.narrative.PocketNarrative;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NarrativeEvidenceValidatorTest {

    private final NarrativeEvidenceValidator validator =
            new NarrativeEvidenceValidator();

    @Test
    void acceptsFindingLinkedToKnownEvidence() {
        PocketReport report = report();
        PocketNarrative narrative = narrative(List.of("R-014"));

        assertThatCode(() -> validator.validate(report, narrative))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsFindingLinkedToUnknownEvidence() {
        PocketReport report = report();
        PocketNarrative narrative = narrative(List.of("R-999"));

        assertThatThrownBy(() -> validator.validate(report, narrative))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown evidence");
    }

    private PocketReport report() {
        return new PocketReport(
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
    }

    private PocketNarrative narrative(List<String> evidenceIds) {
        return new PocketNarrative(
                "Pocket occupancy is concentrated.",
                List.of(new NarrativeFinding(
                        "PHE103 is frequently contacted.",
                        NarrativeFinding.FindingType.OBSERVATION,
                        NarrativeFinding.FindingConfidence.HIGH,
                        evidenceIds
                )),
                "Computational evidence only.",
                "Further validation is required."
        );
    }
}
