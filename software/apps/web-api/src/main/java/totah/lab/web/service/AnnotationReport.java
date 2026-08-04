package totah.lab.web.service;

import java.util.List;

/**
 * The full annotation pipeline result: per-accession rows, hit-list
 * summary counts, and per-category enrichment against the database
 * background.
 */
public record AnnotationReport(
        List<AnnotatedProtein> hits,
        int requested,
        int found,
        FlagTally hitTally,
        FlagTally backgroundTally,
        List<AnnotationEnrichment> enrichment
) {
}
