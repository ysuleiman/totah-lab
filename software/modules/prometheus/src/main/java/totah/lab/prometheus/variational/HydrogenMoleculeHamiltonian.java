package totah.lab.prometheus.variational;

import totah.lab.prometheus.identity.CanonicalHashing;

/** Fixed-R Born-Oppenheimer H2 Hamiltonian including nuclear repulsion. */
public final class HydrogenMoleculeHamiltonian implements Hamiltonian {
    private final double bondLengthBohr;
    public HydrogenMoleculeHamiltonian(double bondLengthBohr) {
        if(!Double.isFinite(bondLengthBohr)||bondLengthBohr<=0) throw new IllegalArgumentException("positive R required");
        this.bondLengthBohr=bondLengthBohr;
    }
    public double bondLengthBohr() { return bondLengthBohr; }
    @Override public String scientificIdentity() {
        return CanonicalHashing.sha256Hex("h2-born-oppenheimer-v1|R="+bondLengthBohr+"|atomic-units");
    }
    public double potential(QuantumCoordinates coordinates) {
        var one=coordinates.particles().get(0); var two=coordinates.particles().get(1); double h=bondLengthBohr/2;
        double r1a=r(one.xBohr(),one.yBohr(),one.zBohr()+h),r1b=r(one.xBohr(),one.yBohr(),one.zBohr()-h);
        double r2a=r(two.xBohr(),two.yBohr(),two.zBohr()+h),r2b=r(two.xBohr(),two.yBohr(),two.zBohr()-h);
        double r12=r(one.xBohr()-two.xBohr(),one.yBohr()-two.yBohr(),one.zBohr()-two.zBohr());
        return -1/r1a-1/r1b-1/r2a-1/r2b+1/r12+1/bondLengthBohr;
    }
    /** Explicit partial derivative of the Coulomb potential with respect to R, in hartree/bohr. */
    public double potentialDerivativeBondLength(QuantumCoordinates coordinates) {
        var one=coordinates.particles().get(0); var two=coordinates.particles().get(1); double h=bondLengthBohr/2;
        return electronNuclearDerivative(one.xBohr(),one.yBohr(),one.zBohr(),h)
                +electronNuclearDerivative(two.xBohr(),two.yBohr(),two.zBohr(),h)
                -1/(bondLengthBohr*bondLengthBohr);
    }
    @Override public QuantumAmplitude apply(QuantumState state,QuantumCoordinates coordinates) {
        var differentiable=(DifferentiableQuantumState)state; double psi=differentiable.value(coordinates).real();
        return QuantumAmplitude.real(-0.5*differentiable.coordinateLaplacian(coordinates).value().real()
                +potential(coordinates)*psi);
    }
    private static double r(double x,double y,double z) { return Math.sqrt(x*x+y*y+z*z); }
    private static double electronNuclearDerivative(double x,double y,double z,double halfBondLength) {
        double za=z+halfBondLength,zb=z-halfBondLength;
        double ra=r(x,y,za),rb=r(x,y,zb);
        return za/(2*ra*ra*ra)-zb/(2*rb*rb*rb);
    }
}
