package totah.lab.prometheus.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class RecoveryAuditReportTest {

    @Test
    void countsEveryDispositionWithoutCombiningThem() {
        FieldSourceProvenance source = new FieldSourceProvenance(
                "raw/input.json", "sha", "$.software.pyscf", "JSON_FIELD");
        RecoveryAuditReport report = new RecoveryAuditReport("generation", List.of(
                new RecoveryAuditEntry("e1", "softwareVersion", "unknown",
                        new RecoveredField<>("softwareVersion", Optional.of("2.14.0"),
                                RecoveryClassification.RECOVERABLE_FROM_RAW_ARTIFACT,
                                List.of(source), "explicit input field"), Optional.empty()),
                new RecoveryAuditEntry("e2", "softwareVersion", "unknown",
                        RecoveredField.unrecoverable("softwareVersion", "no version artifact"),
                        Optional.empty())));

        assertThat(report.countsByClassification())
                .containsEntry(RecoveryClassification.RECOVERABLE_FROM_RAW_ARTIFACT, 1L)
                .containsEntry(RecoveryClassification.GENUINELY_UNRECOVERABLE, 1L);
    }
}
