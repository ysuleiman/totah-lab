package totah.lab.web.service;

import java.util.List;

/**
 * Development diagnostic row for one ranked candidate: the Stage 1
 * candidate information, the intermediate stage ranks, the Stage 2
 * shape distance, the full Stage 3 {@code PocketComparison} metrics
 * as produced by Athena, and the Stage 3 chemistry assessment with
 * the resulting classification and final similarity, plus the graded
 * BLOSUM62 substitution similarity (which never feeds the ranking),
 * the Stage 1 provenance and the PocketMatch channel evidence. Field names mirror
 * the Athena accessors one-to-one; no metric is recomputed in web-api.
 *
 * <p>{@code provenance} is the {@code CandidateProvenance} name of the
 * Stage 1 channel that first surfaced the candidate.
 * {@code pocketMatchQueryCoverage} and {@code pocketMatchRank} are the
 * PocketMatch channel's own evidence — they are never blended into the
 * descriptor distances or the Stage 3 ordering — and are {@code null}
 * when the channel is disabled or the candidate is not in the
 * channel's top-N. {@code pocketMatchSymmetricRank} and
 * {@code pocketMatchQueryCoverageRank} are the channel's two natural
 * orderings; {@code candidateSources} is the unioned channel
 * membership (a candidate can arrive through several channels);
 * {@code assessment} is the evidence-pipeline verdict name, or
 * {@code null} when evidence assembly is not wired.</p>
 */
public record PocketSimilarityDiagnostic(
        long pocketId,
        long structureId,
        String sourceAccession,
        int pocketNumber,
        int alphaSphereCount,
        int stageOneRank,
        double descriptorDistance,
        double volumeDistance,
        double residueDistance,
        double chemistryDistance,
        int stageTwoRank,
        double shapeDistance,
        int stageThreeRank,
        double geometricOverallSimilarity,
        double geometrySimilarity,
        double sizeSimilarity,
        double queryCoverage,
        double candidateCoverage,
        double queryToCandidateMeanDistance,
        double candidateToQueryMeanDistance,
        double meanBidirectionalDistance,
        double maximumNearestNeighborDistance,
        int queryPointCount,
        int candidatePointCount,
        String basis,
        double chemistrySimilarity,
        double chemistryCoverageAdjustedSimilarity,
        double compatibleMatchedFraction,
        double spatialReplacementFraction,
        int identicalCount,
        int conservativeCount,
        int chemistryCompatibleCount,
        int spatialReplacementCount,
        int matchedResidueCount,
        double keyResidueChemistrySimilarity,
        double meanSubstitutionSimilarity,
        String classification,
        double finalSimilarity,
        String uniProtId,
        String proteinName,
        String geneName,
        String organism,
        String alignmentInitialization,
        String provenance,
        Double pocketMatchQueryCoverage,
        Integer pocketMatchRank,
        Integer pocketMatchSymmetricRank,
        Integer pocketMatchQueryCoverageRank,
        List<String> candidateSources,
        String assessment
) {
}
