package totah.lab.web.service;

/**
 * Development diagnostic row for one ranked candidate: the Stage 1
 * candidate information, the intermediate stage ranks, the Stage 2
 * shape distance, and the full Stage 3 {@code PocketComparison}
 * metrics as produced by Athena. Field names mirror the Athena
 * accessors one-to-one; no metric is recomputed in web-api.
 */
public record PocketSimilarityDiagnostic(
        long pocketId,
        long structureId,
        String sourceAccession,
        int pocketNumber,
        int stageOneRank,
        double descriptorDistance,
        double volumeDistance,
        double residueDistance,
        double chemistryDistance,
        int stageTwoRank,
        double shapeDistance,
        int stageThreeRank,
        double overallSimilarity,
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
        String uniProtId,
        String proteinName,
        String geneName,
        String organism
) {
}
