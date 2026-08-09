package totah.lab.athena.pocket.compare.residue;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Scores the residue chemistry of a pocket correspondence and gates
 * geometric similarity candidates on it.
 *
 * <p>The chemistry similarity is a weighted score over the matched
 * residue pairs: {@link MatchType#IDENTICAL} counts 1.00,
 * {@link MatchType#CONSERVATIVE} 0.70, and
 * {@link MatchType#CHEMISTRY_COMPATIBLE} 0.80; spatial replacements
 * ({@link MatchType#DIFFERENT}) count nothing. The coverage-adjusted
 * similarity scales the chemistry similarity by the geometric mean of
 * the query and candidate residue coverage, so a high chemistry score
 * over a small matched subset cannot dominate.</p>
 *
 * <p>The chemistry gate and the classification thresholds are
 * calibration-pending constants: they encode the current best guess
 * and are expected to move once calibrated against known binders.</p>
 */
public final class ResidueChemistryScorer {

    /**
     * Final similarity at or above which a chemistry-passing candidate
     * is classified {@link PocketSimilarityClassification#STRONG_SIMILARITY}.
     * Calibration-pending.
     */
    public static final double STRONG_THRESHOLD = 0.60;

    /**
     * Final similarity at or above which a chemistry-passing candidate
     * is classified
     * {@link PocketSimilarityClassification#MODERATE_SIMILARITY};
     * below it the candidate is
     * {@link PocketSimilarityClassification#REJECTED}.
     * Calibration-pending.
     */
    public static final double MODERATE_THRESHOLD = 0.40;

    private static final double IDENTICAL_WEIGHT = 1.00;
    private static final double CONSERVATIVE_WEIGHT = 0.70;
    private static final double CHEMISTRY_COMPATIBLE_WEIGHT = 0.80;

    // Chemistry gate thresholds (calibration-pending).
    private static final double MINIMUM_CHEMISTRY_SIMILARITY = 0.40;
    private static final double MINIMUM_COMPATIBLE_MATCHED_FRACTION =
            0.40;
    private static final double MAXIMUM_SPATIAL_REPLACEMENT_FRACTION =
            0.50;

    // Final similarity blend weights (calibration-pending).
    private static final double GEOMETRIC_WEIGHT = 0.45;
    private static final double CHEMISTRY_WEIGHT = 0.40;
    private static final double KEY_RESIDUE_WEIGHT = 0.15;

    /**
     * Assesses the residue chemistry of a correspondence. Key residues
     * are matched against the query residue name and number,
     * uppercased (for example {@code "CYS202"}); an empty key set
     * yields a key similarity of {@code 0.0}.
     */
    public ResidueChemistryAssessment assess(
            ResidueCorrespondence correspondence,
            Set<String> keyResidues
    ) {
        Objects.requireNonNull(correspondence, "correspondence");
        Objects.requireNonNull(keyResidues, "keyResidues");

        int identicalCount = 0;
        int conservativeCount = 0;
        int chemistryCompatibleCount = 0;
        int spatialReplacementCount = 0;
        double keyWeightSum = 0.0;
        int keyMatchedCount = 0;

        for (ResidueMatch match : correspondence.matches()) {
            double weight = chemistryWeight(match.matchType());

            switch (match.matchType()) {
                case IDENTICAL -> identicalCount++;
                case CONSERVATIVE -> conservativeCount++;
                case CHEMISTRY_COMPATIBLE -> chemistryCompatibleCount++;
                case DIFFERENT -> spatialReplacementCount++;
                default -> {
                    // UNMATCHED never appears in a match list.
                }
            }

            if (!keyResidues.isEmpty()
                    && keyResidues.contains(keyLabel(match))) {
                keyWeightSum += weight;
                keyMatchedCount++;
            }
        }

        int matchedResidueCount = correspondence.matches().size();
        int queryResidueCount = matchedResidueCount
                + correspondence.unmatchedQuery().size();
        int candidateResidueCount = matchedResidueCount
                + correspondence.unmatchedCandidate().size();

        int chemicallyAcceptable = identicalCount
                + conservativeCount
                + chemistryCompatibleCount;

        double chemistrySimilarity = matchedResidueCount == 0
                ? 0.0
                : (IDENTICAL_WEIGHT * identicalCount
                        + CONSERVATIVE_WEIGHT * conservativeCount
                        + CHEMISTRY_COMPATIBLE_WEIGHT
                                * chemistryCompatibleCount)
                        / matchedResidueCount;

        double queryResidueCoverage = queryResidueCount == 0
                ? 0.0
                : (double) matchedResidueCount / queryResidueCount;
        double candidateResidueCoverage = candidateResidueCount == 0
                ? 0.0
                : (double) matchedResidueCount / candidateResidueCount;
        double residueCoverage = Math.sqrt(
                queryResidueCoverage * candidateResidueCoverage
        );

        return new ResidueChemistryAssessment(
                chemistrySimilarity,
                chemistrySimilarity * residueCoverage,
                matchedResidueCount == 0
                        ? 0.0
                        : (double) chemicallyAcceptable
                                / matchedResidueCount,
                matchedResidueCount == 0
                        ? 1.0
                        : (double) spatialReplacementCount
                                / matchedResidueCount,
                identicalCount,
                conservativeCount,
                chemistryCompatibleCount,
                spatialReplacementCount,
                matchedResidueCount,
                queryResidueCount,
                candidateResidueCount,
                keyMatchedCount == 0
                        ? 0.0
                        : keyWeightSum / keyMatchedCount,
                keyMatchedCount
        );
    }

    /**
     * The chemistry gate: a candidate must show a minimum weighted
     * chemistry similarity, a minimum fraction of chemically
     * acceptable matches, and at most half spatial replacements.
     */
    public boolean passesChemistry(ResidueChemistryAssessment assessment) {
        Objects.requireNonNull(assessment, "assessment");

        return assessment.chemistrySimilarity()
                >= MINIMUM_CHEMISTRY_SIMILARITY
                && assessment.compatibleMatchedFraction()
                        >= MINIMUM_COMPATIBLE_MATCHED_FRACTION
                && assessment.spatialReplacementFraction()
                        <= MAXIMUM_SPATIAL_REPLACEMENT_FRACTION;
    }

    /**
     * Classifies a candidate from its chemistry assessment and the
     * blended final similarity. Candidates failing the chemistry gate
     * are {@link PocketSimilarityClassification#SHAPE_ONLY_NEIGHBOR}
     * regardless of the final similarity.
     */
    public PocketSimilarityClassification classify(
            ResidueChemistryAssessment assessment,
            double finalSimilarity
    ) {
        Objects.requireNonNull(assessment, "assessment");

        if (!passesChemistry(assessment)) {
            return PocketSimilarityClassification.SHAPE_ONLY_NEIGHBOR;
        }

        if (finalSimilarity >= STRONG_THRESHOLD) {
            return PocketSimilarityClassification.STRONG_SIMILARITY;
        }

        if (finalSimilarity >= MODERATE_THRESHOLD) {
            return PocketSimilarityClassification.MODERATE_SIMILARITY;
        }

        return PocketSimilarityClassification.REJECTED;
    }

    /**
     * Blends geometric and chemistry similarity into the final ranking
     * score: 45% geometric overall similarity, 40% coverage-adjusted
     * chemistry similarity, 15% key-residue chemistry similarity.
     */
    public static double finalSimilarity(
            double geometricOverallSimilarity,
            ResidueChemistryAssessment assessment
    ) {
        Objects.requireNonNull(assessment, "assessment");

        return GEOMETRIC_WEIGHT * geometricOverallSimilarity
                + CHEMISTRY_WEIGHT
                        * assessment.chemistryCoverageAdjustedSimilarity()
                + KEY_RESIDUE_WEIGHT
                        * assessment.keyResidueChemistrySimilarity();
    }

    /**
     * Canonical per-pair chemistry contribution used by aggregate and
     * evidence calculations.
     */
    public static double chemistryWeight(MatchType matchType) {
        Objects.requireNonNull(matchType, "matchType");
        return switch (matchType) {
            case IDENTICAL -> IDENTICAL_WEIGHT;
            case CONSERVATIVE -> CONSERVATIVE_WEIGHT;
            case CHEMISTRY_COMPATIBLE -> CHEMISTRY_COMPATIBLE_WEIGHT;
            default -> 0.0;
        };
    }

    private static String keyLabel(ResidueMatch match) {
        ResidueReference reference = match.query().reference();

        return (reference.residueName().trim()
                + reference.residueNumber())
                .toUpperCase(Locale.ROOT);
    }
}
