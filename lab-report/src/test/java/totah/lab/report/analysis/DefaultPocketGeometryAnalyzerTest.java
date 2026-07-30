package totah.lab.report.analysis;

import org.junit.jupiter.api.Test;
import totah.lab.pocket.Pocket;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;
import totah.lab.protein.Structure;
import totah.lab.report.config.PocketReportConfiguration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DefaultPocketGeometryAnalyzerTest {

    @Test
    void calculatesGeometryAndPreservesSourceMetrics() {
        Residue first = PocketAnalyzerTestSupport.residue(
                "ALA",
                1,
                new Point3D(0, 0, 0),
                new Point3D(2, 0, 0)
        );
        Residue second = PocketAnalyzerTestSupport.residue(
                "CYS",
                2,
                new Point3D(0, 2, 0),
                new Point3D(0, 0, 2)
        );
        Structure structure = PocketAnalyzerTestSupport.structure(
                first,
                second
        );
        Pocket pocket = PocketAnalyzerTestSupport.pocket(
                Map.of(
                        "volume", 1578.3,
                        "druggability_score", 0.82
                ),
                first,
                second
        );

        PocketAnalysisResult result =
                new DefaultPocketGeometryAnalyzer().analyze(
                        pocket,
                        structure,
                        PocketReportConfiguration.defaults()
                );

        assertThat(result.values())
                .containsEntry("basis", "RESIDUE_HEAVY_ATOMS")
                .containsEntry("heavyAtomCount", 4)
                .containsEntry("pointCount", 0)
                .containsEntry("boundingBoxVolumeAngstrom3", 8.0)
                .containsEntry("estimatedVolumeAngstrom3", 1578.3)
                .containsEntry("druggabilityScore", 0.82);
        assertThat((double) result.values().get(
                "maximumCentroidDistanceAngstrom"))
                .isCloseTo(Math.sqrt(2.75), within(1.0e-12));
        assertThat((double) result.values().get(
                "meanCentroidDistanceAngstrom"))
                .isCloseTo(
                        (Math.sqrt(0.75) + 3 * Math.sqrt(2.75)) / 4,
                        within(1.0e-12)
                );
        assertThat((double) result.values().get(
                "percentile95CentroidDistanceAngstrom"))
                .isCloseTo(Math.sqrt(2.75), within(1.0e-12));
        assertThat((double) result.values().get(
                "maximumPairwiseSpanAngstrom"))
                .isCloseTo(Math.sqrt(8.0), within(1.0e-12));
        assertThat(result.evidence())
                .extracting(evidence -> evidence.id())
                .containsExactly("G-001", "G-002", "G-003");
        assertThat(result.evidence().getFirst().statement())
                .contains("estimated cavity volume");
    }
}
