package totah.lab.athena.geometry;

import java.util.Objects;

/**
 * Result of {@link GridVolume#sharedEnvelopeVolume}.
 * {@code overlapFraction} is the overlap volume divided by the
 * smaller of the two envelope volumes (overlap coefficient); it is
 * 0.0 when either envelope volume is 0.0.
 */
public record SharedEnvelopeVolume(
        double overlapVolumeCubicAngstroms,
        int overlapVoxelCount,
        double firstVolumeCubicAngstroms,
        double secondVolumeCubicAngstroms,
        double overlapFraction,
        EnvelopeOptions options) {

    public SharedEnvelopeVolume {
        if (!Double.isFinite(overlapVolumeCubicAngstroms)
                || overlapVolumeCubicAngstroms < 0.0) {
            throw new IllegalArgumentException(
                    "overlapVolumeCubicAngstroms must be finite and non-negative");
        }
        if (overlapVoxelCount < 0) {
            throw new IllegalArgumentException(
                    "overlapVoxelCount must be non-negative");
        }
        if (!Double.isFinite(overlapFraction)
                || overlapFraction < 0.0 || overlapFraction > 1.0) {
            throw new IllegalArgumentException(
                    "overlapFraction must be finite and within [0, 1]");
        }
        Objects.requireNonNull(options, "options");
    }
}
