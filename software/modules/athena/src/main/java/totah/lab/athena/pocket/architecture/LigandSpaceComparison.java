package totah.lab.athena.pocket.architecture;

import java.util.Objects;
import java.util.Set;

/**
 * Comparison of two poses, each in its own receptor/pocket, in the
 * ALIGNED (pocket-A) frame: the candidate pose B is moved into pocket
 * A's frame with the pocket-alignment transform.
 *
 * <p>Definitions:</p>
 * <ul>
 *   <li>{@code occupiedSpheresA}/{@code occupiedSpheresB}: ids of the
 *       OWN pocket's spheres occupied by the pose under the occupancy
 *       criterion of {@link LigandSpaceOptions} (default: a ligand
 *       heavy-atom center inside the sphere; B spheres and pose B
 *       measured in the A frame).</li>
 *   <li>{@code occupiedComponentsPoseA}/{@code occupiedComponentsPoseB}:
 *       component ids of the POCKET-A sphere set occupied by pose A
 *       respectively by the aligned pose B — the cross-pocket
 *       compartment comparison.</li>
 *   <li>{@code occupancyJaccard}: Jaccard of the pocket-A sphere sets
 *       occupied by the two poses in the shared frame; 0.0 when both
 *       sets are empty (no occupancy evidence).</li>
 *   <li>{@code alignedCentroidDisplacement} and
 *       {@code displacementAlongU1/U2/U3}: the displacement vector
 *       from pose A's heavy-atom centroid to the ALIGNED pose B
 *       centroid, decomposed onto pocket A's principal axes (u1 is
 *       the depth axis). {@code lateralDisplacement} is the magnitude
 *       of the u2/u3 components.</li>
 *   <li>{@code depthPoseA}/{@code depthPoseB}: pose centroid depth =
 *       mouth-plane projection minus pose-centroid projection on the
 *       own pocket's first principal axis (positive = below the mouth
 *       plane, toward the interior).</li>
 *   <li>{@code mouthDistancePoseA}/{@code mouthDistancePoseB}: radial
 *       distance of the pose centroid from the own pocket's mouth
 *       CENTER (centroid of the mouth sphere centers) — deliberately
 *       distinct from the signed depth.</li>
 *   <li>{@code dominantDifference}: the first firing rule of
 *       {@link DominantArchitectureDifference}, evaluated in enum
 *       declaration order.</li>
 * </ul>
 */
public record LigandSpaceComparison(
        LigandSpaceAnalysis poseA,
        LigandSpaceAnalysis poseB,
        Set<Long> occupiedSpheresA,
        Set<Long> occupiedSpheresB,
        Set<Integer> occupiedComponentsPoseA,
        Set<Integer> occupiedComponentsPoseB,
        double occupancyJaccard,
        double alignedCentroidDisplacement,
        double displacementAlongU1,
        double displacementAlongU2,
        double displacementAlongU3,
        double lateralDisplacement,
        double depthPoseA,
        double depthPoseB,
        double mouthDistancePoseA,
        double mouthDistancePoseB,
        DominantArchitectureDifference dominantDifference,
        String reason
) {

    public LigandSpaceComparison {
        Objects.requireNonNull(poseA, "poseA");
        Objects.requireNonNull(poseB, "poseB");
        occupiedSpheresA = Set.copyOf(
                Objects.requireNonNull(
                        occupiedSpheresA,
                        "occupiedSpheresA"
                )
        );
        occupiedSpheresB = Set.copyOf(
                Objects.requireNonNull(
                        occupiedSpheresB,
                        "occupiedSpheresB"
                )
        );
        occupiedComponentsPoseA = Set.copyOf(
                Objects.requireNonNull(
                        occupiedComponentsPoseA,
                        "occupiedComponentsPoseA"
                )
        );
        occupiedComponentsPoseB = Set.copyOf(
                Objects.requireNonNull(
                        occupiedComponentsPoseB,
                        "occupiedComponentsPoseB"
                )
        );
        Objects.requireNonNull(
                dominantDifference,
                "dominantDifference"
        );
        Objects.requireNonNull(reason, "reason");

        if (reason.isBlank()) {
            throw new IllegalArgumentException(
                    "reason must not be blank"
            );
        }
    }
}
