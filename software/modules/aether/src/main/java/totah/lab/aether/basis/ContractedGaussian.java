package totah.lab.aether.basis;

import java.util.List;
import totah.lab.aether.integral.SOverlap;
import totah.lab.gaia.geometry.Point3D;

/** Immutable unit-normalized Cartesian s/p contraction; signed input coefficients are retained. */
public final class ContractedGaussian {
    public static final double MINIMUM_NORM_SQUARED = 1e-14;
    private final List<GaussianTerm> terms;
    private final double normalization;
    private final CartesianAngularMomentum angularMomentum;

    public ContractedGaussian(List<GaussianTerm> terms) {
        this(terms, CartesianAngularMomentum.S);
    }

    public ContractedGaussian(List<GaussianTerm> terms, CartesianAngularMomentum angularMomentum) {
        this.angularMomentum = java.util.Objects.requireNonNull(angularMomentum);
        this.terms = List.copyOf(terms);
        if (this.terms.isEmpty()) {
            throw new IllegalArgumentException("A contraction needs at least one primitive");
        }
        Point3D center = this.terms.getFirst().primitive().centerBohr();
        for (GaussianTerm term : this.terms) {
            Point3D other = term.primitive().centerBohr();
            if (center.x() != other.x() || center.y() != other.y() || center.z() != other.z()) {
                throw new IllegalArgumentException("Contraction primitives must share a center");
            }
        }
        double normSquared = angularMomentum == CartesianAngularMomentum.S
                ? SOverlap.unnormalizedContraction(this.terms, this.terms)
                : totah.lab.aether.integral.CartesianIntegrals.unnormalizedOverlap(this.terms, angularMomentum, this.terms, angularMomentum);
        if (!Double.isFinite(normSquared) || normSquared <= MINIMUM_NORM_SQUARED) {
            throw new IllegalArgumentException("Numerically singular contraction norm");
        }
        this.normalization = 1 / StrictMath.sqrt(normSquared);
    }

    public CartesianAngularMomentum angularMomentum() { return angularMomentum; }

    public List<GaussianTerm> terms() { return terms; }
    public double normalization() { return normalization; }
}
