package totah.lab.athena.pocket.architecture;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.RigidTransform;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class WallGeometryAnalyzerTest {

    private final WallGeometryAnalyzer analyzer =
            new WallGeometryAnalyzer();

    @Test
    void identicalReceptorsHaveZeroWallDisplacement() {
        Structure receptor =
                ArchitectureTestFixtures.receptorWithSideChains(0, 0);
        Pocket pocket = ArchitectureTestFixtures.pocket("1",
                ArchitectureTestFixtures.BASE_SPHERES);

        WallGeometryComparison comparison = analyzer.compare(
                receptor, pocket, receptor, pocket,
                RigidTransform.identity());

        assertThat(comparison.maxSideChainDisplacement())
                .isCloseTo(0.0, offset(1.0e-9));
        assertThat(comparison.sideChainDisplacements()).hasSize(6);
        assertThat(comparison.wallDistanceFieldA())
                .isEqualTo(comparison.wallDistanceFieldB());
        assertThat(comparison.meanNormalAngleDegrees())
                .isCloseTo(0.0, offset(1.0e-6));
        assertThat(comparison.meanRoughnessA())
                .isCloseTo(comparison.meanRoughnessB(),
                        offset(1.0e-9));
    }

    @Test
    void shiftedSideChainIsFlaggedWithItsResidue() {
        Structure receptorA = ArchitectureTestFixtures
                .receptorWithSideChainsNearSpheres(0, 0);
        Structure receptorB = ArchitectureTestFixtures
                .receptorWithSideChainsNearSpheres(3, 3.0);
        Pocket pocket = ArchitectureTestFixtures.pocket("1",
                ArchitectureTestFixtures.BASE_SPHERES);

        WallGeometryComparison comparison = analyzer.compare(
                receptorA, pocket, receptorB, pocket,
                RigidTransform.identity());

        assertThat(comparison.maxDisplacementResidueA()
                .residueNumber()).isEqualTo(3);
        assertThat(comparison.maxSideChainDisplacement())
                .isCloseTo(3.0, offset(1.0e-9));

        // The unshifted residues stay at zero.
        assertThat(comparison.sideChainDisplacements().get(1)
                .centroidDisplacement())
                .isCloseTo(0.0, offset(1.0e-9));

        // The wall distance field reacts at the spheres nearest the
        // shifted residue's side chain.
        assertThat(comparison.wallDistanceFieldA())
                .isNotEqualTo(comparison.wallDistanceFieldB());
    }
}
