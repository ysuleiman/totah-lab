package totah.lab.athena.pocket.pocketmatch;

import java.util.Objects;

/**
 * Default {@link PocketMatchComparator}: for each of the ninety
 * categories, matches the two sorted distance lists with an incremental
 * two-pointer sweep under the configured tolerance, then aggregates the
 * per-category match counts.
 *
 * <p>Scores:</p>
 *
 * <ul>
 *     <li>symmetric: matched / max(first size, second size) — the
 *     PocketMatch-style PMScore;</li>
 *     <li>first coverage: matched / first size;</li>
 *     <li>second coverage: matched / second size.</li>
 * </ul>
 *
 * <p>Empty signatures and empty distance lists are safe: a zero
 * denominator yields a zero score.</p>
 *
 * <p>Part of Athena's independent, clean-room implementation of the
 * PocketMatch representation described by Yeturu &amp; Chandra (2008);
 * see the package documentation for the full citation and provenance.</p>
 */
public final class DefaultPocketMatchComparator
        implements PocketMatchComparator {

    private final PocketMatchConfiguration configuration;

    public DefaultPocketMatchComparator() {
        this(PocketMatchConfiguration.defaults());
    }

    public DefaultPocketMatchComparator(
            PocketMatchConfiguration configuration
    ) {
        this.configuration = Objects.requireNonNull(
                configuration,
                "configuration"
        );
    }

    @Override
    public PocketMatchComparison compare(
            PocketMatchSignature first,
            PocketMatchSignature second
    ) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");

        double tolerance = configuration.distanceToleranceAngstroms();

        int matched = 0;
        double[][] firstLists = first.sortedDistances();
        double[][] secondLists = second.sortedDistances();

        for (int index = 0;
             index < PocketMatchCategories.CATEGORY_COUNT;
             index++) {
            matched += countMatches(
                    firstLists[index],
                    secondLists[index],
                    tolerance
            );
        }

        int firstCount = first.totalDistanceCount();
        int secondCount = second.totalDistanceCount();

        return new PocketMatchComparison(
                matched,
                firstCount,
                secondCount,
                ratio(matched, Math.max(firstCount, secondCount)),
                ratio(matched, firstCount),
                ratio(matched, secondCount),
                tolerance
        );
    }

    /**
     * Incremental two-pointer matching of two ascending distance lists.
     * Each list element participates in at most one match.
     */
    static int countMatches(
            double[] first,
            double[] second,
            double tolerance
    ) {
        int i = 0;
        int j = 0;
        int matches = 0;

        while (i < first.length && j < second.length) {
            double delta = first[i] - second[j];

            if (Math.abs(delta) <= tolerance) {
                matches++;
                i++;
                j++;
            } else if (delta < 0) {
                i++;
            } else {
                j++;
            }
        }

        return matches;
    }

    private static double ratio(int numerator, int denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        return numerator / (double) denominator;
    }
}
