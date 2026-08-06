package totah.lab.athena.pocket.evidence;

import totah.lab.athena.pocket.compare.AlignmentInitialization;
import totah.lab.athena.pocket.compare.MultiHypothesisPocketAligner;
import totah.lab.athena.pocket.compare.PocketAlignmentResult;
import totah.lab.athena.pocket.compare.SeededAlignmentEvaluation;
import totah.lab.athena.pocket.compare.residue.ResidueChemistryScorer;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Builds {@link PocketAlignmentEvidence} from a
 * {@link PocketAlignmentResult}, preserving BOTH hypotheses with
 * their real metrics: the PCA+ICP hypothesis is always the first
 * entry of {@link PocketAlignmentResult#hypotheses()}; the
 * sequence-seeded hypothesis is the second entry when present and
 * {@link AlignmentHypothesisEvidence#unavailable()} otherwise (a
 * hypothesis that was never computed is reported as unavailable, not
 * with fabricated metrics).
 *
 * <p>The selection reason is a human-readable description of the
 * decisive criterion of the aligner's lexicographic selection
 * (geometric acceptance gate, geometry tolerance, sequence
 * consistency, chemistry, bidirectional distance, production
 * fallback). It is descriptive, not normative: the selection itself
 * is made by the aligner.</p>
 */
public final class PocketAlignmentEvidenceFactory {

    private final ResidueChemistryScorer chemistryScorer =
            new ResidueChemistryScorer();

    public PocketAlignmentEvidence create(PocketAlignmentResult result) {
        Objects.requireNonNull(result, "result");

        SeededAlignmentEvaluation pca = result.hypotheses().getFirst();

        AlignmentHypothesisEvidence pcaEvidence = hypothesisEvidence(
                pca,
                result.initialization()
        );

        AlignmentHypothesisEvidence seededEvidence =
                result.hypotheses().size() > 1
                        ? hypothesisEvidence(
                                result.hypotheses().get(1),
                                result.initialization()
                        )
                        : AlignmentHypothesisEvidence.unavailable();

        return new PocketAlignmentEvidence(
                pcaEvidence,
                seededEvidence,
                result.initialization(),
                selectionReason(result)
        );
    }

    private static AlignmentHypothesisEvidence hypothesisEvidence(
            SeededAlignmentEvaluation evaluation,
            AlignmentInitialization selectedInitialization
    ) {
        return new AlignmentHypothesisEvidence(
                true,
                evaluation.initialization() == selectedInitialization,
                evaluation.comparison().geometrySimilarity(),
                evaluation.comparison().queryCoverage(),
                evaluation.comparison().candidateCoverage(),
                evaluation.comparison().queryToCandidateMeanDistance(),
                evaluation.comparison().candidateToQueryMeanDistance(),
                evaluation.comparison().meanBidirectionalDistance(),
                evaluation.comparison()
                        .maximumNearestNeighborDistance(),
                evaluation.sequenceConsistentCorrespondenceCount(),
                evaluation.correspondence().matches().size()
        );
    }

    private String selectionReason(PocketAlignmentResult result) {
        if (result.hypotheses().size() == 1) {
            if (result.sequenceSeedDegenerate()) {
                return "PCA_ICP selected: the sequence seed was"
                        + " geometrically degenerate, so the"
                        + " production PCA+ICP fallback was retained";
            }

            return "PCA_ICP selected: no usable sequence seed, so"
                    + " PCA+ICP was the only evaluated hypothesis";
        }

        SeededAlignmentEvaluation pca = result.hypotheses().get(0);
        SeededAlignmentEvaluation seeded = result.hypotheses().get(1);

        String selectedName = name(result.initialization());
        String otherName = name(other(result.initialization()));

        if (pca.geometryAcceptable() != seeded.geometryAcceptable()) {
            return selectedName + " selected: the " + otherName
                    + " hypothesis failed the geometric acceptance"
                    + " gate (symmetric coverage or mean bidirectional"
                    + " distance)";
        }

        if (!pca.geometryAcceptable()) {
            return selectedName + " selected: both hypotheses failed"
                    + " the geometric acceptance gate; the production"
                    + " PCA+ICP fallback was retained";
        }

        double geometryDifference = Math.abs(
                pca.comparison().geometrySimilarity()
                        - seeded.comparison().geometrySimilarity()
        );

        if (geometryDifference
                > MultiHypothesisPocketAligner.GEOMETRY_TOLERANCE) {
            return String.format(
                    Locale.ROOT,
                    "%s selected: better geometry (%.3f vs %.3f,"
                            + " beyond the %.2f tolerance)",
                    selectedName,
                    selected(pca, seeded, result).comparison()
                            .geometrySimilarity(),
                    other(pca, seeded, result).comparison()
                            .geometrySimilarity(),
                    MultiHypothesisPocketAligner.GEOMETRY_TOLERANCE
            );
        }

        if (pca.sequenceConsistentCorrespondenceFraction()
                != seeded.sequenceConsistentCorrespondenceFraction()) {
            return String.format(
                    Locale.ROOT,
                    "%s selected: equivalent geometry (difference"
                            + " %.3f within the %.2f tolerance) and"
                            + " higher sequence consistency"
                            + " (%.2f vs %.2f)",
                    selectedName,
                    geometryDifference,
                    MultiHypothesisPocketAligner.GEOMETRY_TOLERANCE,
                    selected(pca, seeded, result)
                            .sequenceConsistentCorrespondenceFraction(),
                    other(pca, seeded, result)
                            .sequenceConsistentCorrespondenceFraction()
            );
        }

        double pcaChemistry = chemistrySimilarity(pca);
        double seededChemistry = chemistrySimilarity(seeded);

        if (pcaChemistry != seededChemistry) {
            double selectedChemistry =
                    selected(pca, seeded, result).initialization()
                            == pca.initialization()
                            ? pcaChemistry
                            : seededChemistry;
            double otherChemistry =
                    selected(pca, seeded, result).initialization()
                            == pca.initialization()
                            ? seededChemistry
                            : pcaChemistry;

            return String.format(
                    Locale.ROOT,
                    "%s selected: equivalent geometry and sequence"
                            + " consistency; higher chemistry"
                            + " (%.3f vs %.3f)",
                    selectedName,
                    selectedChemistry,
                    otherChemistry
            );
        }

        if (pca.comparison().meanBidirectionalDistance()
                != seeded.comparison().meanBidirectionalDistance()) {
            return selectedName + " selected: equivalent geometry,"
                    + " sequence consistency and chemistry; lower"
                    + " mean bidirectional distance";
        }

        return "PCA_ICP selected: perfect tie between the hypotheses;"
                + " the production hypothesis was retained";
    }

    private double chemistrySimilarity(
            SeededAlignmentEvaluation evaluation
    ) {
        return chemistryScorer.assess(
                evaluation.correspondence(),
                Set.of()
        ).chemistrySimilarity();
    }

    private static SeededAlignmentEvaluation selected(
            SeededAlignmentEvaluation pca,
            SeededAlignmentEvaluation seeded,
            PocketAlignmentResult result
    ) {
        return result.initialization() == pca.initialization()
                ? pca
                : seeded;
    }

    private static SeededAlignmentEvaluation other(
            SeededAlignmentEvaluation pca,
            SeededAlignmentEvaluation seeded,
            PocketAlignmentResult result
    ) {
        return result.initialization() == pca.initialization()
                ? seeded
                : pca;
    }

    private static AlignmentInitialization other(
            AlignmentInitialization initialization
    ) {
        return initialization == AlignmentInitialization.PCA_ICP
                ? AlignmentInitialization.SEQUENCE_SEEDED_KABSCH
                : AlignmentInitialization.PCA_ICP;
    }

    private static String name(AlignmentInitialization initialization) {
        return initialization == AlignmentInitialization.PCA_ICP
                ? "PCA_ICP"
                : "sequence-seeded";
    }
}
