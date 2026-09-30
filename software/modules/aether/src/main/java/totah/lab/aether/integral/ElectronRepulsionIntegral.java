package totah.lab.aether.integral;

import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.basis.PrimitiveGaussian;

/** Chemists' (ab|cd) Coulomb integrals; s primitives and Cartesian s/p contractions, in hartree. */
public final class ElectronRepulsionIntegral {
    private ElectronRepulsionIntegral() {}

    public static double between(PrimitiveGaussian a, PrimitiveGaussian b,
                                 PrimitiveGaussian c, PrimitiveGaussian d) {
        return between(new PreparedPair(a,b),new PreparedPair(c,d));
    }
    static double between(PreparedPair ab,PreparedPair cd) {
        double p=ab.sum,q=cd.sum;
        double low = StrictMath.min(p, q);
        double high = StrictMath.max(p, q);
        double rho = low / (1 + low / high);
        var productAB = ab.center;
        var productCD = cd.center;
        double r2 = GaussianContraction.requireFinite(productAB.distanceSquared(productCD));
        double t = GaussianContraction.requireFinite(rho * r2);
        double boys = BoysF0.value(t);
        // 2 sqrt(rho/pi) S_ab S_cd F0(rho |P-Q|^2), reusing normalized overlaps.
        double scale = (2 / StrictMath.sqrt(StrictMath.PI)) * StrictMath.sqrt(rho);
        double sab = ab.overlap;
        double scd = cd.overlap;
        double value = (scale * boys) * sab * scd;
        if (sab < Double.MIN_NORMAL || scd < Double.MIN_NORMAL
                || value < Double.MIN_NORMAL || !Double.isFinite(value)) {
            value = StrictMath.exp(StrictMath.log(scale) + StrictMath.log(boys)
                    + ab.pair.logOverlap(ab.a, ab.b) + cd.pair.logOverlap(cd.a, cd.b));
        }
        return GaussianContraction.requireFinite(value);
    }

    static final class PreparedPair {
        final PrimitiveGaussian a,b;final GaussianPair pair;final double sum,overlap;final totah.lab.gaia.geometry.Point3D center;
        PreparedPair(PrimitiveGaussian a,PrimitiveGaussian b) {
            this.a=a;this.b=b;pair=GaussianPair.of(a,b);sum=GaussianContraction.requireFinite(a.exponent()+b.exponent());
            center=GaussianPair.productCenter(a,b,sum);overlap=pair.overlap();
        }
    }

    public static double between(ContractedGaussian a, ContractedGaussian b,
                                 ContractedGaussian c, ContractedGaussian d) {
        if (java.util.stream.Stream.of(a,b,c,d).anyMatch(f -> f.angularMomentum() != totah.lab.aether.basis.CartesianAngularMomentum.S)) return CartesianIntegrals.repulsion(a,b,c,d);
        return GaussianContraction.normalized(a, b, c, d, ElectronRepulsionIntegral::between);
    }
}
