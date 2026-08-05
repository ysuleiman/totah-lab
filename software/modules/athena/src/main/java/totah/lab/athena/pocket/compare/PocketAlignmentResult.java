package totah.lab.athena.pocket.compare;

import totah.lab.athena.pocket.compare.residue.ResidueCorrespondence;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of a {@link MultiHypothesisPocketAligner} run: the SELECTED
 * hypothesis (its alignment, initialization, comparison and residue
 * correspondence) plus the sequence-seed diagnostics and the full list
 * of evaluated hypotheses for diagnostic inspection (the PCA+ICP
 * hypothesis is always first).
 *
 * @param alignment     the selected alignment
 * @param initialization how the selected alignment was initialized
 * @param seedPairCount number of sequence-aligned residue pairs that
 *                      seeded the selected alignment ({@code 0} for
 *                      PCA_ICP)
 * @param sequenceConsistentCorrespondenceCount matched residue pairs of
 *                      the selected hypothesis that agree with the
 *                      sequence alignment
 * @param sequenceConsistentCorrespondenceFraction the same count as a
 *                      fraction of all matched residue pairs
 * @param sequenceSeedAvailable whether a usable sequence seed existed
 *                      (sufficient identity and enough aligned residue
 *                      pairs present in both pockets)
 * @param sequenceSeedDegenerate whether an available seed failed
 *                      geometrically (fewer than three non-collinear
 *                      points or a Kabsch failure), forcing the PCA+ICP
 *                      fallback
 * @param comparison    geometric comparison of the selected hypothesis
 * @param correspondence residue correspondence of the selected
 *                      hypothesis
 * @param hypotheses    every evaluated hypothesis, PCA+ICP first;
 *                      contains a second entry when a sequence-seeded
 *                      hypothesis was evaluated (also when it lost)
 */
public record PocketAlignmentResult(
        PocketAlignment alignment,
        AlignmentInitialization initialization,
        int seedPairCount,
        int sequenceConsistentCorrespondenceCount,
        double sequenceConsistentCorrespondenceFraction,
        boolean sequenceSeedAvailable,
        boolean sequenceSeedDegenerate,
        PocketComparison comparison,
        ResidueCorrespondence correspondence,
        List<SeededAlignmentEvaluation> hypotheses
) {

    public PocketAlignmentResult {
        Objects.requireNonNull(alignment, "alignment");
        Objects.requireNonNull(initialization, "initialization");
        Objects.requireNonNull(comparison, "comparison");
        Objects.requireNonNull(correspondence, "correspondence");

        hypotheses = List.copyOf(
                Objects.requireNonNull(hypotheses, "hypotheses")
        );

        if (hypotheses.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least the PCA_ICP hypothesis must be present"
            );
        }
    }
}
