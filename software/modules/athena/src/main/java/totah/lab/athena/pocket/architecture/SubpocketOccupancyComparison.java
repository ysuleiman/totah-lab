package totah.lab.athena.pocket.architecture;

import totah.lab.gaia.geometry.Point3D;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Occupancy comparison of two aligned poses against one common
 * reference pocket cloud (typically the superpocket's alpha spheres):
 * both poses are placed in the reference frame by the caller's
 * transform, and every occupancy set names REFERENCE-pocket sphere
 * ids.
 *
 * <ul>
 *   <li>{@code occupiedReferenceSpheresPoseA}/{@code ...PoseB}:
 *       reference spheres occupied by each pose.</li>
 *   <li>{@code occupiedBoth}/{@code poseAOnly}/{@code poseBOnly}:
 *       set arithmetic on those; {@code differenceCloudPoseAOnly} /
 *       {@code differenceCloudPoseBOnly} are the COORDINATES of the
 *       only-occupied reference spheres.</li>
 *   <li>{@code occupiedOwnCloudSpheresPoseA}: spheres of pose A's OWN
 *       pocket (the subsite) occupied by pose A, in that pocket's own
 *       ids — the directional subsite-vs-superpocket context.</li>
 *   <li>{@code poseAAtoms}/{@code poseBAtoms}: per heavy atom, the
 *       nearest reference sphere, its center/surface distance, and
 *       the local sphere density.</li>
 *   <li>{@code depthPoseA}/{@code depthPoseB}: pose-centroid depth
 *       along the reference pocket's first principal axis (mouth-plane
 *       relative, positive = interior); the u2/u3 fields are the
 *       centroid's lateral coordinates on the remaining axes.</li>
 * </ul>
 */
public record SubpocketOccupancyComparison(
        Set<Long> occupiedReferenceSpheresPoseA,
        Set<Long> occupiedReferenceSpheresPoseB,
        Set<Long> occupiedBoth,
        Set<Long> occupiedPoseAOnly,
        Set<Long> occupiedPoseBOnly,
        Set<Long> occupiedOwnCloudSpheresPoseA,
        List<Point3D> differenceCloudPoseAOnly,
        List<Point3D> differenceCloudPoseBOnly,
        List<AtomSphereContext> poseAAtoms,
        List<AtomSphereContext> poseBAtoms,
        double depthPoseA,
        double depthPoseB,
        double lateralU2PoseA,
        double lateralU3PoseA,
        double lateralU2PoseB,
        double lateralU3PoseB,
        int referenceSphereCount
) {

    /**
     * Sphere context of one ligand heavy atom against the reference
     * cloud.
     *
     * @param nearestSphereId reference-pocket sphere id
     * @param nearestSphereCenterDistance center-to-center distance (A)
     * @param nearestSphereSurfaceDistance signed surface distance
     *        max(0, d - radius) (A)
     * @param localSphereCount reference spheres whose centers lie
     *        within the local-density radius of the atom
     */
    public record AtomSphereContext(
            String atomName,
            long nearestSphereId,
            double nearestSphereCenterDistance,
            double nearestSphereSurfaceDistance,
            int localSphereCount
    ) {
    }

    public SubpocketOccupancyComparison {
        occupiedReferenceSpheresPoseA = Set.copyOf(Objects
                .requireNonNull(occupiedReferenceSpheresPoseA,
                        "occupiedReferenceSpheresPoseA"));
        occupiedReferenceSpheresPoseB = Set.copyOf(Objects
                .requireNonNull(occupiedReferenceSpheresPoseB,
                        "occupiedReferenceSpheresPoseB"));
        occupiedBoth = Set.copyOf(Objects.requireNonNull(
                occupiedBoth, "occupiedBoth"));
        occupiedPoseAOnly = Set.copyOf(Objects.requireNonNull(
                occupiedPoseAOnly, "occupiedPoseAOnly"));
        occupiedPoseBOnly = Set.copyOf(Objects.requireNonNull(
                occupiedPoseBOnly, "occupiedPoseBOnly"));
        occupiedOwnCloudSpheresPoseA = Set.copyOf(Objects.requireNonNull(
                occupiedOwnCloudSpheresPoseA,
                "occupiedOwnCloudSpheresPoseA"));
        differenceCloudPoseAOnly = List.copyOf(Objects.requireNonNull(
                differenceCloudPoseAOnly, "differenceCloudPoseAOnly"));
        differenceCloudPoseBOnly = List.copyOf(Objects.requireNonNull(
                differenceCloudPoseBOnly, "differenceCloudPoseBOnly"));
        poseAAtoms = List.copyOf(Objects.requireNonNull(
                poseAAtoms, "poseAAtoms"));
        poseBAtoms = List.copyOf(Objects.requireNonNull(
                poseBAtoms, "poseBAtoms"));
    }

    /**
     * Directional occupancy summary: subsite-by-superpocket
     * containment is reported per direction (A-occupies, B-occupies,
     * shared), never collapsed into one number.
     */
    public String render() {
        return String.format(
                "pose A occupies %d of %d reference spheres; pose B "
                        + "%d of %d; shared %d; A-only %d; B-only "
                        + "%d%n",
                occupiedReferenceSpheresPoseA.size(),
                referenceSphereCount,
                occupiedReferenceSpheresPoseB.size(),
                referenceSphereCount,
                occupiedBoth.size(),
                occupiedPoseAOnly.size(),
                occupiedPoseBOnly.size()
        ) + String.format(
                "pose A occupies %d spheres of its own subsite "
                        + "cloud%n",
                occupiedOwnCloudSpheresPoseA.size()
        ) + String.format(
                "pose centroid depth along u1: A %.2f A, B %.2f A; "
                        + "lateral (u2, u3): A (%.2f, %.2f), B "
                        + "(%.2f, %.2f)%n",
                depthPoseA,
                depthPoseB,
                lateralU2PoseA,
                lateralU3PoseA,
                lateralU2PoseB,
                lateralU3PoseB
        );
    }
}
