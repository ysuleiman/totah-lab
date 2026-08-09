package totah.lab.athena.ligand.pose;

import org.junit.jupiter.api.Test;
import totah.lab.athena.ligand.contact.ContactType;
import totah.lab.athena.ligand.contact.LigandContact;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketId;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.Atom;
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

class DefaultCrossProteinPoseComparatorTest {

    private final DefaultCrossProteinPoseComparator comparator =
            new DefaultCrossProteinPoseComparator();

    // Query pocket residues: irregular, non-collinear CA layout.
    private static final String[] RESIDUE_NAMES = {
            "ALA", "LEU", "SER", "LYS", "VAL", "GLY"
    };
    private static final double[][] RESIDUE_POSITIONS = {
            {0, 0, 0},
            {9, 1, 2},
            {2, 8, 1},
            {5, 3, 9},
            {11, 7, 4},
            {3, 12, 6}
    };

    // Compact irregular alpha-sphere cluster.
    private static final double[][] SPHERE_POSITIONS = {
            {1, 1, 1},
            {4, 2, 0},
            {2, 5, 3},
            {7, 4, 2},
            {3, 3, 7},
            {9, 6, 5},
            {5, 8, 4},
            {0, 4, 6}
    };

    // DCMB-like pose: four heavy atoms inside the sphere cluster.
    private static final double[][] POSE_POSITIONS = {
            {3, 3, 2},
            {4, 4, 3},
            {5, 3, 4},
            {4, 2, 3}
    };

    // 90 degrees about z, then translation.
    private static final RigidTransform ROTATE_Z_90 = new RigidTransform(
            0, -1, 0,
            1, 0, 0,
            0, 0, 1,
            new Point3D(10, -4, 6)
    );

    // 120 degrees about the (1,1,1) axis (cyclic permutation), then
    // translation.
    private static final RigidTransform PERMUTE_AXES = new RigidTransform(
            0, 0, 1,
            1, 0, 0,
            0, 1, 0,
            new Point3D(-7, 11, 3)
    );

    @Test
    void scenarioG_transformedPoseInCorrespondingPocketIsSameSite() {
        Fixture query = fixture(RigidTransform.identity(), "3", 1,
                RESIDUE_NAMES, RESIDUE_POSITIONS, SPHERE_POSITIONS,
                POSE_POSITIONS);
        Fixture candidate = fixture(ROTATE_Z_90, "7", 1,
                RESIDUE_NAMES, RESIDUE_POSITIONS, SPHERE_POSITIONS,
                POSE_POSITIONS);

        CrossProteinPoseComparison comparison = compare(query, candidate);

        assertThat(comparison.relationship())
                .isEqualTo(PoseSiteRelationship.SAME_HOMOLOGOUS_SITE);
        assertThat(comparison.pocketsStructurallyHomologous()).isTrue();
        assertThat(comparison.pocketSimilarity())
                .isGreaterThanOrEqualTo(CrossProteinPoseComparisonOptions
                        .defaults().homologySimilarityThreshold());
        assertThat(comparison.samePocketNumber()).isFalse();
        assertThat(comparison.sharedAlignedContactResidues()).isEqualTo(2);
        assertThat(comparison.contactResidueSimilarity())
                .isCloseTo(1.0, offset(1.0e-6));
        assertThat(comparison.reason()).isNotBlank();
    }

    @Test
    void scenarioH_dissimilarPocketsAreDifferentSiteDespiteSameNumber() {
        Fixture query = fixture(RigidTransform.identity(), "4", 1,
                RESIDUE_NAMES, RESIDUE_POSITIONS, SPHERE_POSITIONS,
                POSE_POSITIONS);
        // Completely different protein: residues and spheres on a
        // 140 A line; same pocket number "4" on purpose.
        Fixture candidate = lineFixture("4");

        CrossProteinPoseComparison comparison = compare(query, candidate);

        assertThat(comparison.relationship())
                .isEqualTo(PoseSiteRelationship.DIFFERENT_SITE);
        assertThat(comparison.pocketsStructurallyHomologous()).isFalse();
        assertThat(comparison.pocketSimilarity())
                .isLessThan(CrossProteinPoseComparisonOptions
                        .defaults().homologySimilarityThreshold());
        // Equal pocket numbers are not evidence of correspondence.
        assertThat(comparison.samePocketNumber()).isTrue();
        assertThat(comparison.reason()).contains("homology threshold");
    }

