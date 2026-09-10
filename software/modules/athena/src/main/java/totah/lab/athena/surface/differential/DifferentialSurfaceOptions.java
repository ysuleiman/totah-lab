package totah.lab.athena.surface.differential;

/** Immutable constants for one differential-surface definition. */
public record DifferentialSurfaceOptions(
        DifferentialSurfaceMode mode,
        double neighborhoodRadiusAngstroms,
        double scoringRadiusAngstroms,
        double probeRadiusAngstroms,
        double absoluteSasaThreshold,
        double relativeSasaThreshold,
        boolean neighborhoodUpdate,
        boolean symmetricNeighborhood,
        boolean mutationsOnly) {

    public static final DifferentialSurfaceOptions SURFDIFF_COMPATIBLE =
            new DifferentialSurfaceOptions(
                    DifferentialSurfaceMode.SURFDIFF_COMPATIBLE,
                    7.0, 5.0, 1.4, 0.0, 0.05,
                    false, true, false);

    public DifferentialSurfaceOptions {
        if (mode == null) {
            throw new NullPointerException("mode");
        }
        requirePositive(neighborhoodRadiusAngstroms,
                "neighborhoodRadiusAngstroms");
        requirePositive(scoringRadiusAngstroms,
                "scoringRadiusAngstroms");
        requirePositive(probeRadiusAngstroms, "probeRadiusAngstroms");
        requireNonNegative(absoluteSasaThreshold, "absoluteSasaThreshold");
        if (!Double.isFinite(relativeSasaThreshold)
                || relativeSasaThreshold < 0.0) {
            throw new IllegalArgumentException(
                    "relativeSasaThreshold must be finite and non-negative");
        }
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(
                    name + " must be finite and non-negative");
        }
    }
}
