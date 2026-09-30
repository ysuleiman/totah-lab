package totah.lab.aether.basis;

import java.util.Objects;

/** Coefficient multiplies a normalized primitive, before contraction normalization. */
public record GaussianTerm(PrimitiveGaussian primitive, double coefficient) {
    public GaussianTerm {
        Objects.requireNonNull(primitive, "primitive");
        if (!Double.isFinite(coefficient)) {
            throw new IllegalArgumentException("Coefficient must be finite");
        }
    }
}
