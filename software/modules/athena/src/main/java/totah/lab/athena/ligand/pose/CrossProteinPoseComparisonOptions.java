package totah.lab.athena.ligand.pose;

/**
 * Thresholds for cross-protein pose comparison. All values are
 * <b>calibration-pending</b>: they encode the current best guess and
 * must not be tuned toward an expected outcome.
 *
 * <p>{@code homologySimilarityThreshold} applies to
 * {@code PocketComparison.overallSimilarity} (unit interval; 0.85
 * geometry + 0.15 size under
 * {@code PocketComparisonOptions.defaults()}). The default 0.3 sits
 * below the known structurally homologous METTL7A/METTL7B pocket pair,
 * which scores geometry ~0.265 and size ~0.82 after sequence-seeded
 * alignment (overall ~0.35), and far above what dissimilar pockets
 * reach after rigid alignment; identical pockets score ~1.0.
 */
public record CrossProteinPoseComparisonOptions(
        double homologySimilarityThreshold,
        double sameSiteCentroidDistanceAngstroms
) {

    public CrossProteinPoseComparisonOptions {
        if (!Double.isFinite(homologySimilarityThreshold)
                || homologySimilarityThreshold < 0.0
                || homologySimilarityThreshold > 1.0) {
            throw new IllegalArgumentException(
                    "homologySimilarityThreshold must be between 0 and 1"
            );
        }

        if (!Double.isFinite(sameSiteCentroidDistanceAngstroms)
                || sameSiteCentroidDistanceAngstroms <= 0.0) {
            throw new IllegalArgumentException(
                    "sameSiteCentroidDistanceAngstroms must be finite "
                            + "and greater than zero"
            );
        }
    }

    public static CrossProteinPoseComparisonOptions defaults() {
        return new CrossProteinPoseComparisonOptions(
                0.3,
                3.0
        );
    }
}
