package totah.lab.report.analysis;

import org.junit.jupiter.api.Test;
import totah.lab.pocket.Pocket;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.report.config.PocketReportConfiguration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPocketResidueAnalyzerTest {

    @Test
    void inventoriesGenericResidueProperties() {
        Residue phenylalanine = PocketAnalyzerTestSupport.residue(
                "PHE", 10, new Point3D(0, 0, 0));
        Residue threonine = PocketAnalyzerTestSupport.residue(
                "THR", 11, new Point3D(2, 0, 0));
        Residue cysteine = PocketAnalyzerTestSupport.residue(
                "CYS", 12, new Point3D(4, 0, 0));
        Structure structure = PocketAnalyzerTestSupport.structure(
                phenylalanine,
                threonine,
                cysteine
        );
        Pocket pocket = PocketAnalyzerTestSupport.pocket(
                Map.of(),
                phenylalanine,
                threonine,
                cysteine
        );
        PocketAnalysisResult geometry =
                new DefaultPocketGeometryAnalyzer().analyze(
                        pocket,
                        structure,
                        PocketReportConfiguration.defaults()
                );

        PocketAnalysisResult result =
                new DefaultPocketResidueAnalyzer().analyze(
                        pocket,
                        structure,
                        geometry,
                        PocketReportConfiguration.defaults()
                );

        assertThat(result.values()).containsEntry("totalResidues", 3);
        assertThat(categoryCounts(result))
                .containsEntry("HYDROPHOBIC", 1)
                .containsEntry("AROMATIC", 1)
                .containsEntry("POLAR", 2)
                .containsEntry("CYSTEINE", 1);
        assertThat(residueRows(result))
                .extracting(row -> row.get("residueNumber"))
                .containsExactly(10, 11, 12);
        assertThat(result.evidence())
                .extracting(evidence -> evidence.id())
                .containsExactly("R-001", "R-002");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> categoryCounts(
            PocketAnalysisResult result
    ) {
        return (Map<String, Integer>) result.values().get("categoryCounts");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> residueRows(
            PocketAnalysisResult result
    ) {
        return (List<Map<String, Object>>) result.values().get("residues");
    }
}
