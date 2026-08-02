package totah.lab.gaia.pocket;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.ResidueId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PocketTest {

    @Test
    void storesImmutableResidueIdentitiesAndTypedMetrics() {
        List<ResidueId> residues = new ArrayList<>();
        residues.add(new ResidueId("A", 42, null));
        AlphaSphereSet spheres = new AlphaSphereSet(List.of(
                new AlphaSphere(1, new Point3D(1, 2, 3), 1.5)));

        Pocket pocket = new Pocket(
                PocketId.of(1),
                "Pocket 1",
                PocketSource.FPOCKET,
                new Point3D(1, 2, 3),
                residues,
                List.of(new PocketMetric(
                        PocketMetricType.FPOCKET_SCORE, 0.75)),
                Optional.empty(),
                Optional.of(spheres),
                Map.of());

        residues.clear();

        assertEquals(1, pocket.residues().size());
        assertEquals(0.75, pocket.metric(
                PocketMetricType.FPOCKET_SCORE).orElseThrow());
        assertTrue(pocket.metric(
                PocketMetricType.P2RANK_PROBABILITY).isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> pocket.residues().clear());
    }

    @Test
    void p2RankPocketDoesNotRequireAlphaSpheres() {
        Pocket pocket = new Pocket(
                new PocketId("prediction-1"),
                "prediction-1",
                PocketSource.P2RANK,
                new Point3D(0, 0, 0),
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Map.of());

        assertTrue(pocket.alphaSphereSet().isEmpty());
    }
}
