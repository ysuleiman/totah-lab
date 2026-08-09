package totah.lab.athena.ligand.pose;

import org.junit.jupiter.api.Test;
import totah.lab.athena.ligand.contact.ContactType;
import totah.lab.athena.ligand.contact.LigandContact;
import totah.lab.gaia.geometry.BoundingBox;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static totah.lab.athena.ligand.pose.AlphaSphereMetricsTest.pocket;
import static totah.lab.athena.ligand.pose.AlphaSphereMetricsTest.pocketWithoutSpheres;
import static totah.lab.athena.ligand.pose.AlphaSphereMetricsTest.sphere;
import static totah.lab.athena.ligand.pose.LigandGeometryTest.atom;
import static totah.lab.athena.ligand.pose.LigandGeometryTest.ligand;

class DefaultPosePocketAssignerTest {

    private final DefaultPosePocketAssigner assigner =
            new DefaultPosePocketAssigner();

    private static final Structure EMPTY_RECEPTOR =
            new Structure(List.of(new Chain("A", List.of(
                    new Residue("GLY", 99, List.of(
                            atom(900, "CA", 500, 500, 500)))))));

    @Test
    void scenarioA_ligandInsidePocketAIsAssignedToA() {
        List<Pocket> pockets = defaultPockets();
        Ligand ligand = ligand(
                atom(1, "C1", 0, 0, 0),
                atom(2, "C2", 1, 0, 0),
                atom(3, "C3", 2, 0, 0)
        );

        PosePocketAssignment assignment = assigner.assign(
                EMPTY_RECEPTOR, pockets, ligand, List.of());

        assertThat(assignment.status())
                .isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(assignment.ambiguous()).isFalse();
        assertThat(assignment.pocket().id().value()).isEqualTo("A");
        assertThat(assignment.bestMetrics().basis()).isEqualTo(
                PosePocketMetrics.ContainmentBasis.ALPHA_SPHERES);
        assertThat(assignment.bestMetrics().atomContainmentFraction())
                .isEqualTo(1.0);
        assertThat(assignment.bestMetrics().spheres().basisAvailable())
                .isTrue();
        assertThat(assignment.reason()).isNotBlank();
    }

    @Test
    void scenarioB_ligandInsidePocketBIsAssignedToB() {
        List<Pocket> pockets = defaultPockets();
        Ligand ligand = ligand(
                atom(1, "C1", 19, 0, 0),
                atom(2, "C2", 20, 0, 0),
                atom(3, "C3", 21, 0, 0)
        );

        PosePocketAssignment assignment = assigner.assign(
                EMPTY_RECEPTOR, pockets, ligand, List.of());

        assertThat(assignment.status())
                .isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(assignment.pocket().id().value()).isEqualTo("B");
        assertThat(assignment.bestMetrics().atomContainmentFraction())
                .isEqualTo(1.0);
    }

    @Test
    void scenarioC_overlappingPocketsYieldAmbiguousAssignment() {
        Pocket pocketA = pocket("A", 0, 0,
                List.of(new ResidueId("A", 10, null)),
                Optional.empty(),
                List.of(sphere(0, 0, 2.0)));
        Pocket pocketB = pocket("B", 0, 0,
                List.of(new ResidueId("A", 11, null)),
                Optional.empty(),
                List.of(sphere(0, 0, 2.0)));
        Ligand ligand = ligand(
                atom(1, "C1", 0, 0, 0),
                atom(2, "C2", 1, 0, 0)
        );

        // Reversed input order proves the PocketId tiebreak, not the
        // input order, decides the reported best pocket.
        PosePocketAssignment assignment = assigner.assign(
                EMPTY_RECEPTOR, List.of(pocketB, pocketA),
                ligand, List.of());

        assertThat(assignment.status())
                .isEqualTo(AssignmentStatus.AMBIGUOUS);
        assertThat(assignment.ambiguous()).isTrue();
        assertThat(assignment.pocket().id().value()).isEqualTo("A");
        assertThat(assignment.secondBestPocket().id().value())
                .isEqualTo("B");
        assertThat(assignment.scoreMargin())
                .isLessThan(PosePocketAssignmentOptions.defaults()
                        .ambiguityMargin());
        assertThat(assignment.assignmentScore())
                .isEqualTo(assignment.secondBestScore());
        assertThat(assignment.reason()).isNotBlank();
    }

