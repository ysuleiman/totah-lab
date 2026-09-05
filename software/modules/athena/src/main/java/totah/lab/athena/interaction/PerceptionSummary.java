package totah.lab.athena.interaction;

import totah.lab.athena.interaction.perception.PerceptionProvenance;

import java.util.Objects;

/**
 * Provenance summary of the perception run over one side of a profiling
 * call, so callers can see degraded input instead of trusting counts.
 *
 * @param side which side was perceived: {@code "receptor"},
 *             {@code "ligand"} or {@code "cofactor"}
 * @param hydrophobicProvenance how the hydrophobic atom set was derived
 * @param hydrophobicAtomCount number of perceived hydrophobic atoms
 * @param ringCount number of perceived aromatic rings
 * @param degradedRingCount how many of those rings carry a degraded
 *                          provenance (unknown topology)
 * @param chargedGroupCount number of perceived charged groups
 * @param degradedChargedGroupCount how many of those groups carry a
 *                                  degraded provenance
 */
public record PerceptionSummary(
        String side,
        PerceptionProvenance hydrophobicProvenance,
        int hydrophobicAtomCount,
        int ringCount,
        int degradedRingCount,
        int chargedGroupCount,
        int degradedChargedGroupCount) {

    /** Side label of the receptor structure. */
    public static final String RECEPTOR = "receptor";
    /** Side label of the ligand structure. */
    public static final String LIGAND = "ligand";
    /** Side label of a separately passed cofactor structure. */
    public static final String COFACTOR = "cofactor";

    public PerceptionSummary {
        if (side == null || side.isBlank()) {
            throw new IllegalArgumentException("side must not be blank");
        }
        side = side.trim();
        Objects.requireNonNull(
                hydrophobicProvenance, "hydrophobicProvenance");
        if (hydrophobicAtomCount < 0 || ringCount < 0
                || degradedRingCount < 0 || chargedGroupCount < 0
                || degradedChargedGroupCount < 0) {
            throw new IllegalArgumentException("counts must be non-negative");
        }
        if (degradedRingCount > ringCount
                || degradedChargedGroupCount > chargedGroupCount) {
            throw new IllegalArgumentException(
                    "degraded counts cannot exceed totals");
        }
    }

    /**
     * Returns {@code true} when any part of the perception relied on a
     * degraded fallback (AD4 typing, charge sums, or unknown ring
     * topology).
     */
    public boolean degraded() {
        return hydrophobicProvenance.isDegraded()
                || degradedRingCount > 0
                || degradedChargedGroupCount > 0;
    }
}
