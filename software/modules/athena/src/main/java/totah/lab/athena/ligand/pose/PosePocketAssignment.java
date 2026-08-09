package totah.lab.athena.ligand.pose;

import totah.lab.gaia.pocket.Pocket;

import java.util.Objects;

/**
 * The assignment of one predicted Vina pose to a candidate pocket,
 * together with the runner-up and the reason the deciding rule fired,
 * so a report reader can audit the decision without re-running the
 * assigner.
 *
 * <p>Terminology: a Vina pose is <i>assigned to</i> a pocket, or a
 * predicted pose <i>occupies</i> a pocket — this record says nothing
 * about binding. {@code pocket} and {@code assignmentScore} are
 * {@code null} for {@link AssignmentStatus#NOT_ASSIGNED}; the best
 * candidate's {@code bestMetrics} are still exposed (when candidates
 * existed) so the rejected evidence stays visible.
 */
public record PosePocketAssignment(
        Pocket pocket,
        Double assignmentScore,
        PosePocketMetrics bestMetrics,
        Pocket secondBestPocket,
        Double secondBestScore,
        double scoreMargin,
        boolean ambiguous,
        AssignmentStatus status,
        String reason
) {

    public PosePocketAssignment {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reason, "reason");

        if (reason.isBlank()) {
            throw new IllegalArgumentException(
                    "reason must not be blank"
            );
        }

        if (!Double.isFinite(scoreMargin) || scoreMargin < 0.0) {
            throw new IllegalArgumentException(
                    "scoreMargin must be finite and non-negative"
            );
        }

        if (status == AssignmentStatus.NOT_ASSIGNED) {
            if (pocket != null || assignmentScore != null) {
                throw new IllegalArgumentException(
                        "NOT_ASSIGNED assignments must not report a "
                                + "pocket or score"
                );
            }
        } else {
            Objects.requireNonNull(pocket, "pocket");
            Objects.requireNonNull(assignmentScore, "assignmentScore");
        }

        if (ambiguous != (status == AssignmentStatus.AMBIGUOUS)) {
            throw new IllegalArgumentException(
                    "ambiguous must match status AMBIGUOUS"
            );
        }
    }
}
