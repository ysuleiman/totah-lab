package totah.lab.athena.pocket.compare.residue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Scores graded amino-acid substitution similarity for a pocket
 * residue correspondence, complementing the discrete
 * {@link ResidueChemistryScorer}: where the chemistry scorer buckets
 * matched pairs into match types, this scorer assigns every matched
 * pair a continuous similarity derived from the BLOSUM62 substitution
 * matrix. It never feeds the chemistry gate or the final similarity
 * blend.
 *
 * <p>The correspondence input is already expressed in the aligned
 * frame produced by Stage 2; no alignment work happens here.</p>
 *
 * <p>Normalization: raw BLOSUM62 integer scores are mapped linearly
 * onto {@code [0, 1]} via
 * {@code (score - MINIMUM_SCORE) / (MAXIMUM_SCORE - MINIMUM_SCORE)},
 * where the bounds are the matrix's global minimum ({@code -4}) and
 * maximum ({@code 11}, the W-W self-score). Identity pairs go through
 * the same map, so they are not forced to {@code 1.0}: BLOSUM62
 * diagonal values differ on purpose (W-W is 11 while A-A is 4),
 * reflecting how conserved each residue tends to be.</p>
 */
public final class ResidueSubstitutionScorer {

    /**
     * Similarity reported when either residue name is not a standard
     * amino acid.
     */
    public static final double UNKNOWN_SIMILARITY = 0.0;

    // Row/column order of the BLOSUM62 matrix below.
    private static final String AMINO_ACIDS = "ARNDCQEGHILKMFPSTWYV";

    private static final int MINIMUM_SCORE = -4;
    private static final int MAXIMUM_SCORE = 11;

    // BLOSUM62 substitution matrix (Henikoff & Henikoff, PNAS 1992),
    // standard 20x20 integer scores in the AMINO_ACIDS order.
    private static final int[][] BLOSUM62 = {
        { 4, -1, -2, -2,  0, -1, -1,  0, -2, -1, -1, -1, -1, -2, -1,  1,  0, -3, -2,  0},
        {-1,  5,  0, -2, -3,  1,  0, -2,  0, -3, -2,  2, -1, -3, -2, -1, -1, -3, -2, -3},
        {-2,  0,  6,  1, -3,  0,  0,  0,  1, -3, -3,  0, -2, -3, -2,  1,  0, -4, -2, -3},
        {-2, -2,  1,  6, -3,  0,  2, -1, -1, -3, -4, -1, -3, -3, -1,  0, -1, -4, -3, -3},
        { 0, -3, -3, -3,  9, -3, -4, -3, -3, -1, -1, -3, -1, -2, -3, -1, -1, -2, -2, -1},
        {-1,  1,  0,  0, -3,  5,  2, -2,  0, -3, -2,  1,  0, -3, -1,  0, -1, -2, -1, -2},
        {-1,  0,  0,  2, -4,  2,  5, -2,  0, -3, -3,  1, -2, -3, -1,  0, -1, -3, -2, -2},
        { 0, -2,  0, -1, -3, -2, -2,  6, -2, -4, -4, -2, -3, -3, -2,  0, -2, -2, -3, -3},
        {-2,  0,  1, -1, -3,  0,  0, -2,  8, -3, -3, -1, -2, -1, -2, -1, -2, -2,  2, -3},
        {-1, -3, -3, -3, -1, -3, -3, -4, -3,  4,  2, -3,  1,  0, -3, -2, -1, -3, -1,  3},
        {-1, -2, -3, -4, -1, -2, -3, -4, -3,  2,  4, -2,  2,  0, -3, -2, -1, -2, -1,  1},
        {-1,  2,  0, -1, -3,  1,  1, -2, -1, -3, -2,  5, -1, -3, -1,  0, -1, -3, -2, -2},
        {-1, -1, -2, -3, -1,  0, -2, -3, -2,  1,  2, -1,  5,  0, -2, -1, -1, -1, -1,  1},
        {-2, -3, -3, -3, -2, -3, -3, -3, -1,  0,  0, -3,  0,  6, -4, -2, -2,  1,  3, -1},
        {-1, -2, -2, -1, -3, -1, -1, -2, -2, -3, -3, -1, -2, -4,  7, -1, -1, -4, -3, -2},
        { 1, -1,  1,  0, -1,  0,  0,  0, -1, -2, -2,  0, -1, -2, -1,  4,  1, -3, -2, -2},
        { 0, -1,  0, -1, -1, -1, -1, -2, -2, -1, -1, -1, -1, -2, -1,  1,  5, -2, -2,  0},
        {-3, -3, -4, -4, -2, -2, -3, -2, -2, -3, -2, -3, -1,  1, -4, -3, -2, 11,  2, -3},
        {-2, -2, -2, -3, -2, -1, -2, -3,  2, -1, -1, -2, -1,  3, -3, -2, -2,  2,  7, -1},
        { 0, -3, -3, -3, -1, -2, -2, -3, -3,  3,  1, -2,  1, -1, -2, -2,  0, -3, -1,  4}
    };

