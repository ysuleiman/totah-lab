package totah.lab.report.analysis;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.report.config.PocketReportConfiguration;
import totah.lab.report.config.PocketReportServiceFactory;
import totah.lab.report.model.PocketReport;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PocketReportServiceIntegrationTest {

    @Test
    void buildsValidatedReportFromGenericInputs() {
        Residue phenylalanine = PocketAnalyzerTestSupport.residue(
                "PHE",
                103,
                new Point3D(0, 0, 0),
                new Point3D(2, 2, 2)
        );
        Structure structure = PocketAnalyzerTestSupport.structure(
                phenylalanine
        );
        Pocket pocket = PocketAnalyzerTestSupport.pocket(
                Map.of("volume", 1578.3),
                phenylalanine
        );
        Map<String, Object> docking = Map.of(
                "runId", 41L,
                "totalLigandCount", 1000L,
                "totalPoseCount", 2000L,
                "contactScoreThreshold", -5.0,
                "residues", List.of(Map.of(
                        "chain", "A",
                        "residueNumber", 103,
                        "residueName", "PHE",
                        "contactingLigandCount", 824,
                        "contactingLigandFraction", 0.824,
                        "contactingPoseCount", 1200,
                        "contactingPoseFraction", 0.6,
                        "scoreFilteredContactingLigandFraction", 0.91,
                        "enrichmentRatio", 1.31
                )),
                "scoreBands", List.of()
        );

        PocketReport report = PocketReportServiceFactory.createDefault()
                .generate(
                        pocket,
                        structure,
                        docking,
                        PocketReportConfiguration.defaults()
                );

        assertThat(report.data().geometry())
                .containsEntry("estimatedVolumeAngstrom3", 1578.3);
        assertThat(report.data().residues())
                .containsEntry("totalResidues", 1);
        assertThat(report.data().docking())
                .containsEntry("totalLigandCount", 1000L);
        assertThat(report.data().hotspots())
                .containsKey("contactFrequencyLeader");
        assertThat(report.evidence())
                .extracting(evidence -> evidence.id())
                .containsExactly(
                        "G-001",
                        "G-002",
                        "G-003",
                        "R-001",
                        "R-002",
                        "D-001",
                        "D-R-A-103",
                        "H-001",
                        "H-003"
                );
    }
}
