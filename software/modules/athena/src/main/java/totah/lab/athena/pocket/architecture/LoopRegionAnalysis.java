package totah.lab.athena.pocket.architecture;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Objects;

/**
 * Focused analysis of one residue range (the "loop region") of two
 * aligned receptors and their docked poses, pose B aligned into the
 * A frame with the receptor-backbone CA-Kabsch transform.
 *
 * <p>This is a measurement, not a mechanism claim: the verdict states
 * whether the pose displacement points toward or away from the loop
 * centroid, never that the loop causes anything.</p>
 *
 * <p>Region-level definitions:</p>
 * <ul>
 *   <li>{@code loopCentroidA}: centroid of the CA atoms of the
 *       in-range A-side residues ({@code null} when no aligned pair
 *       falls in the range).</li>
 *   <li>{@code poseACentroidToLoopAngstroms} /
 *       {@code poseBCentroidToLoopAngstroms}: distance from each pose
 *       heavy-atom centroid (B aligned) to the loop centroid;
 *       {@code null} when no loop centroid exists.</li>
 *   <li>{@code poseDisplacementTowardLoopAngstroms}: dot product of
 *       the A&rarr;B pose-centroid displacement with the unit vector
 *       from pose A's centroid to the loop centroid — positive means
 *       toward the loop. 0.0 (and an ORTHOGONAL verdict) when the
 *       direction is undefined.</li>
 * </ul>
 */
public record LoopRegionAnalysis(
        int rangeStart,
        int rangeEnd,
        List<LoopRegionResidueRow> rows,
        Point3D loopCentroidA,
        Double poseACentroidToLoopAngstroms,
        Double poseBCentroidToLoopAngstroms,
        double poseDisplacementTowardLoopAngstroms,
        LoopShiftVerdict verdict,
        String reason
) {

    /**
     * Per-aligned-residue-pair row of the loop region. Side-chain
     * values use the side-chain heavy atoms (N/CA/C/O/OXT excluded);
     * a residue without side-chain heavy atoms (e.g. GLY) falls back
     * to its CA — documented on the analyzer.
     *
     * @param caDisplacement aligned CA displacement (A)
     * @param backboneDisplacement RMS displacement over same-named
     *        backbone (N/CA/C/O) atoms present on both sides (A)
     * @param sideChainCentroidDisplacement displacement of the
     *        side-chain centroids (A)
     * @param sideChainRmsd RMS displacement over same-named side-chain
     *        heavy atoms; {@code null} when the two sides share no
     *        atom names
     * @param minDistanceToPoseA / minDistanceToPoseB minimum distance
     *        from the residue's side-chain atoms to the pose heavy
     *        atoms (B aligned)
     * @param contactA / contactB whether that minimum distance is
     *        within the contact cutoff
     * @param pocketWallA / pocketWallB whether the residue belongs to
     *        the respective pocket's residue list
     * @param burialA / burialB burial proxy: count of receptor heavy
     *        atoms within the burial radius of the side-chain
     *        centroid, excluding the residue's own atoms
     * @param localCavityDisplacement mean displacement of the A-side
     *        alpha spheres within the locality cutoff of the residue
     *        centroid to their nearest aligned B-side sphere;
     *        {@code null} when no such spheres exist
     * @param localFreeVolumeDifference shell free-volume fraction at
     *        the B side-chain centroid minus at the A centroid (each
     *        measured against its own receptor, excluding the
     *        residue's own atoms)
     */
    public record LoopRegionResidueRow(
            ResidueId residueA,
            ResidueId residueB,
            String residueNameA,
            String residueNameB,
            double caDisplacement,
            double backboneDisplacement,
            double sideChainCentroidDisplacement,
            Double sideChainRmsd,
            double minDistanceToPoseA,
            double minDistanceToPoseB,
            boolean contactA,
            boolean contactB,
            boolean pocketWallA,
            boolean pocketWallB,
            int burialA,
            int burialB,
            Double localCavityDisplacement,
            double localFreeVolumeDifference
    ) {
    }

    public LoopRegionAnalysis {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(reason, "reason");

        if (reason.isBlank()) {
            throw new IllegalArgumentException(
                    "reason must not be blank"
            );
        }
    }
}
