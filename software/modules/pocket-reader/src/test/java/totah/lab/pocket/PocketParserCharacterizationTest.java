package totah.lab.pocket;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketMetricType;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.ResidueId;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PocketParserCharacterizationTest {

    @Test
    void mapsP2RankPredictionToGaiaPocketWithoutAlphaSpheres()
            throws Exception {

        List<Pocket> pockets = new P2RankJsonParser().parse(
                resource("prankweb-AF-Q9H8H3-F1-model_v6"));

        assertEquals(2, pockets.size());
        Pocket first = pockets.getFirst();
        assertEquals("1", first.id().value());
        assertEquals("pocket1", first.name());
        assertEquals(PocketSource.P2RANK, first.source());
        assertEquals(27.20, first.metric(
                PocketMetricType.P2RANK_PROBABILITY).orElseThrow(), 1.0e-9);
        assertFalse(first.residues().isEmpty());
        assertEquals("A", first.residues().getFirst().chainId());
        assertTrue(first.alphaSphereSet().isEmpty());
    }

    @Test
    void mapsFpocketMetricsResiduesAndAlphaSpheresToGaia()
            throws Exception {

        List<Pocket> pockets = FPocketParser.parse(
                resource("AF-Q6UX53-F1-model_v6_out"));

        assertFalse(pockets.isEmpty());
        Pocket first = pockets.getFirst();
        assertEquals("1", first.id().value());
        assertEquals(PocketSource.FPOCKET, first.source());
        assertEquals(0.027, first.metric(
                PocketMetricType.FPOCKET_SCORE).orElseThrow(), 1.0e-9);
        assertEquals(0.001, first.metric(
                PocketMetricType.FPOCKET_DRUGGABILITY).orElseThrow(), 1.0e-9);
        assertEquals(707.754, first.metric(
                PocketMetricType.VOLUME).orElseThrow(), 1.0e-9);
        assertEquals(78.0, first.metric(
                PocketMetricType.ALPHA_SPHERE_COUNT).orElseThrow(), 1.0e-9);
        assertEquals(78, first.alphaSphereSet().orElseThrow().spheres().size());
        assertFalse(first.residues().isEmpty());
        assertTrue(first.residues().stream()
                .allMatch(residue -> residue instanceof ResidueId));
    }

    private Path resource(String name) throws URISyntaxException {
        return Path.of(getClass().getClassLoader()
                .getResource(name).toURI());
    }
}