    @Test
    void scenarioD_ligandOutsideAllPocketsIsNotAssigned() {
        List<Pocket> pockets = defaultPockets();
        Ligand ligand = ligand(
                atom(1, "C1", 100, 0, 0),
                atom(2, "C2", 101, 0, 0)
        );

        PosePocketAssignment assignment = assigner.assign(
                EMPTY_RECEPTOR, pockets, ligand, List.of());

        assertThat(assignment.status())
                .isEqualTo(AssignmentStatus.NOT_ASSIGNED);
        assertThat(assignment.pocket()).isNull();
        assertThat(assignment.assignmentScore()).isNull();
        assertThat(assignment.reason())
                .contains("no pocket shows");
    }

    @Test
    void scenarioE_atomOccupancyBeatsCentroidProximity() {
        List<Pocket> pockets = defaultPockets();
        // Centroid at x = 7.5 is closer to pocket A (d = 7.5) than to
        // pocket B (d = 12.5), but three of four heavy atoms occupy the
        // spheres of pocket B.
        Ligand ligand = ligand(
                atom(1, "C1", 19, 0, 0),
                atom(2, "C2", 20, 0, 0),
                atom(3, "C3", 21, 0, 0),
                atom(4, "C4", -30, 0, 0)
        );

        Point3D centroid = LigandGeometry.shape(ligand).centroid();
        assertThat(centroid.distance(pockets.get(0).center()))
                .isLessThan(centroid.distance(pockets.get(1).center()));

        PosePocketAssignment assignment = assigner.assign(
                EMPTY_RECEPTOR, pockets, ligand, List.of());

        assertThat(assignment.status())
                .isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(assignment.pocket().id().value()).isEqualTo("B");
        assertThat(assignment.bestMetrics().atomContainmentFraction())
                .isCloseTo(0.75, offset(1.0e-9));
    }

    @Test
    void scenarioF_contactCoverageRaisesPocketWithWeakSpheres() {
        List<Pocket> pockets = defaultPockets();
        // Between the pockets: no sphere occupancy anywhere.
        Ligand ligand = ligand(
                atom(1, "C1", 9, 0, 0),
                atom(2, "C2", 10, 0, 0),
                atom(3, "C3", 11, 0, 0)
        );
        // Contacts: one residue of pocket A, two of pocket B.
        List<LigandContact> contacts = List.of(
                contact(ligand, "A", 10),
                contact(ligand, "A", 20),
                contact(ligand, "A", 21)
        );

        PosePocketAssignment assignment = assigner.assign(
                EMPTY_RECEPTOR, pockets, ligand, contacts);

        assertThat(assignment.status())
                .isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(assignment.pocket().id().value()).isEqualTo("B");
        assertThat(assignment.bestMetrics().atomContainmentFraction())
                .isEqualTo(0.0);
        assertThat(assignment.bestMetrics().contactResidueCoverage())
                .isCloseTo(2.0 / 3.0, offset(1.0e-9));
        assertThat(assignment.bestMetrics().pocketContactCoverage())
                .isCloseTo(1.0, offset(1.0e-9));
    }

    @Test
    void emptyCandidateListIsNotAssigned() {
        Ligand ligand = ligand(atom(1, "C1", 0, 0, 0));

        PosePocketAssignment assignment = assigner.assign(
                EMPTY_RECEPTOR, List.of(), ligand, List.of());

        assertThat(assignment.status())
                .isEqualTo(AssignmentStatus.NOT_ASSIGNED);
        assertThat(assignment.pocket()).isNull();
        assertThat(assignment.reason()).contains("no candidate pockets");
    }

    @Test
    void overloadComputesContactsInternally() {
        Structure receptor = new Structure(List.of(new Chain("A",
                List.of(
                        new Residue("ALA", 10, List.of(
                                atom(100, "CA", 1, 1, 0))),
                        new Residue("GLY", 20, List.of(
                                atom(200, "CA", 30, 0, 0)))))));
        Ligand ligand = ligand(
                atom(1, "C1", 0, 0, 0),
                atom(2, "C2", 1, 0, 0)
        );

        PosePocketAssignment assignment = assigner.assign(
                receptor, defaultPockets(), ligand);

        assertThat(assignment.status())
                .isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(assignment.pocket().id().value()).isEqualTo("A");
        assertThat(assignment.bestMetrics().contactResidueCoverage())
                .isEqualTo(1.0);
    }

