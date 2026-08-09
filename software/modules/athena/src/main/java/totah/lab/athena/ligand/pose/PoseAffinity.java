package totah.lab.athena.ligand.pose;

import java.util.Objects;

/**
 * The Vina-predicted affinity of one docked pose, as a plain input
 * value. Athena stays Vina-agnostic: affinities arrive from the caller
 * and are reported alongside — never merged into — the pocket
 * assignment score.
 */
public record PoseAffinity(
        String poseLabel,
        double affinityKcalPerMol
) {

    public PoseAffinity {
        Objects.requireNonNull(poseLabel, "poseLabel");

        if (poseLabel.isBlank()) {
            throw new IllegalArgumentException(
                    "poseLabel must not be blank"
            );
        }

        if (!Double.isFinite(affinityKcalPerMol)) {
            throw new IllegalArgumentException(
                    "affinityKcalPerMol must be finite"
            );
        }
    }
}
