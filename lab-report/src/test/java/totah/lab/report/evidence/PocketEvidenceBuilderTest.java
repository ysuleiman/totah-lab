package totah.lab.report.evidence;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PocketEvidenceBuilderTest {

    @Test
    void preservesEvidenceOrderAndMetrics() {
        var evidence = new PocketEvidenceBuilder()
                .add(
                        "P-001",
                        EvidenceCategory.GEOMETRY,
                        "Pocket volume is 1578.3 cubic angstroms.",
                        Map.of("volumeAngstrom3", 1578.3)
                )
                .add(
                        "R-014",
                        EvidenceCategory.DOCKING,
                        "PHE103 contacts 82.4 percent of ligands.",
                        Map.of("ligandContactPercent", 82.4)
                )
                .build();

        assertThat(evidence)
                .extracting(ReportEvidence::id)
                .containsExactly("P-001", "R-014");
        assertThat(evidence.get(1).metrics())
                .containsEntry("ligandContactPercent", 82.4);
    }

    @Test
    void rejectsDuplicateEvidenceIdentifiers() {
        PocketEvidenceBuilder builder = new PocketEvidenceBuilder()
                .add(
                        "P-001",
                        EvidenceCategory.GEOMETRY,
                        "First fact.",
                        Map.of()
                );

        assertThatThrownBy(() -> builder.add(
                "P-001",
                EvidenceCategory.GEOMETRY,
                "Second fact.",
                Map.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }
}
