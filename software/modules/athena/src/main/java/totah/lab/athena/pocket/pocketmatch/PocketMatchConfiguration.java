package totah.lab.athena.pocket.pocketmatch;

/**
 * Configuration for PocketMatch signature comparison.
 *
 * <p>The default distance tolerance of 0.50 angstroms is a starting
 * point, not a validated final value; it is expected to be tuned from
 * benchmark evidence.</p>
 *
 * <p>Part of Athena's independent, clean-room implementation of the
 * PocketMatch representation described by Yeturu &amp; Chandra (2008);
 * see the package documentation for the full citation and provenance.</p>
 */
public record PocketMatchConfiguration(
        double distanceToleranceAngstroms
) {

    public static final double DEFAULT_DISTANCE_TOLERANCE_ANGSTROMS = 0.50;

    public PocketMatchConfiguration {
        if (!Double.isFinite(distanceToleranceAngstroms)
                || distanceToleranceAngstroms < 0.0) {
            throw new IllegalArgumentException(
                    "distanceToleranceAngstroms must be finite and "
                            + "non-negative, but was "
                            + distanceToleranceAngstroms
            );
        }
    }

    public static PocketMatchConfiguration defaults() {
        return new PocketMatchConfiguration(
                DEFAULT_DISTANCE_TOLERANCE_ANGSTROMS
        );
    }
}
