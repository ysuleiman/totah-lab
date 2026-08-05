package totah.lab.athena.pocket.pocketmatch;

/**
 * Result of comparing two PocketMatch signatures.
 *
 * <p>{@code symmetricScore} is the PocketMatch-style PMScore: matched
 * distance elements normalized by the larger signature size.
 * {@code firstCoverage} and {@code secondCoverage} are directional
 * containment scores that expose subset relationships — for example a
 * small pocket embedded in a much larger merged pocket — which the
 * symmetric score alone would hide. All normalized scores lie in
 * [0, 1]; higher means more similar.</p>
 *
 * <p>Part of Athena's independent, clean-room implementation of the
 * PocketMatch representation described by Yeturu &amp; Chandra (2008);
 * see the package documentation for the full citation and provenance.</p>
 */
public record PocketMatchComparison(
        int matchedDistanceCount,
        int firstDistanceCount,
        int secondDistanceCount,
        double symmetricScore,
        double firstCoverage,
        double secondCoverage,
        double distanceToleranceAngstroms
) {

    public PocketMatchComparison {
        if (matchedDistanceCount < 0
                || firstDistanceCount < 0
                || secondDistanceCount < 0) {
            throw new IllegalArgumentException(
                    "distance counts must be non-negative"
            );
        }
        requireUnitInterval(symmetricScore, "symmetricScore");
        requireUnitInterval(firstCoverage, "firstCoverage");
        requireUnitInterval(secondCoverage, "secondCoverage");
        if (!Double.isFinite(distanceToleranceAngstroms)
                || distanceToleranceAngstroms < 0.0) {
            throw new IllegalArgumentException(
                    "distanceToleranceAngstroms must be finite and "
                            + "non-negative"
            );
        }
    }

    private static void requireUnitInterval(double value, String name) {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    name + " must lie in [0, 1], but was " + value
            );
        }
    }
}
