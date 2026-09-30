package totah.lab.aether.integral;

import java.util.List;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.basis.GaussianTerm;
import totah.lab.aether.basis.PrimitiveGaussian;

/** Dimensionless overlap; legacy s primitives and Cartesian s/p contractions. No screening. */
public final class SOverlap {
    private SOverlap() {}

    public static double between(PrimitiveGaussian a, PrimitiveGaussian b) {
        return GaussianPair.of(a, b).overlap();
    }

    public static double between(ContractedGaussian a, ContractedGaussian b) {
        if (a.angularMomentum() != totah.lab.aether.basis.CartesianAngularMomentum.S || b.angularMomentum() != totah.lab.aether.basis.CartesianAngularMomentum.S) return CartesianIntegrals.overlap(a,b);
        return GaussianContraction.normalized(a, b, SOverlap::between);
    }

    /** Shared contraction sum used both for normalization and cross overlap. */
    public static double unnormalizedContraction(List<GaussianTerm> a, List<GaussianTerm> b) {
        return GaussianContraction.sum(a, b, SOverlap::between);
    }
}
