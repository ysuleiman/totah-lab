package totah.lab.athena.pocket.architecture;

import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Objects;

/**
 * The residues lying between two aligned poses (the inter-pose
 * region, see {@link InterPoseRegionOptions} for the definition),
 * with their aligned displacements and pose distances.
 *
 * <p>Rows are sorted by the documented ordering: ascending minimum
 * distance to either pose, then descending side-chain centroid
 * displacement (residues without an aligned counterpart sort with
 * displacement 0). This ranks geometric features near the
 * pose-transition region; it does not establish mechanism.</p>
 */
public record InterPoseRegionAnalysis(
        List<InterPoseRegionResidueRow> rows
) {

    /**
     * Chemistry relationship of an aligned residue pair.
     */
    public enum ChemistryDifference {
        /** Same residue name on both sides. */
        SAME_RESIDUE,

        /** Different names, same broad chemistry class
         *  ({@code ResidueChemistry}). */
        SAME_CHEMISTRY,

        /** Different chemistry classes. */
        DIFFERENT_CHEMISTRY,

        /** No aligned counterpart on the B side. */
        UNPAIRED
    }

    /**
     * One receptor-A residue of the inter-pose region. Displacement
     * fields are {@code null} when the residue has no aligned B
     * counterpart. Distances are from any of the residue's heavy
     * atoms to the nearest pose heavy atom (pose B in the aligned
     * frame).
     */
    public record InterPoseRegionResidueRow(
            ResidueId residueA,
            String residueNameA,
            ResidueId residueB,
            String residueNameB,
            Double caDisplacement,
            Double backboneDisplacement,
            Double sideChainCentroidDisplacement,
            double minDistanceToPoseA,
            double minDistanceToPoseB,
            double localFreeVolumeDifference,
            ChemistryDifference chemistryDifference
    ) {

        double rankingDistance() {
            return Math.min(minDistanceToPoseA, minDistanceToPoseB);
        }

        double rankingDisplacement() {
            return sideChainCentroidDisplacement == null
                    ? 0.0
                    : sideChainCentroidDisplacement;
        }
    }

    public InterPoseRegionAnalysis {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
    }
}
