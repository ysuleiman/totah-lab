package totah.lab.athena.ligand.selectivity;

import org.junit.jupiter.api.Test;
import totah.lab.athena.ligand.contact.LigandContact;
import totah.lab.athena.ligand.pose.AssignmentStatus;
import totah.lab.athena.ligand.pose.PosePocketAssignment;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static totah.lab.athena.ligand.selectivity.DefaultLigandContactAlignmentAnalyzerTest.atom;
import static totah.lab.athena.ligand.selectivity.DefaultLigandContactAlignmentAnalyzerTest.contact;
import static totah.lab.athena.ligand.selectivity.DefaultLigandContactAlignmentAnalyzerTest.pocket;
import static totah.lab.athena.ligand.selectivity.DefaultLigandContactAlignmentAnalyzerTest.receptor;

class DefaultMutationPoseComparatorTest {

    private final DefaultMutationPoseComparator comparator =
            new DefaultMutationPoseComparator();

    private static final Structure RECEPTOR = receptor(1, "ALA");

    private static final double[][] BASE_POSE = {
            {1, 0, 0},
            {2, 0, 0},
            {1, 1, 0}
    };

    @Test
    void identicalPosesHaveZeroShiftRmsdAndFullOverlap() {
        Ligand wt = pose("wt-pose", BASE_POSE);
        Ligand mutant = pose("mutant-pose", BASE_POSE);

        MutationPoseComparison comparison = comparator.compareSameFrame(
                "F43L",
                RECEPTOR, wt, List.of(contact(10), contact(11)),
                RECEPTOR, mutant, List.of(contact(10), contact(11)),
                assigned(), assigned(), null, null
        );

        assertThat(comparison.mutationLabel()).isEqualTo("F43L");
        assertThat(comparison.referencePoseLabel()).isEqualTo("wt-pose");
        assertThat(comparison.mutantPoseLabel()).isEqualTo("mutant-pose");
        assertThat(comparison.alignedLigandCentroidShift())
                .isCloseTo(0.0, offset(1.0e-9));
        assertThat(comparison.alignedLigandRmsd())
                .isCloseTo(0.0, offset(1.0e-9));
        assertThat(comparison.ligandRotationAngle())
                .isCloseTo(0.0, offset(1.0e-6));
        assertThat(comparison.contactSetJaccard()).isEqualTo(1.0);
        assertThat(comparison.gainedContacts()).isEmpty();
        assertThat(comparison.lostContacts()).isEmpty();
        assertThat(comparison.retainedContacts())
                .containsExactly(
                        new ResidueId("A", 10, null),
                        new ResidueId("A", 11, null));
        assertThat(comparison.pocketRelationship()).isNull();
    }

    @Test
    void pureTranslationGivesExpectedShiftAndRmsdWithoutRotation() {
        Ligand wt = pose("wt-pose", BASE_POSE);
        Ligand mutant = pose("mutant-pose", translated(BASE_POSE, 3, 0, 0));

        MutationPoseComparison comparison = comparator.compareSameFrame(
                "F43L",
                RECEPTOR, wt, List.of(),
                RECEPTOR, mutant, List.of(),
                assigned(), assigned(), null, null
        );

        assertThat(comparison.alignedLigandCentroidShift())
                .isCloseTo(3.0, offset(1.0e-9));
        assertThat(comparison.alignedLigandRmsd())
                .isCloseTo(3.0, offset(1.0e-9));
        assertThat(comparison.ligandRotationAngle())
                .isCloseTo(0.0, offset(1.0e-6));
        // No contacts on either side: empty union, no evidence.
        assertThat(comparison.contactSetJaccard()).isEqualTo(0.0);
    }

    @Test
    void ninetyDegreeRotationIsRecovered() {
        Ligand wt = pose("wt-pose", BASE_POSE);
        double[][] rotated = {
                {0, 1, 0},
                {0, 2, 0},
                {-1, 1, 0}
        };
        Ligand mutant = pose("mutant-pose", rotated);

        MutationPoseComparison comparison = comparator.compareSameFrame(
                "F43L",
                RECEPTOR, wt, List.of(),
                RECEPTOR, mutant, List.of(),
                assigned(), assigned(), null, null
        );

        assertThat(comparison.ligandRotationAngle())
                .isCloseTo(90.0, offset(1.0e-6));
        // Centroid (4/3, 1/3, 0) -> (-1/3, 4/3, 0).
        assertThat(comparison.alignedLigandCentroidShift())
                .isCloseTo(Math.sqrt(34.0) / 3.0, offset(1.0e-9));
        // Per-index squared distances 2, 8, 4.
        assertThat(comparison.alignedLigandRmsd())
                .isCloseTo(Math.sqrt(14.0 / 3.0), offset(1.0e-9));
    }

