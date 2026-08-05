package totah.lab.web.service;

import totah.lab.athena.pocket.compare.residue.PocketSimilarityClassification;
import totah.lab.athena.pocket.compare.residue.ResidueChemistryAssessment;

/**
 * Serializable view of Athena's {@link ResidueChemistryAssessment}
 * plus the classification and blended final similarity derived from
 * it, for the comparison UI. No metric is recomputed in web-api.
 */
public record ChemistryAssessmentView(
        double chemistrySimilarity,
        double chemistryCoverageAdjustedSimilarity,
        double compatibleMatchedFraction,
        double spatialReplacementFraction,
        int identicalCount,
        int conservativeCount,
        int chemistryCompatibleCount,
        int spatialReplacementCount,
        int matchedResidueCount,
        int queryResidueCount,
        int candidateResidueCount,
        double keyResidueChemistrySimilarity,
        int keyMatchedCount,
        String classification,
        double finalSimilarity
) {

    static ChemistryAssessmentView toView(
            ResidueChemistryAssessment assessment,
            PocketSimilarityClassification classification,
            double finalSimilarity
    ) {
        return new ChemistryAssessmentView(
                assessment.chemistrySimilarity(),
                assessment.chemistryCoverageAdjustedSimilarity(),
                assessment.compatibleMatchedFraction(),
                assessment.spatialReplacementFraction(),
                assessment.identicalCount(),
                assessment.conservativeCount(),
                assessment.chemistryCompatibleCount(),
                assessment.spatialReplacementCount(),
                assessment.matchedResidueCount(),
                assessment.queryResidueCount(),
                assessment.candidateResidueCount(),
                assessment.keyResidueChemistrySimilarity(),
                assessment.keyMatchedCount(),
                classification.name(),
                finalSimilarity
        );
    }
}
