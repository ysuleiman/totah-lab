package totah.lab.athena.geometry;

import java.util.Objects;

/** Result of {@link GridVolume#envelopeVolume}. */
public record EnvelopeVolume(
        double volumeCubicAngstroms,
        int voxelCount,
        EnvelopeOptions options) {

    public EnvelopeVolume {
        if (!Double.isFinite(volumeCubicAngstroms)
                || volumeCubicAngstroms < 0.0) {
            throw new IllegalArgumentException(
                    "volumeCubicAngstroms must be finite and non-negative");
        }
        if (voxelCount < 0) {
            throw new IllegalArgumentException(
                    "voxelCount must be non-negative");
        }
        Objects.requireNonNull(options, "options");
    }
}
