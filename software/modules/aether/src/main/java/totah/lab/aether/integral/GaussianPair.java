package totah.lab.aether.integral;

import totah.lab.aether.basis.PrimitiveGaussian;
import totah.lab.gaia.geometry.Point3D;

/** Shared normalized s-pair quantities. Package-private to keep scalar APIs stable. */
record GaussianPair(double prefactor, double reducedExponent, double decay) {
    static Point3D productCenter(PrimitiveGaussian a, PrimitiveGaussian b, double exponentSum) {
        double wa = a.exponent() / exponentSum;
        double wb = b.exponent() / exponentSum;
        Point3D ac = a.centerBohr();
        Point3D bc = b.centerBohr();
        return new Point3D(
                GaussianContraction.requireFinite(wa * ac.x() + wb * bc.x()),
                GaussianContraction.requireFinite(wa * ac.y() + wb * bc.y()),
                GaussianContraction.requireFinite(wa * ac.z() + wb * bc.z()));
    }

    static GaussianPair of(PrimitiveGaussian a, PrimitiveGaussian b) {
        double low = StrictMath.min(a.exponent(), b.exponent());
        double high = StrictMath.max(a.exponent(), b.exponent());
        double ratio = low / high;
        // sqrt(low)/sqrt(high) avoids underflow in the exponent ratio before sqrt.
        double prefactor = StrictMath.pow(2 * (StrictMath.sqrt(low) / StrictMath.sqrt(high))
                / (1 + ratio), 1.5);
        double distanceSquared = a.centerBohr().distanceSquared(b.centerBohr());
        GaussianContraction.requireFinite(distanceSquared);
        double reducedExponent = low / (1 + ratio);
        double decay = reducedExponent * distanceSquared;
        GaussianContraction.requireFinite(decay);
        return new GaussianPair(prefactor, reducedExponent, decay);
    }

    double overlap() {
        return GaussianContraction.requireFinite(prefactor * StrictMath.exp(-decay));
    }

    /** Logarithmic form of the same normalized overlap for scaled Coulomb integrals. */
    double logOverlap(PrimitiveGaussian a, PrimitiveGaussian b) {
        if (prefactor > 0) return StrictMath.log(prefactor) - decay;
        return 0.75 * (StrictMath.log(a.exponent()) + StrictMath.log(b.exponent()))
                + 1.5 * (StrictMath.log(2) - StrictMath.log(a.exponent() + b.exponent())) - decay;
    }

    double kinetic() {
        if (reducedExponent == 0) {
            throw new ArithmeticException("NUMERICAL_FAILURE: reduced exponent underflow in kinetic integral");
        }
        double overlap = overlap();
        double polynomial = 3 - 2 * decay;
        double value = reducedExponent * (polynomial * overlap);
        if (overlap >= Double.MIN_NORMAL && Double.isFinite(value) && value != 0) {
            return value;
        }
        if (decay == 1.5 || prefactor == 0) return 0;
        // Scale in log space when S underflows or intermediate products overflow.
        // An underflowed overlap alone must not erase a representable kinetic integral.
        double logPolynomial = StrictMath.log(2) + StrictMath.log(StrictMath.abs(1.5 - decay));
        double magnitude = StrictMath.exp(StrictMath.log(reducedExponent)
                + logPolynomial + StrictMath.log(prefactor) - decay);
        return GaussianContraction.requireFinite(StrictMath.copySign(magnitude, 1.5 - decay));
    }
}