    @Test
    void scenarioI_knownRigidTransformYieldsZeroDistanceAndRmsd() {
        Fixture query = fixture(RigidTransform.identity(), "3", 1,
                RESIDUE_NAMES, RESIDUE_POSITIONS, SPHERE_POSITIONS,
                POSE_POSITIONS);
        Fixture candidate = fixture(PERMUTE_AXES, "7", 1,
                RESIDUE_NAMES, RESIDUE_POSITIONS, SPHERE_POSITIONS,
                POSE_POSITIONS);

        CrossProteinPoseComparison comparison = compare(query, candidate);

        assertThat(comparison.relationship())
                .isEqualTo(PoseSiteRelationship.SAME_HOMOLOGOUS_SITE);
        assertThat(comparison.alignedLigandCentroidDistance())
                .isCloseTo(0.0, offset(1.0e-6));
        assertThat(comparison.alignedLigandRmsd())
                .isCloseTo(0.0, offset(1.0e-6));
    }

    @Test
    void homologousPocketsWithDisplacedPoseIsDifferentPose() {
        Fixture query = fixture(RigidTransform.identity(), "3", 1,
                RESIDUE_NAMES, RESIDUE_POSITIONS, SPHERE_POSITIONS,
                POSE_POSITIONS);
        // Candidate pose sits 8 A away from the query pose in the
        // candidate frame: same pocket family, different pose.
        Fixture candidate = fixture(ROTATE_Z_90, "7", 1,
                RESIDUE_NAMES, RESIDUE_POSITIONS, SPHERE_POSITIONS,
                shifted(POSE_POSITIONS, 8, 0, 0));

        CrossProteinPoseComparison comparison = compare(query, candidate);

        assertThat(comparison.relationship()).isEqualTo(
                PoseSiteRelationship.HOMOLOGOUS_SITE_DIFFERENT_POSE);
        assertThat(comparison.pocketsStructurallyHomologous()).isTrue();
        assertThat(comparison.alignedLigandCentroidDistance())
                .isCloseTo(8.0, offset(1.0e-6));
    }

    @Test
    void unequalHeavyAtomCountsNullTheRmsd() {
        Fixture query = fixture(RigidTransform.identity(), "3", 1,
                RESIDUE_NAMES, RESIDUE_POSITIONS, SPHERE_POSITIONS,
                POSE_POSITIONS);
        double[][] longerPose = {
                {3, 3, 2},
                {4, 4, 3},
                {5, 3, 4},
                {4, 2, 3},
                {6, 4, 3}
        };
        Fixture candidate = fixture(ROTATE_Z_90, "7", 1,
                RESIDUE_NAMES, RESIDUE_POSITIONS, SPHERE_POSITIONS,
                longerPose);

        CrossProteinPoseComparison comparison = compare(query, candidate);

        assertThat(comparison.alignedLigandRmsd()).isNull();
        assertThat(comparison.alignedLigandCentroidDistance())
                .isLessThan(3.0);
        assertThat(comparison.relationship())
                .isEqualTo(PoseSiteRelationship.SAME_HOMOLOGOUS_SITE);
    }

