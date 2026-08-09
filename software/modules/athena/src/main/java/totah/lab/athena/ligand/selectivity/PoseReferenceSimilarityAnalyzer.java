package totah.lab.athena.ligand.selectivity;

import java.util.Objects;

/**
 * Combines two {@link MutationPoseComparison}s of the same mutant
 * pose — one against the WT-A reference (same frame) and one against
 * the WT-B reference (already transformed into the A frame by the
 * caller via the cross-protein comparator) — into a
 * {@link PoseReferenceSimilarity}. This class performs no pocket
 * alignment itself; the cross-frame transform is a caller
 * responsibility.
 *
 * <p>Classification is deterministic and uses only centroid shift and
 * contact-set Jaccard, in this order:</p>
 * <ol>
 *   <li>both shifts above {@code largePoseChangeAngstroms} &rarr;
 *       {@link PoseSimilarityClassification#DIFFERENT_FROM_BOTH};</li>
 *   <li>both metrics favor A beyond their tie bands &rarr;
 *       {@link PoseSimilarityClassification#MORE_A_LIKE}
 *       (symmetrically for B);</li>
 *   <li>both metrics inside their tie bands &rarr;
 *       {@link PoseSimilarityClassification#AMBIGUOUS};</li>
 *   <li>otherwise (one metric ties, or the metrics disagree) &rarr;
 *       {@link PoseSimilarityClassification#INTERMEDIATE}.</li>
 * </ol>
 */
public final class PoseReferenceSimilarityAnalyzer {

    private final PoseReferenceSimilarityOptions options;

    public PoseReferenceSimilarityAnalyzer() {
        this(PoseReferenceSimilarityOptions.defaults());
    }

    public PoseReferenceSimilarityAnalyzer(
            PoseReferenceSimilarityOptions options
    ) {
        this.options = Objects.requireNonNull(options, "options");
    }

    public PoseReferenceSimilarity summarize(
            MutationPoseComparison mutantVersusA,
            MutationPoseComparison mutantVersusB
    ) {
        Objects.requireNonNull(mutantVersusA, "mutantVersusA");
        Objects.requireNonNull(mutantVersusB, "mutantVersusB");

        double shiftA = mutantVersusA.alignedLigandCentroidShift();
        double shiftB = mutantVersusB.alignedLigandCentroidShift();
        double jaccardA = mutantVersusA.contactSetJaccard();
        double jaccardB = mutantVersusB.contactSetJaccard();

        PoseSimilarityClassification classification;
        String reason;

        if (shiftA > options.largePoseChangeAngstroms()
                && shiftB > options.largePoseChangeAngstroms()) {
            classification =
                    PoseSimilarityClassification.DIFFERENT_FROM_BOTH;
            reason = String.format(
                    "centroid shifts %.2f A (to A) and %.2f A (to B) "
                            + "both exceed the large-pose-change "
                            + "threshold %.2f A",
                    shiftA,
                    shiftB,
                    options.largePoseChangeAngstroms()
            );
        } else {
            boolean shiftFavorsA = shiftB - shiftA
                    > options.shiftTieBandAngstroms();
            boolean shiftFavorsB = shiftA - shiftB
                    > options.shiftTieBandAngstroms();
            boolean jaccardFavorsA = jaccardA - jaccardB
                    > options.contactSimilarityTieBand();
            boolean jaccardFavorsB = jaccardB - jaccardA
                    > options.contactSimilarityTieBand();

            if (shiftFavorsA && jaccardFavorsA) {
                classification =
                        PoseSimilarityClassification.MORE_A_LIKE;
                reason = String.format(
                        "closer to A on both metrics: shift %.2f A "
                                + "vs %.2f A, contact similarity %.2f "
                                + "vs %.2f",
                        shiftA,
                        shiftB,
                        jaccardA,
                        jaccardB
                );
            } else if (shiftFavorsB && jaccardFavorsB) {
                classification =
                        PoseSimilarityClassification.MORE_B_LIKE;
                reason = String.format(
                        "closer to B on both metrics: shift %.2f A "
                                + "vs %.2f A, contact similarity %.2f "
                                + "vs %.2f",
                        shiftA,
                        shiftB,
                        jaccardA,
                        jaccardB
                );
            } else if (!shiftFavorsA && !shiftFavorsB
                    && !jaccardFavorsA && !jaccardFavorsB) {
                classification = PoseSimilarityClassification.AMBIGUOUS;
                reason = String.format(
                        "both metrics within their tie bands: shift "
                                + "%.2f A vs %.2f A, contact "
                                + "similarity %.2f vs %.2f",
                        shiftA,
                        shiftB,
                        jaccardA,
                        jaccardB
                );
            } else {
                classification =
                        PoseSimilarityClassification.INTERMEDIATE;
                reason = String.format(
                        "mixed evidence: shift %.2f A vs %.2f A, "
                                + "contact similarity %.2f vs %.2f",
                        shiftA,
                        shiftB,
                        jaccardA,
                        jaccardB
                );
            }
        }

        return new PoseReferenceSimilarity(
                mutantVersusA.alignedLigandRmsd(),
                shiftA,
                jaccardA,
                mutantVersusB.alignedLigandRmsd(),
                shiftB,
                jaccardB,
                classification,
                reason
        );
    }
}
