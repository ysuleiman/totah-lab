package totah.lab.athena.sequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Global (Needleman-Wunsch) alignment of two protein sequences with
 * the scoring scheme of the METTL7 correspondence reference
 * implementation: match {@value #MATCH_SCORE}, mismatch
 * {@value #MISMATCH_SCORE}, and a linear gap penalty of
 * {@value #GAP_SCORE} per gapped residue (gap open equals gap
 * extend).
 *
 * <p>The traceback is deterministic: at each cell it prefers the
 * diagonal move, then a gap in the candidate sequence, then a gap in
 * the query sequence — exactly the reference tie-breaking. Residue
 * names are compared case-insensitively after trimming.</p>
 *
 * <p>Pure and deterministic: the same inputs always produce the same
 * alignment.</p>
 */
public final class NeedlemanWunschSequenceAligner {

    public static final int MATCH_SCORE = 2;
    public static final int MISMATCH_SCORE = -1;
    public static final int GAP_SCORE = -2;

    /**
     * Aligns {@code query} to {@code candidate}. The direction is
     * meaningful: returned pairs map query residue numbers onto
     * candidate residue numbers.
     */
    public SequenceAlignment align(
            List<SequenceResidue> query,
            List<SequenceResidue> candidate
    ) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(candidate, "candidate");

        if (query.isEmpty() || candidate.isEmpty()) {
            return SequenceAlignment.empty();
        }

        int queryLength = query.size();
        int candidateLength = candidate.size();

        int[][] score =
                new int[queryLength + 1][candidateLength + 1];

        for (int i = 1; i <= queryLength; i++) {
            score[i][0] = GAP_SCORE * i;
        }

        for (int j = 1; j <= candidateLength; j++) {
            score[0][j] = GAP_SCORE * j;
        }

        for (int i = 1; i <= queryLength; i++) {
            for (int j = 1; j <= candidateLength; j++) {
                int diagonal = score[i - 1][j - 1]
                        + substitutionScore(
                                query.get(i - 1),
                                candidate.get(j - 1)
                        );

                score[i][j] = Math.max(
                        diagonal,
                        Math.max(
                                score[i - 1][j] + GAP_SCORE,
                                score[i][j - 1] + GAP_SCORE
                        )
                );
            }
        }

        List<AlignedResiduePair> pairs = new ArrayList<>();

        int i = queryLength;
        int j = candidateLength;

        while (i > 0 && j > 0) {
            SequenceResidue queryResidue = query.get(i - 1);
            SequenceResidue candidateResidue = candidate.get(j - 1);

            int diagonal = score[i - 1][j - 1]
                    + substitutionScore(queryResidue, candidateResidue);

            if (score[i][j] == diagonal) {
                pairs.add(new AlignedResiduePair(
                        queryResidue.residueNumber(),
                        candidateResidue.residueNumber(),
                        queryResidue.residueName(),
                        candidateResidue.residueName()
                ));
                i--;
                j--;
            } else if (score[i][j] == score[i - 1][j] + GAP_SCORE) {
                i--;
            } else {
                j--;
            }
        }

        Collections.reverse(pairs);

        int identicalCount = 0;

        for (AlignedResiduePair pair : pairs) {
            if (sameResidue(
                    pair.queryResidueName(),
                    pair.candidateResidueName()
            )) {
                identicalCount++;
            }
        }

        double identity = pairs.isEmpty()
                ? 0.0
                : (double) identicalCount / pairs.size();

        return new SequenceAlignment(identity, pairs);
    }

    private static int substitutionScore(
            SequenceResidue queryResidue,
            SequenceResidue candidateResidue
    ) {
        return sameResidue(
                queryResidue.residueName(),
                candidateResidue.residueName()
        )
                ? MATCH_SCORE
                : MISMATCH_SCORE;
    }

    private static boolean sameResidue(String first, String second) {
        return first.trim().equalsIgnoreCase(second.trim());
    }
}
