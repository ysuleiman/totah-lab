package totah.lab.web.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DockingReportAggregateMapperTest {

    @Test
    void mapsDatabaseAnalysisToReportContractWithoutNullMetrics() {
        DockingAnalysisService.DockingRunSummary run =
                new DockingAnalysisService.DockingRunSummary(
                        7,
                        3,
                        2,
                        LocalDateTime.of(2026, 7, 29, 12, 0),
                        100,
                        200
                );
        DockingAnalysisService.ResidueAnalysis residue =
                residueAnalysis();
        DockingAnalysisService.ResidueScoreBand band =
                scoreBand();

        Map<String, Object> aggregate =
                new DockingReportAggregateMapper().map(
                        run,
                        List.of(residue),
                        List.of(band)
                );

        assertThat(aggregate)
                .containsEntry("runId", 7L)
                .containsEntry("totalLigandCount", 100L)
                .containsEntry("totalPoseCount", 200L)
                .containsEntry("contactScoreThreshold", -5.0);
        assertThat(rows(aggregate, "residues"))
                .singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("residueName", "PHE")
                        .containsEntry("contactingLigandFraction", 0.82)
                        .doesNotContainKeys(
                                "avgContactingScore",
                                "closestDistance"
                        ));
        assertThat(rows(aggregate, "scoreBands"))
                .singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("scoreLower", -8.0)
                        .containsEntry("scoreUpper", -6.0));
    }

    private DockingAnalysisService.ResidueAnalysis residueAnalysis() {
        return new DockingAnalysisService.ResidueAnalysis(
                7, 3, 2, 10, "A", 103, "PHE",
                -5.0,
                80, 60, 0.75,
                160, 100, 0.625,
                100, 82, 0.82,
                200, 120, 0.6,
                20, 18, 0.9,
                10, 3, 0.3,
                0.6,
                3.0,
                1.58,
                null, null, -10.0, -5.5,
                null, null, null
        );
    }

    private DockingAnalysisService.ResidueScoreBand scoreBand() {
        return new DockingAnalysisService.ResidueScoreBand(
                7, 3, 2, -8.0, -6.0,
                10, "A", 103, "PHE",
                50, 40, 0.8,
                100, 60, 0.6,
                null, null, -9.0, -6.1,
                null, null, null
        );
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(
            Map<String, Object> aggregate,
            String name
    ) {
        return (List<Map<String, Object>>) aggregate.get(name);
    }
}
