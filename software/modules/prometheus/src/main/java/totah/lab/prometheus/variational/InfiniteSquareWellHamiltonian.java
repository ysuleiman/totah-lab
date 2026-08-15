package totah.lab.prometheus.variational;

/** One-dimensional unit-box Hamiltonian H=-1/2 d2/dx2 in atomic units. */
public final class InfiniteSquareWellHamiltonian implements OneDimensionalLocalHamiltonian {
    @Override public String scientificIdentity() { return "infinite-square-well-1d-unit-box-au-v1"; }
    @Override public double kineticCoefficient() { return 0.5; }
    @Override public double potential(double xBohr) {
        return xBohr >= 0.0 && xBohr <= 1.0 ? 0.0 : Double.POSITIVE_INFINITY;
    }

    @Override public QuantumAmplitude apply(QuantumState state, QuantumCoordinates coordinates) {
        if(!(state instanceof DifferentiableQuantumState differentiable)) {
            throw new IllegalArgumentException("Hamiltonian requires a twice-differentiable state");
        }
        double laplacian=differentiable.evaluateWithDerivatives(coordinates).coordinateLaplacian().value().real();
        double x=coordinates.particles().getFirst().xBohr();
        return QuantumAmplitude.real(-kineticCoefficient()*laplacian+potential(x)*state.value(coordinates).real());
    }

    public static double exactGroundEnergyHartree() { return Math.PI*Math.PI/2.0; }
    public static double exactGroundState(double x) { return Math.sqrt(2.0)*Math.sin(Math.PI*x); }
}
