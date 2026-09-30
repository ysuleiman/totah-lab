package totah.lab.aether.integral;

import java.util.List;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.basis.PrimitiveGaussian;
import totah.lab.aether.model.NuclearCenter;

/** Electron–point-nucleus attraction; s primitives and Cartesian s/p contractions, in hartree. */
public final class SNuclearAttraction {
    private SNuclearAttraction() {}

    public static double between(PrimitiveGaussian a, PrimitiveGaussian b, NuclearCenter nucleus) {
        return between(a, b, List.of(nucleus));
    }

    public static double between(PrimitiveGaussian a, PrimitiveGaussian b, List<NuclearCenter> nuclei) {
        return evaluate(a, b, checkedNuclei(nuclei));
    }

    public static double between(ContractedGaussian a, ContractedGaussian b, List<NuclearCenter> nuclei) {
        var centers = checkedNuclei(nuclei);
        if (a.angularMomentum() != totah.lab.aether.basis.CartesianAngularMomentum.S || b.angularMomentum() != totah.lab.aether.basis.CartesianAngularMomentum.S) return CartesianIntegrals.attraction(a,b,centers);
        return GaussianContraction.normalized(a, b, (left, right) -> evaluate(left, right, centers));
    }

    private static List<NuclearCenter> checkedNuclei(List<NuclearCenter> nuclei) {
        var centers = List.copyOf(nuclei);
        if (centers.isEmpty()) throw new IllegalArgumentException("At least one nuclear center is required");
        return centers;
    }

    private static double evaluate(PrimitiveGaussian a, PrimitiveGaussian b, List<NuclearCenter> nuclei) {
        var pair = GaussianPair.of(a, b);
        double p = GaussianContraction.requireFinite(a.exponent() + b.exponent());
        var productCenter = GaussianPair.productCenter(a, b, p);
        double overlap = pair.overlap();
        double scale = (2 / StrictMath.sqrt(StrictMath.PI)) * StrictMath.sqrt(p);
        double sum = 0;
        for (NuclearCenter nucleus : nuclei) {
            double r2 = GaussianContraction.requireFinite(productCenter.distanceSquared(nucleus.centerBohr()));
            double t = GaussianContraction.requireFinite(p * r2);
            double boys = BoysF0.value(t);
            double magnitude = nucleus.charge() * (scale * boys) * overlap;
            if (overlap < Double.MIN_NORMAL || !Double.isFinite(magnitude) || magnitude == 0) {
                // Preserve representable V even when overlap or intermediate scaling underflows.
                magnitude = StrictMath.exp(StrictMath.log(nucleus.charge()) + StrictMath.log(scale)
                        + StrictMath.log(boys) + pair.logOverlap(a, b));
            }
            sum -= GaussianContraction.requireFinite(magnitude);
        }
        return GaussianContraction.requireFinite(sum);
    }
}
