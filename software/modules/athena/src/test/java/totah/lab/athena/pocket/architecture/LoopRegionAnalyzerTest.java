package totah.lab.athena.pocket.architecture;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketId;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class LoopRegionAnalyzerTest {

    private static final int FIRST_RESIDUE = 225;

    private static final LoopRegionOptions LOOP_OPTIONS =
            new LoopRegionOptions(
                    227, 229,
                    4.5, 7.0, 6.0, 1.4, 1.0
            );

    private final LoopRegionAnalyzer analyzer =
            new LoopRegionAnalyzer(LOOP_OPTIONS);

    @Test
    void identicalReceptorsAndPosesGiveZeroAndOrthogonalVerdict() {
        Structure receptor = loopReceptor(0, 0);
        Pocket pocket = loopPocket(List.of(227), true);
        Ligand pose = poseAt(0, 0, 0);

        LoopRegionAnalysis analysis = analyzer.analyze(
                receptor, receptor,
                RigidTransform.identity(),
                pose, pose,
                pocket, pocket
        );

        assertThat(analysis.rows()).hasSize(3);
        for (LoopRegionAnalysis.LoopRegionResidueRow row
                : analysis.rows()) {
            assertThat(row.caDisplacement())
                    .isCloseTo(0.0, offset(1.0e-9));
            assertThat(row.sideChainCentroidDisplacement())
                    .isCloseTo(0.0, offset(1.0e-9));
            assertThat(row.sideChainRmsd())
                    .isCloseTo(0.0, offset(1.0e-9));
        }
        assertThat(analysis.poseDisplacementTowardLoopAngstroms())
                .isCloseTo(0.0, offset(1.0e-9));
        assertThat(analysis.verdict()).isEqualTo(
                LoopShiftVerdict.ORTHOGONAL_OR_NEGLIGIBLE);
    }

    @Test
    void poseShiftedTowardLoopIsDetected() {
        Structure receptor = loopReceptor(0, 0);
        Pocket pocket = loopPocket(List.of(227), true);
        // Loop CA centroid of residues 227-229 is (12, 1/3, 2/3);
        // pose B sits 3 A further along +x than pose A (centroid at
        // the origin): almost exactly toward the loop.
        Ligand poseA = poseAt(0, 0, 0);
        Ligand poseB = poseAt(3, 0, 0);

        LoopRegionAnalysis analysis = analyzer.analyze(
                receptor, receptor,
                RigidTransform.identity(),
                poseA, poseB,
                pocket, pocket
        );

        assertThat(analysis.verdict())
                .isEqualTo(LoopShiftVerdict.POSE_SHIFTED_TOWARD_LOOP);
        assertThat(analysis.poseDisplacementTowardLoopAngstroms())
                .isCloseTo(2.99, offset(0.01));
        assertThat(analysis.poseACentroidToLoopAngstroms())
                .isGreaterThan(11.0);
        assertThat(analysis.poseBCentroidToLoopAngstroms())
                .isLessThan(analysis.poseACentroidToLoopAngstroms());
        assertThat(analysis.reason()).contains("toward");
    }

    @Test
    void poseShiftedAwayFromLoopIsDetected() {
        Structure receptor = loopReceptor(0, 0);
        Pocket pocket = loopPocket(List.of(227), true);
        Ligand poseA = poseAt(0, 0, 0);
        Ligand poseB = poseAt(-3, 0, 0);

        LoopRegionAnalysis analysis = analyzer.analyze(
                receptor, receptor,
                RigidTransform.identity(),
                poseA, poseB,
                pocket, pocket
        );

        assertThat(analysis.verdict())
                .isEqualTo(LoopShiftVerdict.POSE_SHIFTED_AWAY_FROM_LOOP);
        assertThat(analysis.poseDisplacementTowardLoopAngstroms())
                .isCloseTo(-2.99, offset(0.01));
    }

    @Test
    void orthogonalDisplacementIsNegligibleVerdict() {
        Structure receptor = loopReceptor(0, 0);
        Pocket pocket = loopPocket(List.of(227), true);
        Ligand poseA = poseAt(0, 0, 0);
        // Mostly orthogonal to the pose-to-loop direction.
        Ligand poseB = poseAt(0, 3, 0);

        LoopRegionAnalysis analysis = analyzer.analyze(
                receptor, receptor,
                RigidTransform.identity(),
                poseA, poseB,
                pocket, pocket
        );

        assertThat(analysis.verdict()).isEqualTo(
                LoopShiftVerdict.ORTHOGONAL_OR_NEGLIGIBLE);
        assertThat(Math.abs(
                analysis.poseDisplacementTowardLoopAngstroms()))
                .isLessThan(LOOP_OPTIONS
                        .towardLoopSignificanceAngstroms());
    }

    @Test
    void contactsAndWallMembershipAreReportedPerResidue() {
        Structure receptor = loopReceptor(0, 0);
        // Pocket A contains residue 227, pocket B does not.
        Pocket pocketA = loopPocket(List.of(227), true);
        Pocket pocketB = loopPocket(List.of(228), true);

        // Pose atoms ~2.9 A from residue 227's CB (8, 0, 1.5), far
        // from the other in-range side chains.
        Ligand pose = ArchitectureTestFixtures.pose("pose",
                new double[][]{
                        {7, -1, 4.0},
                        {7.4, -1, 4.4},
                        {6.6, -1, 4.4}
                });

        LoopRegionAnalysis analysis = analyzer.analyze(
                receptor, receptor,
                RigidTransform.identity(),
                pose, pose,
                pocketA, pocketB
        );

        LoopRegionAnalysis.LoopRegionResidueRow row227 =
                analysis.rows().get(0);
        assertThat(row227.residueA().residueNumber()).isEqualTo(227);
        assertThat(row227.minDistanceToPoseA())
                .isCloseTo(Math.sqrt(8.25), offset(1.0e-6));
        assertThat(row227.contactA()).isTrue();
        assertThat(row227.pocketWallA()).isTrue();
        assertThat(row227.pocketWallB()).isFalse();

        // Residues 228/229 are far from the pose and not in pocket A.
        LoopRegionAnalysis.LoopRegionResidueRow row228 =
                analysis.rows().get(1);
        assertThat(row228.contactA()).isFalse();
        assertThat(row228.pocketWallA()).isFalse();
        assertThat(row228.pocketWallB()).isTrue();
    }

    @Test
    void burialAndCavityMetricsAreDeterministic() {
        Structure receptor = loopReceptor(0, 0);
        Pocket pocket = loopPocket(List.of(227), true);
        Ligand pose = poseAt(0, 0, 0);

        LoopRegionAnalysis analysis = analyzer.analyze(
                receptor, receptor,
                RigidTransform.identity(),
                pose, pose,
                pocket, pocket
        );

        LoopRegionAnalysis.LoopRegionResidueRow row227 =
                analysis.rows().get(0);
        // CA+CB of residues 226 and 228 lie within 8 A of residue
        // 227's CB; its own atoms are excluded.
        assertThat(row227.burialA()).isEqualTo(4);
        assertThat(row227.burialB()).isEqualTo(4);
        // Identical receptors: no cavity displacement, no free-volume
        // difference.
        assertThat(row227.localCavityDisplacement())
                .isCloseTo(0.0, offset(1.0e-6));
        assertThat(row227.localFreeVolumeDifference())
                .isCloseTo(0.0, offset(1.0e-9));
    }

    @Test
    void pocketWithoutNearbySpheresHasNoCavityDisplacement() {
        Structure receptor = loopReceptor(0, 0);
        Pocket pocket = loopPocket(List.of(227), false);
        Ligand pose = poseAt(0, 0, 0);

        LoopRegionAnalysis analysis = analyzer.analyze(
                receptor, receptor,
                RigidTransform.identity(),
                pose, pose,
                pocket, pocket
        );

        assertThat(analysis.rows().get(0).localCavityDisplacement())
                .isNull();
    }

    /**
     * Receptor with residues 225-236, CA on an irregular x-walk and
     * CB offset +1.5 in z; residue {@code shiftedResidue}'s CB is
     * shifted in x by {@code cbShiftX}.
     */
    private static Structure loopReceptor(
            int shiftedResidue,
            double cbShiftX
    ) {
        List<Residue> residues = new ArrayList<>();

        for (int index = 0; index < 12; index++) {
            int number = FIRST_RESIDUE + index;
            double x = index * 4.0;
            double y = index % 2;
            double z = index % 2 == 0 ? 0 : 1;

            double cbX = x + (number == shiftedResidue ? cbShiftX : 0);

            residues.add(new Residue(
                    "ALA",
                    number,
                    List.of(
                            ArchitectureTestFixtures.atom(
                                    1000 + index, "CA",
                                    new Point3D(x, y, z)),
                            ArchitectureTestFixtures.atom(
                                    2000 + index, "CB",
                                    new Point3D(cbX, y, z + 1.5))
                    )
            ));
        }

        return new Structure(List.of(new Chain("A", residues)));
    }

    /**
     * Pocket over the given residue numbers; with {@code withSpheres}
     * a small sphere cluster sits near residue 227's side chain,
     * otherwise the spheres sit 50 A away (outside the locality
     * cutoff).
     */
    private static Pocket loopPocket(
            List<Integer> residueNumbers,
            boolean withSpheres
    ) {
        List<ResidueId> residues = residueNumbers.stream()
                .map(number -> new ResidueId("A", number, null))
                .toList();

        double[][] spherePositions = withSpheres
                ? new double[][]{{8, 1, 3}, {9, 0, 4}, {7, 2, 4}}
                : new double[][]{{58, 1, 3}, {59, 0, 4}, {57, 2, 4}};

        List<AlphaSphere> spheres = new ArrayList<>();
        for (int index = 0; index < spherePositions.length; index++) {
            spheres.add(new AlphaSphere(
                    index + 1L,
                    ArchitectureTestFixtures.point(
                            spherePositions[index]),
                    1.5
            ));
        }

        return new Pocket(
                new PocketId("loop"),
                "pocket-loop",
                PocketSource.FPOCKET,
                spheres.get(0).center(),
                residues,
                List.of(),
                Optional.empty(),
                Optional.of(new AlphaSphereSet(spheres)),
                Map.of()
        );
    }

    /** Three-atom pose whose centroid is (x, y, z + 1/6). */
    private static Ligand poseAt(double x, double y, double z) {
        return ArchitectureTestFixtures.pose("pose",
                new double[][]{
                        {x + 0.5, y, z},
                        {x - 0.5, y, z},
                        {x, y, z + 0.5}
                });
    }
}
