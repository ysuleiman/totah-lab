package totah.lab.athena.pocket.compare;

import totah.lab.athena.pocket.compare.residue.ResidueCorrespondence;

import java.util.Objects;

/**
 * Evaluation of one alignment hypothesis produced by
 * {@link MultiHypothesisPocketAligner}: the alignment itself, its
 * geometric comparison, its residue correspondence (computed with the
 * retained transform), and how much of that correspondence agrees with
 * the protein sequence alignment.
 *
 * @param alignment     the hypothesis alignment
 * @param initialization how the alignment was initialized
 * @param comparison    geometric comparison of the aligned clouds
 * @param correspondence greedy one-to-one residue correspondence under
 *                       the retained transform
 * @param seedPairCount number of sequence-aligned residue pairs used to
 *                      seed the alignment ({@code 0} for PCA_ICP)
 * @param sequenceConsistentCorrespondenceCount matched residue pairs
 *                       whose residue numbers form an aligned pair of
 *                       the sequence alignment
 * @param sequenceConsistentCorrespondenceFraction the same count as a
 *                       fraction of all matched residue pairs
 *                       ({@code 0.0} when nothing matched)
 * @param geometryAcceptable whether the hypothesis passes the geometric
 *                       acceptance gate (symmetric coverage and mean
 *                       bidirectional distance); rejected hypotheses
 *                       can never be selected
 */
public record SeededAlignmentEvaluation(
        PocketAlignment alignment,
        AlignmentInitialization initialization,
        PocketComparison comparison,
        ResidueCorrespondence correspondence,
        int seedPairCount,
        int sequenceConsistentCorrespondenceCount,
        double sequenceConsistentCorrespondenceFraction,
        boolean geometryAcceptable
) {

    public SeededAlignmentEvaluation {
        Objects.requireNonNull(alignment, "alignment");
        Objects.requireNonNull(initialization, "initialization");
        Objects.requireNonNull(comparison, "comparison");
        Objects.requireNonNull(correspondence, "correspondence");

        if (seedPairCount < 0) {
            throw new IllegalArgumentException(
                    "seedPairCount must be non-negative"
            );
        }

        if (sequenceConsistentCorrespondenceCount < 0) {
            throw new IllegalArgumentException(
                    "sequenceConsistentCorrespondenceCount"
                            + " must be non-negative"
            );
        }

        if (!Double.isFinite(sequenceConsistentCorrespondenceFraction)
                || sequenceConsistentCorrespondenceFraction < 0.0
                || sequenceConsistentCorrespondenceFraction > 1.0) {
            throw new IllegalArgumentException(
                    "sequenceConsistentCorrespondenceFraction"
                            + " must be within [0, 1]"
            );
        }
    }
}