    private static final Map<String, Integer> INDEX_BY_THREE_LETTER =
            indexByThreeLetterName();

    /**
     * Returns the normalized BLOSUM62 similarity in {@code [0, 1]} for
     * a pair of residues given by three-letter names (any case,
     * surrounding whitespace ignored). Unknown names yield
     * {@link #UNKNOWN_SIMILARITY}.
     */
    public double similarity(
            String queryResidueName,
            String candidateResidueName
    ) {
        Integer queryIndex = indexOf(queryResidueName);
        Integer candidateIndex = indexOf(candidateResidueName);

        if (queryIndex == null || candidateIndex == null) {
            return UNKNOWN_SIMILARITY;
        }

        return normalize(BLOSUM62[queryIndex][candidateIndex]);
    }

    /**
     * Assesses the substitution similarity of an aligned
     * correspondence: one normalized similarity per matched pair (in
     * match order), their mean, and the identical-match fraction. The
     * residue coverage fractions pass through unchanged from the
     * correspondence.
     */
    public ResidueSubstitutionAssessment assess(
            ResidueCorrespondence correspondence
    ) {
        Objects.requireNonNull(correspondence, "correspondence");

        List<Double> matchSimilarities = new ArrayList<>(
                correspondence.matches().size()
        );
        int identicalCount = 0;
        double similaritySum = 0.0;

        for (ResidueMatch match : correspondence.matches()) {
            double similarity = similarity(
                    match.query().reference().residueName(),
                    match.candidate().reference().residueName()
            );
            matchSimilarities.add(similarity);
            similaritySum += similarity;

            if (match.identicalResidue()) {
                identicalCount++;
            }
        }

        int matchedResidueCount = correspondence.matches().size();

        return new ResidueSubstitutionAssessment(
                matchSimilarities,
                matchedResidueCount == 0
                        ? 0.0
                        : similaritySum / matchedResidueCount,
                matchedResidueCount == 0
                        ? 0.0
                        : (double) identicalCount / matchedResidueCount,
                matchedResidueCount,
                correspondence.matchedFractionQuery(),
                correspondence.matchedFractionCandidate()
        );
    }

    private static Integer indexOf(String residueName) {
        Objects.requireNonNull(residueName, "residueName");

        return INDEX_BY_THREE_LETTER.get(
                residueName.trim().toUpperCase(Locale.ROOT)
        );
    }

    private static double normalize(int score) {
        return (double) (score - MINIMUM_SCORE)
                / (MAXIMUM_SCORE - MINIMUM_SCORE);
    }

    private static Map<String, Integer> indexByThreeLetterName() {
        String[] threeLetterNames = {
                "ALA", "ARG", "ASN", "ASP", "CYS",
                "GLN", "GLU", "GLY", "HIS", "ILE",
                "LEU", "LYS", "MET", "PHE", "PRO",
                "SER", "THR", "TRP", "TYR", "VAL"
        };

        Map<String, Integer> index = new HashMap<>();
        for (int position = 0;
             position < threeLetterNames.length;
             position++) {
            index.put(threeLetterNames[position], position);
        }

        return Map.copyOf(index);
    }
}
