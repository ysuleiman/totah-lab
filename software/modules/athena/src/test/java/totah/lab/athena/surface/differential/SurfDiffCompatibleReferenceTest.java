package totah.lab.athena.surface.differential;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdb.reader.PdbReader;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SurfDiffCompatibleReferenceTest {
    private static final Path RESOURCE = Path.of(
            "src/test/resources/surfdiff-compatible");
    private static final double SASA_TOLERANCE = 1.0e-6;
    private static final double DISTANCE_TOLERANCE = 1.0e-6;
    // Original fragmentation stores coordinates/distances as NumPy float32;
    // Athena deliberately retains Gaia double coordinates.
    private static final double SCORE_TOLERANCE = 1.0e-8;

    @Test
    void reproducesOriginalSurfDiffIntermediateAndFinalValues() throws Exception {
        Structure queryStructure = new PdbReader().read(RESOURCE.resolve("query.pdb"));
        Structure subjectStructure = new PdbReader().read(RESOURCE.resolve("subject.pdb"));
        DifferentialSurfaceOptions options = DifferentialSurfaceOptions.SURFDIFF_COMPATIBLE;
        List<SurfaceResidue> query = SurfDiffCompatibleSasa.calculate(queryStructure, options);
        List<SurfaceResidue> subject = SurfDiffCompatibleSasa.calculate(subjectStructure, options);
        ResidueId q1 = id("A", 1); ResidueId q2 = id("A", 2); ResidueId q3 = id("A", 3);
        ResidueId s1 = id("B", 11); ResidueId s2 = id("B", 12); ResidueId s3 = id("B", 13);
        ExplicitResidueCorrespondence correspondence =
                new ExplicitResidueCorrespondence(Map.of(q1, s1, q2, s2, q3, s3));

        assertThat(query.get(0).sasaSquareAngstroms())
                .isCloseTo(131.17189824224255, within(SASA_TOLERANCE));
        assertThat(query.get(1).relativeSasa())
                .isCloseTo(0.5444030823852598, within(SASA_TOLERANCE));
        Map<ResidueId, Map<ResidueId, Double>> neighborhoods =
                LocalResidueNeighborhood.build(query, 7.0);
        assertThat(neighborhoods.get(q1).get(q2))
                .isCloseTo(3.605551242828369, within(DISTANCE_TOLERANCE));
        assertThat(neighborhoods.get(q1)).containsKeys(q1, q2, q3);

        DifferentialSurfaceMap result = new DifferentialSurfaceAnalyzer()
                .analyze(query, subject, correspondence, options);
        assertScore(result, q1, 0.0, 0.1139201220334493);
        assertScore(result, q2, 8.0 / 18.0, 0.3182841215456591);
        assertScore(result, q3, 5.0 / 18.0, 0.3203996876906979);
        assertThat(result.mode()).isEqualTo(DifferentialSurfaceMode.SURFDIFF_COMPATIBLE);
    }

    private static void assertScore(
            DifferentialSurfaceMap map, ResidueId id, double rup, double rus) {
        DifferentialResidueScore score = map.score(id).orElseThrow();
        assertThat(score.rup()).isCloseTo(rup, within(SCORE_TOLERANCE));
        assertThat(score.rus()).isCloseTo(rus, within(SCORE_TOLERANCE));
        assertThat(score.rss()).isCloseTo(1.0 - rus, within(SCORE_TOLERANCE));
    }

    private static ResidueId id(String chain, int number) {
        return new ResidueId(chain, number, null);
    }
}
