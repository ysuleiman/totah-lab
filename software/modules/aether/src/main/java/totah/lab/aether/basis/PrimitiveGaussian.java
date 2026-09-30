package totah.lab.aether.basis;

import java.util.Objects;
import totah.lab.gaia.geometry.Point3D;

/** A normalized Cartesian s primitive. Centers are bohr; exponent is bohr^-2. */
public record PrimitiveGaussian(Point3D centerBohr, double exponent) {
    public PrimitiveGaussian {
        Objects.requireNonNull(centerBohr, "centerBohr");
        if (!Double.isFinite(exponent) || exponent <= 0) {
            throw new IllegalArgumentException("Exponent must be finite and positive");
        }
        double n = normalization(exponent);
        if (!Double.isFinite(n) || n == 0) {
            throw new IllegalArgumentException("Primitive normalization is not representable");
        }
    }

    public double normalization() {
        return normalization(exponent);
    }

    private static double normalization(double exponent) {
        return StrictMath.exp(0.75 * (StrictMath.log(exponent) + StrictMath.log(2 / StrictMath.PI)));
    }
}
