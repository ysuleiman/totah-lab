package totah.lab.aether.integral;

import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.basis.PrimitiveGaussian;

/** Nonrelativistic -1/2 Laplacian integral; s primitives and s/p contractions, in hartree. */
public final class SKinetic {
    private SKinetic() {}

    public static double between(PrimitiveGaussian a, PrimitiveGaussian b) {
        return GaussianPair.of(a, b).kinetic();
    }

    /** Reuses the overlap-normalized contraction coefficients and normalization. */
    public static double between(ContractedGaussian a, ContractedGaussian b) {
        if (a.angularMomentum() != totah.lab.aether.basis.CartesianAngularMomentum.S || b.angularMomentum() != totah.lab.aether.basis.CartesianAngularMomentum.S) return CartesianIntegrals.kinetic(a,b);
        return GaussianContraction.normalized(a, b, SKinetic::between);
    }
}
