package totah.lab.athena.pocket.architecture;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.pocket.Pocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class PocketArchitectureTest {

    @Test
    void computesHandVerifiableArchitecture() {
        // Cluster along x with a two-sphere mouth at the +x end;
        // all radii are 1.5 in the fixture.
        Pocket pocket = ArchitectureTestFixtures.pocket("1",
                new double[][]{
                        {0, 0, 0},
                        {2, 0, 0},
                        {4, 0, 0},
                        {8, -1, 0},
                        {8, 1, 0}
                });

        PocketArchitecture architecture =
                PocketArchitecture.of(pocket);

        // Centroid of the centers.
        assertThat(architecture.centroid().x())
                .isCloseTo(4.4, offset(1.0e-9));
        assertThat(architecture.alphaSphereCount()).isEqualTo(5);
        assertThat(architecture.meanSphereRadius())
                .isCloseTo(1.5, offset(1.0e-9));

        // Projections on the first (x) axis: -4.4, -2.4, -0.4, 3.6,
        // 3.6 relative to the centroid.
        assertThat(architecture.extentsAlongAxes().get(0))
                .isCloseTo(8.0, offset(1.0e-6));
        assertThat(architecture.extentsAlongAxes().get(1))
                .isCloseTo(2.0, offset(1.0e-6));
        assertThat(architecture.extentsAlongAxes().get(2))
                .isCloseTo(0.0, offset(1.0e-6));

        // Mouth spheres: projections >= 3.6 - 1.5 = 2.1 -> the two
        // x = 8 spheres; mouth plane at 3.6, mouth center (8, 0, 0).
        assertThat(architecture.mouthSphereIds()).hasSize(2);
        assertThat(architecture.mouthCenter().x())
                .isCloseTo(8.0, offset(1.0e-9));
        assertThat(architecture.mouthCenter().y())
                .isCloseTo(0.0, offset(1.0e-9));
        assertThat(architecture.mouthPlaneProjection())
                .isCloseTo(3.6, offset(1.0e-6));
        assertThat(architecture.cavityDepth())
                .isCloseTo(8.0, offset(1.0e-6));
        assertThat(architecture.mouthWidth())
                .isCloseTo(2.0, offset(1.0e-9));
        assertThat(architecture.mouthArea())
                .isCloseTo(Math.PI, offset(1.0e-9));

        // Neck: projections >= 3.6 - 8/3 ~ 0.93 -> the x = 8 spheres.
        assertThat(architecture.bottleneckRadius())
                .isCloseTo(1.5, offset(1.0e-9));

        assertThat(architecture.reportedVolume()).isNull();
        assertThat(architecture.reportedTotalSasa()).isNull();
    }
}
