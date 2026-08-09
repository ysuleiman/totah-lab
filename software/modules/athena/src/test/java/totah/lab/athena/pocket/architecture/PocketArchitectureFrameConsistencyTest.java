package totah.lab.athena.pocket.architecture;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.RigidTransform;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * Regression for the wall-geometry frame bug: the wall comparison
 * must use the receptor-backbone alignment frame, so a mismatch
 * between the pocket-sphere frame and the receptor frame must NOT
 * leak into side-chain displacements — and must be flagged by the
 * report's consistency warning instead.
 */
class PocketArchitectureFrameConsistencyTest {

    @Test
    void wallUsesBackboneFrameAndFlagsInconsistentSpheres() {
        Structure receptorA = ArchitectureTestFixtures.receptor();
        Pocket pocketA = ArchitectureTestFixtures.pocket("1",
                ArchitectureTestFixtures.BASE_SPHERES);
        Ligand poseA = ArchitectureTestFixtures.pose("pose-A",
                new double[][]{
                        {3, 0.5, 0},
                        {3.5, -0.5, 0},
                        {3, 0, 0.5}
                });

        // Receptor B is the receptor under the backbone transform T1;
        // its pocket spheres sit under a DIFFERENT transform T2
        // (simulating spheres and residues from different coordinate
        // artifacts).
        Structure receptorB = ArchitectureTestFixtures.transformed(
                receptorA,
                ArchitectureTestFixtures.TRANSFORM
        );
        Pocket pocketB = ArchitectureTestFixtures.transformed(
                pocketA,
                RigidTransform.translation(0, 0, 25)
        );
        Ligand poseB = ArchitectureTestFixtures.pose("pose-B",
                new double[][]{
                        {3, 0.5, 0},
                        {3.5, -0.5, 0},
                        {3, 0, 0.5}
                });

        PocketArchitectureReport report =
                new PocketArchitectureAnalyzer().analyze(
                        receptorA, pocketA, poseA,
                        receptorB, pocketB, poseB
                );

        // The backbone fit recovers T1 exactly, so the wall
        // comparison — in the backbone frame — shows no displacement,
        // NOT a spurious multi-angstrom artifact of the sphere frame.
        assertThat(report.wall().maxSideChainDisplacement())
                .isCloseTo(0.0, offset(1.0e-6));

        // The B-side sphere/wall inconsistency (spheres 25 A away
        // from the wall atoms) is detected and reported.
        assertThat(report.wall().meanWallDistanceB())
                .isGreaterThan(PocketArchitectureReport
                        .SPHERE_WALL_CONSISTENCY_ANGSTROMS);
        assertThat(report.render())
                .contains("WARNING: pocket B");
        assertThat(report.render())
                .doesNotContain("WARNING: pocket A");
    }

    @Test
    void consistentPairProducesNoWarning() {
        Structure receptorA = ArchitectureTestFixtures.receptor();
        Pocket pocketA = ArchitectureTestFixtures.pocket("1",
                ArchitectureTestFixtures.BASE_SPHERES);
        Ligand pose = ArchitectureTestFixtures.pose("pose-A",
                new double[][]{
                        {3, 0.5, 0},
                        {3.5, -0.5, 0},
                        {3, 0, 0.5}
                });
        Structure receptorB = ArchitectureTestFixtures.transformed(
                receptorA,
                ArchitectureTestFixtures.TRANSFORM
        );
        Pocket pocketB = ArchitectureTestFixtures.transformed(
                pocketA,
                ArchitectureTestFixtures.TRANSFORM
        );

        PocketArchitectureReport report =
                new PocketArchitectureAnalyzer().analyze(
                        receptorA, pocketA, pose,
                        receptorB, pocketB, pose
                );

        assertThat(report.wall().maxSideChainDisplacement())
                .isCloseTo(0.0, offset(1.0e-6));
        assertThat(report.render()).doesNotContain("WARNING");
    }
}
