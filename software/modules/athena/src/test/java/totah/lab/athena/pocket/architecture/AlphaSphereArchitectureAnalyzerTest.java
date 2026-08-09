package totah.lab.athena.pocket.architecture;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class AlphaSphereArchitectureAnalyzerTest {

    private final AlphaSphereArchitectureAnalyzer analyzer =
            new AlphaSphereArchitectureAnalyzer();

    @Test
    void identicalPocketHasZeroDeltas() {
        Structure receptor = ArchitectureTestFixtures.receptor();
        Pocket pocket = ArchitectureTestFixtures.pocket("1",
                ArchitectureTestFixtures.BASE_SPHERES);

        AlphaSphereArchitectureComparison comparison =
                analyzer.compare(receptor, pocket, receptor, pocket);

        assertThat(comparison.nearestNeighborDistancesAtoB())
                .allSatisfy(distance -> assertThat(distance)
                        .isCloseTo(0.0, offset(1.0e-6)));
        assertThat(comparison.componentsA().componentCount())
                .isEqualTo(1);
        assertThat(comparison.componentsB().componentCount())
                .isEqualTo(1);
        assertThat(comparison.componentsA().componentSizes())
                .containsExactly(8);
        assertThat(comparison.principalAxisAngleDegrees())
                .isCloseTo(0.0, offset(1.0e-3));
        assertThat(comparison.uniqueSpheresA()).isEmpty();
        assertThat(comparison.uniqueSpheresB()).isEmpty();
        assertThat(comparison.sphereVolumeSumDelta())
                .isCloseTo(0.0, offset(1.0e-9));
    }

    @Test
    void rigidlyTransformedPocketHasNearZeroDeltasAfterAlignment() {
        Structure receptorA = ArchitectureTestFixtures.receptor();
        Pocket pocketA = ArchitectureTestFixtures.pocket("1",
                ArchitectureTestFixtures.BASE_SPHERES);
        Structure receptorB = ArchitectureTestFixtures.transformed(
                receptorA,
                ArchitectureTestFixtures.TRANSFORM
        );
        Pocket pocketB = ArchitectureTestFixtures.transformed(
                pocketA,
                ArchitectureTestFixtures.TRANSFORM
        );

        AlphaSphereArchitectureComparison comparison =
                analyzer.compare(receptorA, pocketA, receptorB, pocketB);

        assertThat(comparison.nearestNeighborDistancesAtoB())
                .allSatisfy(distance -> assertThat(distance)
                        .isCloseTo(0.0, offset(1.0e-6)));
        assertThat(comparison.principalAxisAngleDegrees())
                .isCloseTo(0.0, offset(1.0e-3));
        assertThat(comparison.uniqueSpheresA()).isEmpty();
        assertThat(comparison.uniqueSpheresB()).isEmpty();
        assertThat(comparison.sphereVolumeSumDelta())
                .isCloseTo(0.0, offset(1.0e-9));
    }

    @Test
    void extraSphereClusterIsDetectedAsExtraComponent() {
        Structure receptor = ArchitectureTestFixtures.receptor();
        Pocket pocketA = ArchitectureTestFixtures.pocket("1",
                ArchitectureTestFixtures.BASE_SPHERES);

        double[][] extended = new double[11][];
        System.arraycopy(ArchitectureTestFixtures.BASE_SPHERES, 0,
                extended, 0, 8);
        extended[8] = new double[]{30, 0, 0};
        extended[9] = new double[]{33, 0, 0};
        extended[10] = new double[]{31.5, 2, 0};
        Pocket pocketB = ArchitectureTestFixtures.pocket("2", extended);

        AlphaSphereArchitectureComparison comparison =
                analyzer.compare(receptor, pocketA, receptor, pocketB);

        assertThat(comparison.componentsA().componentCount())
                .isEqualTo(1);
        assertThat(comparison.componentsB().componentCount())
                .isEqualTo(2);
        assertThat(comparison.componentsB().componentSizes())
                .containsExactly(8, 3);
        assertThat(comparison.uniqueSpheresA()).isEmpty();
        assertThat(comparison.uniqueSpheresB())
                .containsExactlyInAnyOrder(9L, 10L, 11L);
        assertThat(comparison.sphereVolumeSumDelta())
                .isGreaterThan(0.0);
    }

    @Test
    void unionFindComponentsRespectTheSurfaceGapThreshold() {
        // Two pairs far apart: (0)-(2.5) linked (gap 2.5-3 = -0.5),
        // (10)-(12.5) linked; the pairs are 7.5-3 = 4.5 > 1.0 apart.
        List<AlphaSphere> spheres = List.of(
                new AlphaSphere(1L,
                        new totah.lab.gaia.geometry.Point3D(0, 0, 0),
                        1.5),
                new AlphaSphere(2L,
                        new totah.lab.gaia.geometry.Point3D(2.5, 0, 0),
                        1.5),
                new AlphaSphere(3L,
                        new totah.lab.gaia.geometry.Point3D(10, 0, 0),
                        1.5),
                new AlphaSphere(4L,
                        new totah.lab.gaia.geometry.Point3D(12.5, 0, 0),
                        1.5)
        );

        AlphaSphereArchitectureComparison.SphereComponents components =
                analyzer.components(spheres);

        assertThat(components.componentCount()).isEqualTo(2);
        assertThat(components.componentSizes())
                .containsExactly(2, 2);
        assertThat(components.componentBySphereIndex())
                .containsExactly(0, 0, 1, 1);
    }
}