    @Test
    void gainedLostAndRetainedContactsAreSetDifferences() {
        Ligand wt = pose("wt-pose", BASE_POSE);
        Ligand mutant = pose("mutant-pose", BASE_POSE);

        MutationPoseComparison comparison = comparator.compareSameFrame(
                "F43L",
                RECEPTOR, wt, List.of(contact(10), contact(11)),
                RECEPTOR, mutant, List.of(contact(11), contact(12)),
                assigned(), assigned(), -8.5, -7.0
        );

        assertThat(comparison.gainedContacts())
                .containsExactly(new ResidueId("A", 12, null));
        assertThat(comparison.lostContacts())
                .containsExactly(new ResidueId("A", 10, null));
        assertThat(comparison.retainedContacts())
                .containsExactly(new ResidueId("A", 11, null));
        assertThat(comparison.contactSetJaccard())
                .isCloseTo(1.0 / 3.0, offset(1.0e-9));
        // Confidence is carried as data only.
        assertThat(comparison.confidenceBefore()).isEqualTo(-8.5);
        assertThat(comparison.confidenceAfter()).isEqualTo(-7.0);
    }

    @Test
    void unequalHeavyAtomCountsNullRmsdAndRotation() {
        Ligand wt = pose("wt-pose", BASE_POSE);
        Ligand mutant = pose("mutant-pose", new double[][]{
                {1, 0, 0},
                {2, 0, 0}
        });

        MutationPoseComparison comparison = comparator.compareSameFrame(
                "F43L",
                RECEPTOR, wt, List.of(),
                RECEPTOR, mutant, List.of(),
                assigned(), assigned(), null, null
        );

        assertThat(comparison.alignedLigandRmsd()).isNull();
        assertThat(comparison.ligandRotationAngle()).isNull();
        // The centroid shift is always computed:
        // (4/3, 1/3, 0) vs (3/2, 0, 0) -> sqrt(5)/6.
        assertThat(comparison.alignedLigandCentroidShift())
                .isCloseTo(Math.sqrt(5.0) / 6.0, offset(1.0e-9));
    }

    @Test
    void collinearLigandHasNoRobustRotation() {
        double[][] line = {
                {0, 0, 0},
                {2, 0, 0},
                {4, 0, 0}
        };
        Ligand wt = pose("wt-pose", line);
        Ligand mutant = pose("mutant-pose", translated(line, 1, 0, 0));

        MutationPoseComparison comparison = comparator.compareSameFrame(
                "F43L",
                RECEPTOR, wt, List.of(),
                RECEPTOR, mutant, List.of(),
                assigned(), assigned(), null, null
        );

        assertThat(comparison.ligandRotationAngle()).isNull();
        assertThat(comparison.alignedLigandRmsd())
                .isCloseTo(1.0, offset(1.0e-9));
        assertThat(comparison.alignedLigandCentroidShift())
                .isCloseTo(1.0, offset(1.0e-9));
    }

    static MutationPoseComparison comparison(
            double shift,
            Double rmsd,
            double jaccard
    ) {
        return new MutationPoseComparison(
                "F43L",
                "reference-pose",
                "mutant-pose",
                shift,
                rmsd,
                null,
                jaccard,
                List.of(),
                List.of(),
                List.of(),
                assigned(),
                assigned(),
                null,
                null,
                null
        );
    }

    static PosePocketAssignment assigned() {
        return new PosePocketAssignment(
                pocket(List.of(new ResidueId("A", 10, null))),
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

    private static Ligand pose(String id, double[][] positions) {
        List<totah.lab.gaia.structure.Atom> atoms = new ArrayList<>();

        for (int index = 0; index < positions.length; index++) {
            atoms.add(atom(1 + index, "C" + (index + 1),
                    new Point3D(
                            positions[index][0],
                            positions[index][1],
                            positions[index][2])));
        }

        Residue residue = new Residue("LIG", 1, atoms);
        Structure structure = new Structure(
                List.of(new Chain("L", List.of(residue))));

        return new Ligand(id, id, null, null, null, null, structure);
    }

    private static double[][] translated(
            double[][] positions,
            double dx,
            double dy,
            double dz
    ) {
        double[][] translated = new double[positions.length][];

        for (int index = 0; index < positions.length; index++) {
            translated[index] = new double[]{
                    positions[index][0] + dx,
                    positions[index][1] + dy,
                    positions[index][2] + dz
            };
        }

        return translated;
    }
}
