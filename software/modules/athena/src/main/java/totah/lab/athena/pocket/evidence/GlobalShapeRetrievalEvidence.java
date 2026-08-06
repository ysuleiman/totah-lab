package totah.lab.athena.pocket.evidence;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Retrieval evidence from the global (whole-structure) shape method.
 * When the method did not evaluate the candidate,
 * {@code evaluated} is {@code false} and {@code rank} and
 * {@code distance} are empty: ranks and distances are never
 * invented.
 *
 * @param evaluated whether the global-shape method evaluated the
 *                  candidate
 * @param rank      rank of the candidate in the global-shape ranking
 * @param distance  global-shape distance of the candidate
 */
public record GlobalShapeRetrievalEvidence(
        boolean evaluated,
        OptionalInt rank,
        OptionalDouble distance
) {

    public GlobalShapeRetrievalEvidence {
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(distance, "distance");

        if (!evaluated && (rank.isPresent() || distance.isPresent())) {
            throw new IllegalArgumentException(
                    "A candidate that was not evaluated must not carry"
                            + " a rank or distance"
            );
        }
    }

    /**
     * The evidence of a candidate the global-shape method never
     * evaluated.
     */
    public static GlobalShapeRetrievalEvidence notEvaluated() {
        return new GlobalShapeRetrievalEvidence(
                false,
                OptionalInt.empty(),
                OptionalDouble.empty()
        );
    }
}
