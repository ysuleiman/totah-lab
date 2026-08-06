package totah.lab.web.service;

/**
 * The Stage 1 retrieval channel that first surfaced a candidate.
 *
 * <p>The Stage 1 candidate set is a union of up to three channels,
 * evaluated in this order: the SQL global-shape descriptor retrieval
 * ({@link #GLOBAL_SHAPE}), the experimental PocketMatch signature
 * channel ({@link #POCKET_MATCH}, disabled by default), and the
 * chosen-reference pocket guarantee ({@link #CHOSEN_REFERENCE}). A
 * candidate present in several channels keeps the provenance of the
 * first channel that surfaced it; the dual membership is visible only
 * through the per-channel rank fields on the diagnostic row, not
 * through a combined flag.</p>
 */
public enum CandidateProvenance {

    /**
     * Retrieved by the SQL global shape descriptor ordering
     * ({@code PocketSummaryRepository.findDescriptorCandidates}).
     */
    GLOBAL_SHAPE,

    /**
     * Surfaced only by the PocketMatch signature channel
     * ({@code pocket.search.pocket-match.enabled}).
     */
    POCKET_MATCH,

    /**
     * Surfaced only by the chosen-pocket guarantee
     * ({@code docking.structure.chosen_pocket_id}).
     */
    CHOSEN_REFERENCE
}
