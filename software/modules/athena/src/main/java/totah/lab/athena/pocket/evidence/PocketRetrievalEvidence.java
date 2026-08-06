package totah.lab.athena.pocket.evidence;

import java.util.Objects;
import java.util.Set;

/**
 * How the candidate pocket was retrieved before the comparison. Each
 * retrieval method reports its own evidence; a method that never saw
 * the candidate reports {@code evaluated = false} and empty ranks
 * and scores.
 *
 * @param globalShape      global-shape retrieval evidence
 * @param pocketMatch      pocket-match retrieval evidence
 * @param chosenReference  whether the candidate is the explicitly
 *                         chosen reference structure
 * @param candidateSources which retrieval channels produced the
 *                         candidate
 */
public record PocketRetrievalEvidence(
        GlobalShapeRetrievalEvidence globalShape,
        PocketMatchRetrievalEvidence pocketMatch,
        boolean chosenReference,
        Set<PocketCandidateSource> candidateSources
) {

    public PocketRetrievalEvidence {
        Objects.requireNonNull(globalShape, "globalShape");
        Objects.requireNonNull(pocketMatch, "pocketMatch");

        candidateSources = Set.copyOf(
                Objects.requireNonNull(candidateSources, "candidateSources")
        );
    }
}
