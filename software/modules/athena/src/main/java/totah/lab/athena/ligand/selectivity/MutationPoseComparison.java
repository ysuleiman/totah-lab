package totah.lab.athena.ligand.selectivity;

import totah.lab.athena.ligand.pose.PosePocketAssignment;
import totah.lab.athena.ligand.pose.PoseSiteRelationship;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Objects;

/**
 * Comparison of a mutant receptor's docked pose against a reference
 * (wild-type) pose: how far the predicted pose moved, which contacts
 * it gained or lost, and how the pocket assignment changed.
 *
 * <p>This is computational evidence about predicted poses only; it
 * says nothing about mechanism. {@code confidenceBefore}/
 * {@code confidenceAfter} are caller-supplied docking confidences,
 * carried as data and never used by any classification.
 *
 * <p>{@code alignedLigandCentroidShift} is the distance between the
 * heavy-atom centroids (for the same-frame path no transform is
 * applied — frame identity is a caller contract).
 * {@code alignedLigandRmsd} is the index-correspondence heavy-atom
 * RMSD and assumes both poses are the same compound in the same atom
 * order; it is {@code null} when the heavy-atom counts differ.
 * {@code ligandRotationAngle} is the angle (degrees) of the best-fit
 * Kabsch rotation between the corresponding heavy atoms; it is
 * {@code null} when the correspondence is invalid (unequal counts),
 * when there are fewer than three heavy atoms, or when the ligand
 * geometry is degenerate (collinear atoms), because the rotation is
 * then not robustly defined. {@code pocketRelationship} is carried
 * for cross-frame comparisons (see the cross-protein comparator) and
 * is {@code null} for same-frame comparisons.
 */
public record MutationPoseComparison(
        String mutationLabel,
        String referencePoseLabel,
        String mutantPoseLabel,
        double alignedLigandCentroidShift,
        Double alignedLigandRmsd,
        Double ligandRotationAngle,
        double contactSetJaccard,
        List<ResidueId> gainedContacts,
        List<ResidueId> lostContacts,
        List<ResidueId> retainedContacts,
        PosePocketAssignment pocketAssignmentBefore,
        PosePocketAssignment pocketAssignmentAfter,
        PoseSiteRelationship pocketRelationship,
        Double confidenceBefore,
        Double confidenceAfter
) {

    public MutationPoseComparison {
        mutationLabel = requireLabel(mutationLabel, "mutationLabel");
        referencePoseLabel = requireLabel(
                referencePoseLabel,
                "referencePoseLabel"
        );
        mutantPoseLabel = requireLabel(
                mutantPoseLabel,
                "mutantPoseLabel"
        );

        if (!Double.isFinite(alignedLigandCentroidShift)
                || alignedLigandCentroidShift < 0.0) {
            throw new IllegalArgumentException(
                    "alignedLigandCentroidShift must be finite and "
                            + "non-negative"
            );
        }

        if (!Double.isFinite(contactSetJaccard)
                || contactSetJaccard < 0.0
                || contactSetJaccard > 1.0) {
            throw new IllegalArgumentException(
                    "contactSetJaccard must be between 0 and 1"
            );
        }

        gainedContacts = List.copyOf(
                Objects.requireNonNull(gainedContacts, "gainedContacts")
        );
        lostContacts = List.copyOf(
                Objects.requireNonNull(lostContacts, "lostContacts")
        );
        retainedContacts = List.copyOf(
                Objects.requireNonNull(
                        retainedContacts,
                        "retainedContacts"
                )
        );

        Objects.requireNonNull(
                pocketAssignmentBefore,
                "pocketAssignmentBefore"
        );
        Objects.requireNonNull(
                pocketAssignmentAfter,
                "pocketAssignmentAfter"
        );
    }

    private static String requireLabel(
            String label,
            String fieldName
    ) {
        Objects.requireNonNull(label, fieldName);

        if (label.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }

        return label;
    }
}
