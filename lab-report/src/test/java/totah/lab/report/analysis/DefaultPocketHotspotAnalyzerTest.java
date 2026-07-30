package totah.lab.report.analysis;

import org.junit.jupiter.api.Test;
import totah.lab.report.config.PocketReportConfiguration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPocketHotspotAnalyzerTest {

    @Test
    void ranksSignalsAndPublishesConfiguredRoleAssignments() {
        PocketAnalysisResult docking = new PocketAnalysisResult(
                Map.of("residues", List.of(
                        row("PHE", 103, 0.824, 0.86, 1.31),
                        row("THR", 144, 0.31, 0.58, 1.87)
                )),
                List.of()
        );

        PocketAnalysisResult result =
                new DefaultPocketHotspotAnalyzer().analyze(
                        new PocketAnalysisResult(Map.of(), List.of()),
                        docking,
                        PocketReportConfiguration.defaults()
                );

        assertThat(candidate(result, "contactFrequencyLeader"))
                .containsEntry("residueName", "PHE")
                .containsEntry("residueNumber", 103);
        assertThat(candidate(result, "enrichmentLeader"))
                .containsEntry("residueName", "THR")
                .containsEntry("metricValue", 1.87);
        assertThat(candidate(
                result,
                "scoreFilteredContactIncreaseLeader"))
                .containsEntry("residueName", "THR");
        assertThat((double) candidate(
                result,
                "scoreFilteredContactIncreaseLeader"
        ).get("contactFractionIncrease")).isCloseTo(
                0.27,
                org.assertj.core.data.Offset.offset(1.0e-12)
        );
        assertThat(result.values())
                .containsEntry(
                        "roleAssignmentStatus",
                        "CONFIGURED_THRESHOLDS"
                )
                .containsEntry("roleAssignments", List.of());
        assertThat(result.evidence())
                .extracting(evidence -> evidence.id())
                .containsExactly("H-001", "H-002", "H-003");
    }

    @Test
    void handlesRunWithoutOptionalEnrichmentData() {
        PocketAnalysisResult docking = new PocketAnalysisResult(
                Map.of("residues", List.of(Map.of(
                        "chain", "A",
                        "residueNumber", 1,
                        "residueName", "ALA",
                        "contactingLigandFraction", 0.5
                ))),
                List.of()
        );

        PocketAnalysisResult result =
                new DefaultPocketHotspotAnalyzer().analyze(
                        new PocketAnalysisResult(Map.of(), List.of()),
                        docking,
                        PocketReportConfiguration.defaults()
                );

        assertThat(result.values())
                .containsKey("contactFrequencyLeader")
                .doesNotContainKeys(
                        "enrichmentLeader",
                        "scoreFilteredContactIncreaseLeader"
                );
        assertThat(result.evidence())
                .extracting(evidence -> evidence.id())
                .containsExactly("H-001", "H-003");
        assertThat(result.evidence().getLast().statement())
                .contains("No residue showed a meaningful increase");
    }

    private Map<String, Object> row(
            String name,
            int number,
            double contactFraction,
            double filteredContactFraction,
            double enrichment
    ) {
        return Map.of(
                "chain", "A",
                "residueNumber", number,
                "residueName", name,
                "contactingLigandFraction", contactFraction,
                "scoreFilteredContactingLigandFraction",
                filteredContactFraction,
                "enrichmentRatio", enrichment
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> candidate(
            PocketAnalysisResult result,
            String name
    ) {
        return (Map<String, Object>) result.values().get(name);
    }
}
