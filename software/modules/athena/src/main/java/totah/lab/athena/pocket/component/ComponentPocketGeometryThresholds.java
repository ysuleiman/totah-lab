package totah.lab.athena.pocket.component;

/** Versioned scientific thresholds for component-to-pocket geometry. */
public record ComponentPocketGeometryThresholds(
        double contactDistanceAngstrom,
        double nearDistanceAngstrom,
        double plausiblePocketDistanceAngstrom,
        double sphereNearShellAngstrom,
        double occupiedHeavyAtomFraction) {

    public static ComponentPocketGeometryThresholds defaults() {
        return new ComponentPocketGeometryThresholds(4.0, 6.0, 12.0, 2.0, 0.5);
    }

    public ComponentPocketGeometryThresholds {
        requirePositive(contactDistanceAngstrom, "contactDistanceAngstrom");
        requirePositive(nearDistanceAngstrom, "nearDistanceAngstrom");
        requirePositive(plausiblePocketDistanceAngstrom,
                "plausiblePocketDistanceAngstrom");
        requirePositive(sphereNearShellAngstrom, "sphereNearShellAngstrom");
        if (nearDistanceAngstrom < contactDistanceAngstrom) {
            throw new IllegalArgumentException("near distance must be >= contact distance");
        }
        if (plausiblePocketDistanceAngstrom < nearDistanceAngstrom) {
            throw new IllegalArgumentException(
                    "plausible distance must be >= near distance");
        }
        if (!Double.isFinite(occupiedHeavyAtomFraction)
                || occupiedHeavyAtomFraction <= 0
                || occupiedHeavyAtomFraction > 1) {
            throw new IllegalArgumentException(
                    "occupiedHeavyAtomFraction must be in (0, 1]");
        }
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
