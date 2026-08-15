package totah.lab.prometheus.variational;

import totah.lab.prometheus.identity.CanonicalHashing;

/** Clamped-nucleus, nonrelativistic helium Hamiltonian in atomic units. */
public final class HeliumHamiltonian implements Hamiltonian {
    @Override public String scientificIdentity() {
        return CanonicalHashing.sha256Hex("helium-hamiltonian-v1|Z=2|infinite-nuclear-mass|atomic-units");
    }
    @Override public QuantumAmplitude apply(QuantumState state,QuantumCoordinates coordinates) {
        if(coordinates.particles().size()!=2) throw new IllegalArgumentException("two electrons required");
        var first=coordinates.particles().get(0); var second=coordinates.particles().get(1);
        double r1=radius(first.xBohr(),first.yBohr(),first.zBohr());
        double r2=radius(second.xBohr(),second.yBohr(),second.zBohr());
        double r12=radius(first.xBohr()-second.xBohr(),first.yBohr()-second.yBohr(),first.zBohr()-second.zBohr());
        if(r1==0||r2==0||r12==0) throw new IllegalArgumentException("singular helium configuration");
        var differentiable=(DifferentiableQuantumState)state;
        double psi=differentiable.value(coordinates).real();
        double potential=-2.0/r1-2.0/r2+1.0/r12;
        return QuantumAmplitude.real(-0.5*differentiable.coordinateLaplacian(coordinates).value().real()
                +potential*psi);
    }
    public static double referenceGroundEnergyHartree() { return -2.9037243770341196; }
    private static double radius(double x,double y,double z) { return Math.sqrt(x*x+y*y+z*z); }
}
