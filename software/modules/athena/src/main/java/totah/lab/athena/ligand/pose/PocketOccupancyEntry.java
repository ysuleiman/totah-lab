package totah.lab.athena.ligand.pose;

import totah.lab.gaia.pocket.Pocket;

import java.util.List;
import java.util.Objects;

/**
 * Pose-occupancy summary of one pocket across a docking run: how many
 * predicted poses were assigned to it, their affinities, and their
 * assignment scores.
 *
 * <p>{@code poseCount} and {@code fractionOfPoses} describe <b>pose
 * frequency only</b> — how often predicted poses occupy this pocket in
 * the run. They are not a thermodynamic probability and say nothing
 * about binding. {@code bestAffinity} is the most favorable (lowest)
 * Vina affinity among the assigned poses; affinity and assignment
 * score are reported side by side, never merged.
 *
 * <p>{@code poseLabels} preserves the input order of the poses.
 */
public record PocketOccupancyEntry(
        Pocket pocket,
        int poseCount,
        double fractionOfPoses,
        double bestAffinity,
        double medianAffinity,
        double meanAssignmentScore,
        double bestAssignmentScore,
        List<String> poseLabels
) {

    public PocketOccupancyEntry {
        Objects.requireNonNull(pocket, "pocket");

        if (poseCount <= 0) {
            throw new IllegalArgumentException(
                    "poseCount must be positive"
            );
        }

        if (!Double.isFinite(fractionOfPoses)
                || fractionOfPoses <= 0.0
                || fractionOfPoses > 1.0) {
            throw new IllegalArgumentException(
                    "fractionOfPoses must be in (0, 1]"
            );
        }

        if (!Double.isFinite(bestAffinity)) {
            throw new IllegalArgumentException(
                    "bestAffinity must be finite"
            );
        }

        if (!Double.isFinite(medianAffinity)) {
            throw new IllegalArgumentException(
                    "medianAffinity must be finite"
            );
        }

        validateScore(meanAssignmentScore, "meanAssignmentScore");
        validateScore(bestAssignmentScore, "bestAssignmentScore");

        poseLabels = List.copyOf(
                Objects.requireNonNull(poseLabels, "poseLabels")
        );

        if (poseLabels.size() != poseCount) {
            throw new IllegalArgumentException(
                    "poseLabels size must match poseCount"
            );
        }
    }

    private static void validateScore(
            double value,
            String fieldName
    ) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(
                    fieldName + " must be finite and non-negative"
            );
        }
    }
}
