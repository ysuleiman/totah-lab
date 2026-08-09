package totah.lab.athena.pocket.architecture;

/**
 * Thresholds for {@link SubpocketOccupancyAnalyzer}. Occupancy uses
 * the same criterion as {@link LigandSpaceOptions} (a sphere is
 * occupied when a ligand heavy-atom center lies within
 * {@code fraction * radius + tolerance} of its center).
 * {@code localDensityRadiusAngstroms} defines the local sphere
 * density: the number of reference-pocket sphere centers within that
 * radius of a ligand atom.
 */
public record SubpocketOccupancyOptions(
        double sphereOccupancyRadiusFraction,
        double sphereOccupancyToleranceAngstroms,
        double localDensityRadiusAngstroms
) {

    public SubpocketOccupancyOptions {
        if (!Double.isFinite(sphereOccupancyRadiusFraction)
                || sphereOccupancyRadiusFraction <= 0.0
                || !Double.isFinite(localDensityRadiusAngstroms)
                || localDensityRadiusAngstroms <= 0.0) {
            throw new IllegalArgumentException(
                    "Fraction and density radius must be finite and "
                            + "greater than zero"
            );
        }

        if (!Double.isFinite(sphereOccupancyToleranceAngstroms)
                || sphereOccupancyToleranceAngstroms < 0.0) {
            throw new IllegalArgumentException(
                    "sphereOccupancyToleranceAngstroms must be finite "
                            + "and non-negative"
            );
        }
    }

    public static SubpocketOccupancyOptions defaults() {
        return new SubpocketOccupancyOptions(
                1.0,
                0.0,
                5.0
        );
    }
}
