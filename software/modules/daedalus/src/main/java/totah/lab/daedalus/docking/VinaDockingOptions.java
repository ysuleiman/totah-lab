package totah.lab.daedalus.docking;

import java.util.Objects;

/**
 * Search-space definition for a docking run. Center and size are always
 * supplied by the caller; no auto-centering is attempted.
 */
public record VinaDockingOptions(
        double centerX,
        double centerY,
        double centerZ,
        double sizeX,
        double sizeY,
        double sizeZ,
        int exhaustiveness,
        Integer seed) {

    public VinaDockingOptions {
        for (double center : new double[]{centerX, centerY, centerZ}) {
            if (!Double.isFinite(center)) {
                throw new IllegalArgumentException("Box center must be finite.");
            }
        }
        if (!Double.isFinite(sizeX) || !Double.isFinite(sizeY)
                || !Double.isFinite(sizeZ) || sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("Box sizes must be positive and finite.");
        }
        if (exhaustiveness < 1) {
            throw new IllegalArgumentException("exhaustiveness must be at least 1.");
        }
    }

    public static VinaDockingOptions ofBox(
            double centerX, double centerY, double centerZ,
            double sizeX, double sizeY, double sizeZ) {
        return new VinaDockingOptions(
                centerX, centerY, centerZ, sizeX, sizeY, sizeZ, 8, null);
    }

    public VinaDockingOptions withSeed(Integer seed) {
        return new VinaDockingOptions(
                centerX, centerY, centerZ, sizeX, sizeY, sizeZ,
                exhaustiveness, Objects.requireNonNull(seed, "seed"));
    }
}
