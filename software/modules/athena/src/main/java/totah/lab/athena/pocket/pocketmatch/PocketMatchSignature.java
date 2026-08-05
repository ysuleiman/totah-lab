package totah.lab.athena.pocket.pocketmatch;

import java.util.Objects;

/**
 * PocketMatch signature of one pocket: for each of the ninety
 * categories (fifteen unordered chemistry-group pairs crossed with six
 * unordered point-type pairs), the sorted ascending list of Euclidean
 * distances observed between compatible representative points.
 *
 * <p>Distance lists are stored in a flat array indexed by
 * {@link PocketMatchCategories#indexOf} rather than a hash map: the
 * comparator touches every category for every comparison, so indexed
 * access keeps the hot loop allocation- and lookup-free.</p>
 *
 * <p>Note: array components make {@link #equals(Object)} identity-based
 * in practice; signatures are value objects for transport and
 * comparison input, not for set membership.</p>
 *
 * <p>Part of Athena's independent, clean-room implementation of the
 * PocketMatch representation described by Yeturu &amp; Chandra (2008);
 * see the package documentation for the full citation and provenance.</p>
 */
public record PocketMatchSignature(
        double[][] sortedDistances,
        int totalDistanceCount,
        PocketMatchSignatureDiagnostics diagnostics
) {

    public PocketMatchSignature {
        Objects.requireNonNull(sortedDistances, "sortedDistances");
        Objects.requireNonNull(diagnostics, "diagnostics");

        if (sortedDistances.length != PocketMatchCategories.CATEGORY_COUNT) {
            throw new IllegalArgumentException(
                    "sortedDistances must contain exactly "
                            + PocketMatchCategories.CATEGORY_COUNT
                            + " category lists, but contained "
                            + sortedDistances.length
            );
        }

        int total = 0;
        for (int index = 0; index < sortedDistances.length; index++) {
            double[] distances = Objects.requireNonNull(
                    sortedDistances[index],
                    "sortedDistances[" + index + "]"
            );
            for (double distance : distances) {
                if (!Double.isFinite(distance) || distance < 0.0) {
                    throw new IllegalArgumentException(
                            "distances must be finite and non-negative"
                    );
                }
            }
            total += distances.length;
        }

        if (totalDistanceCount != total) {
            throw new IllegalArgumentException(
                    "totalDistanceCount " + totalDistanceCount
                            + " does not match the stored distance count "
                            + total
            );
        }
    }

    /**
     * Creates a signature without build diagnostics, for callers that
     * load persisted distance lists.
     */
    public static PocketMatchSignature ofPersisted(
            double[][] sortedDistances,
            int totalDistanceCount
    ) {
        return new PocketMatchSignature(
                sortedDistances,
                totalDistanceCount,
                PocketMatchSignatureDiagnostics.NOT_TRACKED
        );
    }

    /**
     * Returns the sorted distance list of one category. The array is
     * shared, not copied; callers must not mutate it.
     */
    public double[] distances(PocketMatchCategory category) {
        return sortedDistances[PocketMatchCategories.indexOf(category)];
    }
}
