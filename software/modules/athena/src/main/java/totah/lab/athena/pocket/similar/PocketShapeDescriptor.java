package totah.lab.athena.pocket.similar;


import totah.lab.athena.pocket.geometry.PocketGeometryBasis;

import java.util.Arrays;
import java.util.Objects;

public record PocketShapeDescriptor(
        int pointCount,
        PocketGeometryBasis basis,
        double radiusOfGyration,
        double maximumRadius,
        double meanRadius,
        double radiusStandardDeviation,
        double majorExtent,
        double middleExtent,
        double minorExtent,
        double elongation,
        double flatness,
        double[] radialHistogram
) {

    public PocketShapeDescriptor {
        if (pointCount < 1) {
            throw new IllegalArgumentException(
                    "pointCount must be greater than zero"
            );
        }

        Objects.requireNonNull(basis, "basis");
        Objects.requireNonNull(
                radialHistogram,
                "radialHistogram"
        );

        radialHistogram = Arrays.copyOf(
                radialHistogram,
                radialHistogram.length
        );
    }

    @Override
    public double[] radialHistogram() {
        return Arrays.copyOf(
                radialHistogram,
                radialHistogram.length
        );
    }
}