package totah.lab.gaia.graph;

import totah.lab.gaia.structure.AtomReference;

import java.util.Objects;

/** One atom pair satisfying a neutral structural distance criterion. */
public record AtomPairDistance(
        AtomReference first,
        AtomReference second,
        double distance) {

    public AtomPairDistance {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first.equals(second)) {
            throw new IllegalArgumentException(
                    "An atom pair requires distinct atoms");
        }
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException(
                    "distance must be finite and non-negative");
        }
    }
}
