package totah.lab.web.service;

/**
 * Summary statistics of a residue correspondence, mirroring the
 * aggregate values of Athena's {@code ResidueCorrespondence}.
 */
public record ResidueSummaryView(
        int queryResidueCount,
        int candidateResidueCount,
        int matchedCount,
        int unmatchedQueryCount,
        int unmatchedCandidateCount,
        double matchedFractionQuery,
        double matchedFractionCandidate,
        double identicalFraction,
        double chemistryCompatibleFraction,
        double meanMatchedDistance,
        double maximumMatchedDistance
) {
}
