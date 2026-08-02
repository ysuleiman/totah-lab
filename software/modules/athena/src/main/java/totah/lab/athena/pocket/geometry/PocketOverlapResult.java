package totah.lab.athena.pocket.geometry;

import java.util.Objects;

public record PocketOverlapResult(
        double intersectionVolume,
        double intersectionOverUnion,
        PocketGeometryBasis firstBasis,
        PocketGeometryBasis secondBasis) {

    public PocketOverlapResult {
        if (!Double.isFinite(intersectionVolume)
                || intersectionVolume < 0.0) {
            throw new IllegalArgumentException(
                    "intersectionVolume must be finite and non-negative");
        }
        if (!Double.isFinite(intersectionOverUnion)
                || intersectionOverUnion < 0.0
                || intersectionOverUnion > 1.0) {
            throw new IllegalArgumentException(
                    "intersectionOverUnion must be between zero and one");
        }
        Objects.requireNonNull(firstBasis, "firstBasis");
        Objects.requireNonNull(secondBasis, "secondBasis");
    }
}