    @Test
    void fallsBackToPocketBoundsWhenNoSpheres() {
        Pocket near = pocketWithoutSpheres("A", 0,
                List.of(new ResidueId("A", 10, null)),
                Optional.of(new BoundingBox(
                        new Point3D(-2, -2, -2),
                        new Point3D(2, 2, 2))));
        Pocket far = pocketWithoutSpheres("B", 40,
                List.of(new ResidueId("A", 20, null)),
                Optional.of(new BoundingBox(
                        new Point3D(38, -2, -2),
                        new Point3D(42, 2, 2))));
        Ligand ligand = ligand(
                atom(1, "C1", 0, 0, 0),
                atom(2, "C2", 1, 0, 0)
        );

        PosePocketAssignment assignment = assigner.assign(
                EMPTY_RECEPTOR, List.of(near, far), ligand, List.of());

        assertThat(assignment.status())
                .isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(assignment.pocket().id().value()).isEqualTo("A");
        assertThat(assignment.bestMetrics().basis()).isEqualTo(
                PosePocketMetrics.ContainmentBasis.POCKET_BOUNDS);
        assertThat(assignment.bestMetrics().spheres()).isNull();
        assertThat(assignment.bestMetrics().atomContainmentFraction())
                .isEqualTo(1.0);
    }

    @Test
    void fallsBackToResidueAtomsWhenNoSpheresAndNoBounds() {
        Structure receptor = new Structure(List.of(new Chain("A",
                List.of(
                        new Residue("ALA", 10, List.of(
                                atom(100, "CA", 0, 0, 0))),
                        new Residue("GLY", 20, List.of(
                                atom(200, "CA", 40, 0, 0)))))));
        Pocket near = pocketWithoutSpheres("A", 0,
                List.of(new ResidueId("A", 10, null)),
                Optional.empty());
        Pocket far = pocketWithoutSpheres("B", 40,
                List.of(new ResidueId("A", 20, null)),
                Optional.empty());
        Ligand ligand = ligand(
                atom(1, "C1", 1, 0, 0),
                atom(2, "C2", 2, 0, 0)
        );

        PosePocketAssignment assignment = assigner.assign(
                receptor, List.of(near, far), ligand, List.of());

        assertThat(assignment.status())
                .isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(assignment.pocket().id().value()).isEqualTo("A");
        assertThat(assignment.bestMetrics().basis()).isEqualTo(
                PosePocketMetrics.ContainmentBasis.RESIDUE_ATOMS);
        assertThat(assignment.bestMetrics().atomContainmentFraction())
                .isEqualTo(1.0);
    }

    /**
     * Pocket A: center (0,0,0), spheres at x = 0 and 4 (radius 2),
     * residues A10/A11. Pocket B: center (20,0,0), spheres at x = 20
     * and 24 (radius 2), residues A20/A21.
     */
    private static List<Pocket> defaultPockets() {
        return List.of(
                pocket("A", 0, 0,
                        List.of(new ResidueId("A", 10, null),
                                new ResidueId("A", 11, null)),
                        Optional.empty(),
                        List.of(sphere(0, 0, 2.0),
                                sphere(4, 0, 2.0))),
                pocket("B", 20, 0,
                        List.of(new ResidueId("A", 20, null),
                                new ResidueId("A", 21, null)),
                        Optional.empty(),
                        List.of(sphere(20, 0, 2.0),
                                sphere(24, 0, 2.0)))
        );
    }

    private static LigandContact contact(
            Ligand ligand,
            String chainId,
            int residueNumber
    ) {
        Atom ligandAtom = ligand.structure().getChains().get(0)
                .residues().get(0).getAtoms().get(0);
        Atom receptorAtom = atom(500, "CA", 0, 0, 0);

        return new LigandContact(
                ligandAtom,
                receptorAtom,
                new ResidueId(chainId, residueNumber, null),
                3.0,
                ContactType.DIRECT
        );
    }
}
