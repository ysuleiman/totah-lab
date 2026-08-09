package totah.lab.athena.ligand.pose;

import totah.lab.gaia.pocket.Pocket;

import java.util.Objects;

/**
 * Everything known about one candidate pocket for a predicted pose:
 * alpha-sphere occupancy (the primary signal), containment of the
 * pose's heavy atoms, contact-residue coverage, and centroid proximity
 * (the weakest signal, never decisive on its own).
 *
 * <p>{@code spheres} is {@code null} when the pocket carries no alpha
 * spheres; containment then falls back to bounding-box or residue-atom
 * proximity and {@code basis} records which basis was used, so the
 * fallback is visible, never silent.
 */
public record PosePocketMetrics(
        Pocket pocket,
        AlphaSphereOccupancy spheres,
        double ligandCentroidDistance,
        double atomContainmentFraction,
        ContainmentBasis basis,
        double contactResidueCoverage,
        double pocketContactCoverage,
        double centroidProximity
) {

    /**
     * Which geometric basis produced
     * {@link PosePocketMetrics#atomContainmentFraction()}.
     */
    public enum ContainmentBasis {

        /** Fraction of heavy atoms within tolerance of an alpha sphere. */
        ALPHA_SPHERES,

        /** Fraction of heavy atoms inside the expanded pocket bounds. */
        POCKET_BOUNDS,

        /** Fraction of heavy atoms near any pocket-residue heavy atom. */
        RESIDUE_ATOMS
    }

    public PosePocketMetrics {
        Objects.requireNonNull(pocket, "pocket");
        Objects.requireNonNull(basis, "basis");

        if (!Double.isFinite(ligandCentroidDistance)
                || ligandCentroidDistance < 0.0) {
            throw new IllegalArgumentException(
                    "ligandCentroidDistance must be finite and "
                            + "non-negative"
            );
        }

        validateUnitInterval(
                atomContainmentFraction,
                "atomContainmentFraction"
        );
        validateUnitInterval(
                contactResidueCoverage,
                "contactResidueCoverage"
        );
        validateUnitInterval(
                pocketContactCoverage,
                "pocketContactCoverage"
        );
        validateUnitInterval(
                centroidProximity,
                "centroidProximity"
        );
    }

    private static void validateUnitInterval(
            double value,
            String fieldName
    ) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    fieldName + " must be between 0 and 1"
            );
        }
    }
}
