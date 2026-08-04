package totah.lab.web.service;

/**
 * Enrichment of one annotation category in the hit list versus the
 * database background. {@code foldEnrichment} is null when the
 * background fraction is zero (ratio undefined).
 */
public record AnnotationEnrichment(
        String category,
        int hitsFlagged,
        int hitsTotal,
        int backgroundFlagged,
        int backgroundTotal,
        Double foldEnrichment,
        double pValue
) {
}
