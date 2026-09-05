package totah.lab.athena.geometry;

import java.util.Objects;

/**
 * Result of {@link GridVolume#localFreeVolume}. {@code regionVoxelCount}
 * counts all voxels in the ligand-neighborhood region (within
 * padding of a reference atom); {@code freeVoxelCount} counts those
 * that additionally satisfy the clearance cutoff against every
 * environment and reference atom.
 */
public record FreeVolume(
        double freeVolumeCubicAngstroms,
        int freeVoxelCount,
        int regionVoxelCount,
        FreeVolumeOptions options) {

    public FreeVolume {
        if (!Double.isFinite(freeVolumeCubicAngstroms)
                || freeVolumeCubicAngstroms < 0.0) {
            throw new IllegalArgumentException(
                    "freeVolumeCubicAngstroms must be finite and non-negative");
        }
        if (freeVoxelCount < 0 || regionVoxelCount < 0
                || freeVoxelCount > regionVoxelCount) {
            throw new IllegalArgumentException(
                    "voxel counts must be non-negative with free <= region");
        }
        Objects.requireNonNull(options, "options");
    }
}
