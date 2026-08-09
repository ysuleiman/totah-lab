package totah.lab.athena.ligand.selectivity;

/**
 * Thresholds for {@link PoseReferenceSimilarityAnalyzer}. All values
 * are <b>calibration-pending</b>: they encode the current best guess
 * and must not be tuned toward an expected outcome.
 *
 * <p>{@code shiftTieBandAngstroms} and
 * {@code contactSimilarityTieBand} define the tie/ambiguity band:
 * differences within the band do not count as evidence for either
 * reference. {@code largePoseChangeAngstroms} is the
 * different-from-both threshold: when the mutant's centroid shift
 * exceeds it against BOTH references, the pose matches neither.
 */
public record PoseReferenceSimilarityOptions(
        double shiftTieBandAngstroms,
        double contactSimilarityTieBand,
        double largePoseChangeAngstroms
) {

    public PoseReferenceSimilarityOptions {
        if (!Double.isFinite(shiftTieBandAngstroms)
                || shiftTieBandAngstroms < 0.0) {
            throw new IllegalArgumentException(
                    "shiftTieBandAngstroms must be finite and "
                            + "non-negative"
            );
        }

        if (!Double.isFinite(contactSimilarityTieBand)
                || contactSimilarityTieBand < 0.0
                || contactSimilarityTieBand > 1.0) {
            throw new IllegalArgumentException(
                    "contactSimilarityTieBand must be between 0 and 1"
            );
        }

        if (!Double.isFinite(largePoseChangeAngstroms)
                || largePoseChangeAngstroms <= 0.0) {
            throw new IllegalArgumentException(
                    "largePoseChangeAngstroms must be finite and "
                            + "greater than zero"
            );
        }
    }

    public static PoseReferenceSimilarityOptions defaults() {
        return new PoseReferenceSimilarityOptions(
                0.5,
                0.1,
                5.0
        );
    }
}
