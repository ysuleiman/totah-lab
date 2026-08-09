package totah.lab.athena.pocket.architecture;

import totah.lab.gaia.geometry.RigidTransform;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Objects;

/**
 * Backbone-level structural comparison of two receptors, aligned by a
 * sequence-seeded Kabsch fit of the CA atoms of all aligned residue
 * pairs (B onto A), with the displacement metrics reported over the
 * pocket region (aligned pairs where either residue belongs to its
 * pocket).
 *
 * <p>{@code caRmsd} is the RMS of per-residue CA displacements over
 * the pocket-region pairs; {@code backboneRmsd} and
 * {@code heavyAtomRmsd} are atom-level RMS values over same-named
 * backbone (N/CA/C/O) respectively all heavy atoms present on both
 * residues of a pocket-region pair. {@code displacementProfile} lists
 * the pocket-region pairs sorted by CA displacement descending, so a
 * report can name the exact residues that moved.
 * {@code segmentProfile} groups ALL aligned CA pairs into contiguous
 * segments (consecutive residue numbers on both sides) and reports
 * the mean CA displacement per segment, sorted descending — this
 * localizes differences to regions such as loops without inventing a
 * secondary-structure assignment.
 */
public record BackboneArchitectureComparison(
        RigidTransform transformBtoA,
        int fittedResiduePairs,
        int pocketRegionResiduePairs,
        double caRmsd,
        double backboneRmsd,
        double heavyAtomRmsd,
        List<ResidueDisplacement> displacementProfile,
        List<SegmentDisplacement> segmentProfile
) {

    /**
     * Displacement of one aligned residue pair after the backbone
     * alignment, in angstroms.
     */
    public record ResidueDisplacement(
            ResidueId residueA,
            ResidueId residueB,
            String residueNameA,
            String residueNameB,
            double caDisplacement,
            double backboneDisplacement,
            double heavyAtomDisplacement
    ) {
    }

    /**
     * Mean CA displacement over one contiguous aligned segment.
     * Residue ranges are inclusive, in receptor numbering.
     */
    public record SegmentDisplacement(
            int startResidueA,
            int endResidueA,
            int startResidueB,
            int endResidueB,
            int length,
            double meanCaDisplacement
    ) {
    }

    public BackboneArchitectureComparison {
        Objects.requireNonNull(transformBtoA, "transformBtoA");
        displacementProfile = List.copyOf(
                Objects.requireNonNull(
                        displacementProfile,
                        "displacementProfile"
                )
        );
        segmentProfile = List.copyOf(
                Objects.requireNonNull(
                        segmentProfile,
                        "segmentProfile"
                )
        );
    }
}
