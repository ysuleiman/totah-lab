package totah.lab.athena.pocket.architecture;

import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Objects;

/**
 * Geometry-only comparison of the pocket wall: side-chain heavy-atom
 * positions of the pocket residues in the aligned (A) frame — no
 * sequence chemistry involved.
 *
 * <p>Definitions:</p>
 * <ul>
 *   <li>{@code sideChainDisplacements}: per aligned residue pair in
 *       the pocket region, the distance between the side-chain
 *       heavy-atom centroids (B transformed into the A frame), sorted
 *       descending. A residue without side-chain heavy atoms (e.g.
 *       GLY) falls back to its CA. This names where the wall
 *       physically moved.</li>
 *   <li>{@code wallDistanceFieldA}/{@code ...B}: per alpha sphere of
 *       the pocket (pocket order), distance from the sphere center to
 *       the nearest wall side-chain heavy atom of the SAME
 *       pocket.</li>
 *   <li>Wall normal of a sphere: the smallest-eigenvalue eigenvector
 *       of the PCA over the {@code normalNeighbourCount} nearest wall
 *       atoms to the sphere center (the local fit-plane normal).
 *       {@code meanNormalAngleDegrees}/{@code maxNormalAngleDegrees}
 *       compare normals across pockets: each A sphere is matched to
 *       the nearest aligned B sphere and the acute angle between
 *       their normals is measured.</li>
 *   <li>{@code meanRoughnessA}/{@code meanRoughnessB}: mean over
 *       spheres of the RMS deviation of the sphere's neighbouring
 *       wall atoms from their local fit plane.</li>
 * </ul>
 */
public record WallGeometryComparison(
        List<SideChainDisplacement> sideChainDisplacements,
        ResidueId maxDisplacementResidueA,
        ResidueId maxDisplacementResidueB,
        double maxSideChainDisplacement,
        List<Double> wallDistanceFieldA,
        List<Double> wallDistanceFieldB,
        double meanWallDistanceA,
        double meanWallDistanceB,
        double meanNormalAngleDegrees,
        double maxNormalAngleDegrees,
        double meanRoughnessA,
        double meanRoughnessB
) {

    /**
     * Side-chain centroid displacement of one aligned pocket-residue
     * pair, in angstroms.
     */
    public record SideChainDisplacement(
            ResidueId residueA,
            ResidueId residueB,
            String residueNameA,
            String residueNameB,
            double centroidDisplacement
    ) {
    }

    public WallGeometryComparison {
        sideChainDisplacements = List.copyOf(
                Objects.requireNonNull(sideChainDisplacements,
                        "sideChainDisplacements")
        );
        wallDistanceFieldA = List.copyOf(
                Objects.requireNonNull(wallDistanceFieldA,
                        "wallDistanceFieldA")
        );
        wallDistanceFieldB = List.copyOf(
                Objects.requireNonNull(wallDistanceFieldB,
                        "wallDistanceFieldB")
        );
    }
}
