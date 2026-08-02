package totah.lab.report.analysis;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Residue;
import totah.lab.report.config.PocketReportConfiguration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPocketDockingAnalyzerTest {

    @Test
    void reportsOnlyResiduesBelongingToThePocket() {
        Residue pocketResidue = PocketAnalyzerTestSupport.residue(
                "PHE", 103, new Point3D(0, 0, 0));
        Pocket pocket = PocketAnalyzerTestSupport.pocket(
                Map.of(),
                pocketResidue
        );
        Map<String, Object> included = residueRow(
                103,
                "PHE",
                824,
                0.824,
                1200,
                0.6
        );
        Map<String, Object> excluded = residueRow(
                200,
                "GLU",
                900,
                0.9,
                1400,
                0.7
        );
        Map<String, Object> input = Map.of(
                "runId", 41L,
                "totalLigandCount", 1000L,
                "totalPoseCount", 2000L,
                "contactScoreThreshold", -5.0,
                "residues", List.of(included, excluded),
                "scoreBands", List.of()
        );

        PocketAnalysisResult result =
                new DefaultPocketDockingAnalyzer().analyze(
                        pocket,
                        input,
                        PocketReportConfiguration.defaults()
                );

        assertThat(result.values())
                .containsEntry("runId", 41L)
                .containsEntry("totalLigandCount", 1000L)
                .containsEntry("totalPoseCount", 2000L)
                .containsEntry(
                        "filterValidationStatus",
                        "ALL_LIGANDS_PASS_THRESHOLD"
                )
                .containsEntry("analyzedPocketResidueCount", 1);
        assertThat(residueRows(result))
                .singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("residueNumber", 103)
                        .containsEntry("residueName", "PHE")
                        .containsEntry("contactingLigandFraction", 0.824)
                        .containsEntry("enrichmentRatio", 1.0)
                        .containsEntry(
                                "roles",
                                List.of("FREQUENT_CONTACT")
                        ));
        assertThat(result.evidence())
                .extracting(evidence -> evidence.id())
                .containsExactly("D-001", "D-R-A-103");
        assertThat(result.evidence().get(1).statement())
                .contains("82.4% of unique ligands")
                .contains("60.0% of poses");
    }

    @Test
    void rejectsAggregateWithoutRequiredDenominators() {
        Pocket pocket = PocketAnalyzerTestSupport.pocket(
                Map.of(),
                PocketAnalyzerTestSupport.residue(
                        "ALA", 1, new Point3D(0, 0, 0))
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new DefaultPocketDockingAnalyzer().analyze(
                        pocket,
                        Map.of(
                                "totalPoseCount", 1,
                                "contactScoreThreshold", -5,
                                "residues", List.of()
                        ),
                        PocketReportConfiguration.defaults()
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalLigandCount");
    }

    private Map<String, Object> residueRow(
            int number,
            String name,
            long ligandCount,
            double ligandFraction,
            long poseCount,
            double poseFraction
    ) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("chain", "A");
        row.put("residueNumber", number);
        row.put("residueName", name);
        row.put("contactingLigandCount", ligandCount);
        row.put("contactingLigandFraction", ligandFraction);
        row.put("contactingPoseCount", poseCount);
        row.put("contactingPoseFraction", poseFraction);
        row.put("scoreFilteredContactingLigandCount", ligandCount);
        row.put("scoreFilteredContactingLigandFraction", ligandFraction);
        row.put("scoreFilteredContactingPoseCount", poseCount);
        row.put("scoreFilteredContactingPoseFraction", poseFraction);
        row.put("enrichmentRatio", 1.31);
        row.put("closestDistance", 3.6);
        return Map.copyOf(row);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> residueRows(
            PocketAnalysisResult result
    ) {
        return (List<Map<String, Object>>) result.values().get("residues");
    }
}
