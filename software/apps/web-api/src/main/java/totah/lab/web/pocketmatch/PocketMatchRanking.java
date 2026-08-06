package totah.lab.web.pocketmatch;

/**
 * Ranking metric for the experimental PocketMatch candidate channel
 * ({@code pocket.search.pocket-match.ranking}).
 *
 * <p>Part of Athena's independent, clean-room implementation of the
 * PocketMatch representation described by Yeturu &amp; Chandra (2008);
 * see the {@code totah.lab.athena.pocket.pocketmatch} package
 * documentation for the full citation and provenance.</p>
 */
public enum PocketMatchRanking {

    /**
     * Directional containment of the query signature in the candidate
     * ({@code PocketMatchComparison.firstCoverage} with the query as
     * first argument). This is the default: it recovers homologous
     * pockets embedded in larger merged pockets that the symmetric
     * score penalizes.
     */
    QUERY_COVERAGE,

    /**
     * The PocketMatch-style symmetric PMScore
     * ({@code PocketMatchComparison.symmetricScore}).
     */
    SYMMETRIC_SCORE
}
