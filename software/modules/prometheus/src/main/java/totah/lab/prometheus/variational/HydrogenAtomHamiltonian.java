package totah.lab.prometheus.variational;

import totah.lab.prometheus.identity.CanonicalHashing;

/** Non-relativistic clamped-nucleus one-electron Coulomb Hamiltonian in atomic units. */
public final class HydrogenAtomHamiltonian implements Hamiltonian {
    private final int nuclearCharge;

    public HydrogenAtomHamiltonian(int nuclearCharge) {
        if(nuclearCharge<1) throw new IllegalArgumentException("nuclearCharge must be positive");
        this.nuclearCharge=nuclearCharge;
    }

    public int nuclearCharge() { return nuclearCharge; }
    @Override public String scientificIdentity() {
        return CanonicalHashing.sha256Hex("hydrogenic-hamiltonian-v1|Z="+nuclearCharge+"|atomic-units");
    }
    @Override public QuantumAmplitude apply(QuantumState state,QuantumCoordinates coordinates) {
        var particle=coordinates.particles().getFirst();
        double r=Math.sqrt(particle.xBohr()*particle.xBohr()+particle.yBohr()*particle.yBohr()
                +particle.zBohr()*particle.zBohr());
        if(r==0.0) throw new IllegalArgumentException("Coulomb Hamiltonian is singular at the nucleus");
        double psi=state.value(coordinates).real();
        double laplacian=((DifferentiableQuantumState)state).coordinateLaplacian(coordinates).value().real();
        return QuantumAmplitude.real(-0.5*laplacian-nuclearCharge*psi/r);
    }

    public double exactGroundEnergyHartree() { return -0.5*nuclearCharge*nuclearCharge; }
    public double exactNormalizedGroundState(double radiusBohr) {
        return Math.pow(nuclearCharge,1.5)/Math.sqrt(Math.PI)*Math.exp(-nuclearCharge*radiusBohr);
    }
}