    @Test
    void notAssignedPoseYieldsAmbiguousRelationship() {
        Fixture query = fixture(RigidTransform.identity(), "3", 1,
                RESIDUE_NAMES, RESIDUE_POSITIONS, SPHERE_POSITIONS,
                POSE_POSITIONS);
        Fixture candidate = fixture(ROTATE_Z_90, "7", 1,
                RESIDUE_NAMES, RESIDUE_POSITIONS, SPHERE_POSITIONS,
                POSE_POSITIONS);

        CrossProteinPoseComparison comparison = comparator.compare(
                "query-pose",
                query.receptor(),
                notAssigned(),
                query.pose(),
                query.contacts(),
                "candidate-pose",
                candidate.receptor(),
                assigned(candidate.pocket()),
                candidate.pose(),
                candidate.contacts()
        );

        assertThat(comparison.relationship())
                .isEqualTo(PoseSiteRelationship.AMBIGUOUS);
        assertThat(comparison.pocketSimilarity()).isNull();
        assertThat(comparison.alignedLigandCentroidDistance()).isNull();
        assertThat(comparison.alignedLigandRmsd()).isNull();
        assertThat(comparison.queryPocket()).isNull();
        assertThat(comparison.reason()).contains("assignment");
    }

    @Test
    void ambiguousAssignmentYieldsAmbiguousRelationship() {
        Fixture query = fixture(RigidTransform.identity(), "3", 1,
                RESIDUE_NAMES, RESIDUE_POSITIONS, SPHERE_POSITIONS,
                POSE_POSITIONS);
        Fixture candidate = fixture(ROTATE_Z_90, "7", 1,
                RESIDUE_NAMES, RESIDUE_POSITIONS, SPHERE_POSITIONS,
                POSE_POSITIONS);

        CrossProteinPoseComparison comparison = comparator.compare(
                "query-pose",
                query.receptor(),
                ambiguous(query.pocket()),
                query.pose(),
                query.contacts(),
                "candidate-pose",
                candidate.receptor(),
                assigned(candidate.pocket()),
                candidate.pose(),
                candidate.contacts()
        );

        assertThat(comparison.relationship())
                .isEqualTo(PoseSiteRelationship.AMBIGUOUS);
        assertThat(comparison.queryPocket()).isNotNull();
        assertThat(comparison.pocketSimilarity()).isNull();
    }

    private CrossProteinPoseComparison compare(
            Fixture query,
            Fixture candidate
    ) {
        return comparator.compare(
                "query-pose",
                query.receptor(),
                assigned(query.pocket()),
                query.pose(),
                query.contacts(),
                "candidate-pose",
                candidate.receptor(),
                assigned(candidate.pocket()),
                candidate.pose(),
                candidate.contacts()
        );
    }

    /**
     * Receptor, fpocket pocket, pose and contacts where every
     * coordinate is the base layout moved by {@code transform}.
     */
    private static Fixture fixture(
            RigidTransform transform,
            String pocketId,
            int firstResidueNumber,
            String[] residueNames,
            double[][] residuePositions,
            double[][] spherePositions,
            double[][] posePositions
    ) {
        Structure receptor =
                receptor(transform, firstResidueNumber,
                        residueNames, residuePositions);
        Pocket pocket = pocket(transform, pocketId,
                firstResidueNumber, residuePositions.length,
                spherePositions);
        Ligand pose = pose(transform, posePositions);
        List<LigandContact> contacts = List.of(
                contact(firstResidueNumber),
                contact(firstResidueNumber + 1)
        );

        return new Fixture(receptor, pocket, pose, contacts);
    }

    /**
     * Candidate spread over ~140 A in an irregular zigzag: nothing
     * like the compact query cluster after any rigid alignment.
     */
    private static Fixture lineFixture(String pocketId) {
        String[] names = {
                "PRO", "ARG", "GLU", "THR", "ILE", "TRP"
        };
        double[][] line = new double[6][];
        for (int index = 0; index < 6; index++) {
            line[index] = new double[]{20.0 * index, 0, 0};
        }
        double[][] spheres = {
                {0, 0, 0},
                {20, 5, 0},
                {40, 0, 5},
                {60, 10, 0},
                {80, 0, 0},
                {100, 5, 10},
                {120, 0, 0},
                {140, 15, 5}
        };
        double[][] pose = {
                {0, 0, 0},
                {20, 0, 0},
                {40, 0, 0},
                {60, 0, 0}
        };

        Structure receptor =
                receptor(RigidTransform.identity(), 101, names, line);
        Pocket pocket = pocket(
                RigidTransform.identity(), pocketId, 101, 6, spheres);

        return new Fixture(
                receptor,
                pocket,
                pose(RigidTransform.identity(), pose),
                List.of()
        );
    }

