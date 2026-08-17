package totah.lab.gaia.graph;

import totah.lab.gaia.structure.ResidueId;

import java.util.Objects;
import java.util.OptionalDouble;

/** Neutral measurements for a residue pair selected by minimum distance. */
public record ResidueDistance(
        ResidueId first,
        ResidueId second,
        double minimumDistance,
        OptionalDouble alphaCarbonDistance,
        OptionalDouble centroidDistance) {

    public ResidueDistance {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(alphaCarbonDistance, "alphaCarbonDistance");
        Objects.requireNonNull(centroidDistance, "centroidDistance");
        if (ResidueIds.compare(first, second) >= 0) {
            throw new IllegalArgumentException(
                    "Residue endpoints must be distinct and canonical");
        }
        if (!Double.isFinite(minimumDistance)
                || minimumDistance < 0.0) {
            throw new IllegalArgumentException(
                    "minimumDistance must be finite and non-negative");
        }
    }
}
