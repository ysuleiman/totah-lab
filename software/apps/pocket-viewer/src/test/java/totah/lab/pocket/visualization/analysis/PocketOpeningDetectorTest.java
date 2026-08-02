package totah.lab.pocket.visualization.analysis;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketId;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.Structure;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PocketOpeningDetectorTest {
    @Test
    void identifiesPrimaryAndSeparatedSecondaryOpenings() {
        Pocket pocket = new Pocket(
                new PocketId("1"),
                "Pocket 1",
                PocketSource.FPOCKET,
                new Point3D(0.0, 0.0, 0.0),
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.of(new AlphaSphereSet(List.of(
                        new AlphaSphere(
                                1,
                                new Point3D(0.0, 0.0, 0.0),
                                2.0)))),
                Map.of());

        List<PocketOpening> openings = PocketOpeningDetector.detect(
                pocket, new Structure(List.of()), 3);

        assertThat(openings).hasSize(3);
        assertThat(openings.getFirst().kind())
                .isEqualTo(PocketOpening.Kind.MOUTH);
        assertThat(openings.subList(1, openings.size()))
                .allMatch(opening -> opening.kind()
                        == PocketOpening.Kind.SECONDARY_OPENING);
    }
}