    private static Structure receptor(
            RigidTransform transform,
            int firstResidueNumber,
            String[] names,
            double[][] positions
    ) {
        List<Residue> residues = new ArrayList<>();

        for (int index = 0; index < names.length; index++) {
            residues.add(new Residue(
                    names[index],
                    firstResidueNumber + index,
                    List.of(atom(1000 + index, "CA",
                            transform.apply(point(positions[index]))))
            ));
        }

        return new Structure(List.of(new Chain("A", residues)));
    }

    private static Pocket pocket(
            RigidTransform transform,
            String pocketId,
            int firstResidueNumber,
            int residueCount,
            double[][] spherePositions
    ) {
        List<ResidueId> residues = new ArrayList<>();
        for (int index = 0; index < residueCount; index++) {
            residues.add(new ResidueId(
                    "A", firstResidueNumber + index, null));
        }

        List<AlphaSphere> spheres = new ArrayList<>();
        for (int index = 0; index < spherePositions.length; index++) {
            spheres.add(new AlphaSphere(
                    index,
                    transform.apply(point(spherePositions[index])),
                    2.0
            ));
        }

        return new Pocket(
                new PocketId(pocketId),
                "pocket-" + pocketId,
                PocketSource.FPOCKET,
                transform.apply(point(spherePositions[0])),
                residues,
                List.of(),
                Optional.empty(),
                Optional.of(new AlphaSphereSet(spheres)),
                Map.of()
        );
    }

    private static Ligand pose(
            RigidTransform transform,
            double[][] positions
    ) {
        List<Atom> atoms = new ArrayList<>();
        for (int index = 0; index < positions.length; index++) {
            atoms.add(atom(1 + index, "C" + (index + 1),
                    transform.apply(point(positions[index]))));
        }

        Residue residue = new Residue("LIG", 1, atoms);
        Structure structure = new Structure(
                List.of(new Chain("L", List.of(residue))));

        return new Ligand("L", "L", null, null, null, null, structure);
    }

    private static LigandContact contact(int residueNumber) {
        return new LigandContact(
                atom(5001, "C1", new Point3D(0, 0, 0)),
                atom(5002, "CA", new Point3D(0, 0, 0)),
                new ResidueId("A", residueNumber, null),
                3.0,
                ContactType.DIRECT
        );
    }

    private static PosePocketAssignment assigned(Pocket pocket) {
        return new PosePocketAssignment(
                pocket,
                0.9,
                null,
                null,
                null,
                0.5,
                false,
                AssignmentStatus.ASSIGNED,
                "test"
        );
    }

    private static PosePocketAssignment ambiguous(Pocket pocket) {
        return new PosePocketAssignment(
                pocket,
                0.9,
                null,
                null,
                null,
                0.01,
                true,
                AssignmentStatus.AMBIGUOUS,
                "test"
        );
    }

    private static PosePocketAssignment notAssigned() {
        return new PosePocketAssignment(
                null,
                null,
                null,
                null,
                null,
                0.0,
                false,
                AssignmentStatus.NOT_ASSIGNED,
                "test"
        );
    }

    private static double[][] shifted(
            double[][] positions,
            double dx,
            double dy,
            double dz
    ) {
        double[][] shifted = new double[positions.length][];
        for (int index = 0; index < positions.length; index++) {
            shifted[index] = new double[]{
                    positions[index][0] + dx,
                    positions[index][1] + dy,
                    positions[index][2] + dz
            };
        }
        return shifted;
    }

    private static Point3D point(double[] coordinates) {
        return new Point3D(
                coordinates[0],
                coordinates[1],
                coordinates[2]
        );
    }

    private static Atom atom(
            int serial,
            String name,
            Point3D position
    ) {
        return Atom.builder()
                .pdbSerial(serial)
                .name(name)
                .position(position)
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(0.0)
                .element(Element.C)
                .build();
    }

    private record Fixture(
            Structure receptor,
            Pocket pocket,
            Ligand pose,
            List<LigandContact> contacts
    ) {
    }
}
