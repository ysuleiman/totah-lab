package totah.lab.prometheus.variational;

/** Local one-dimensional Hamiltonian used by shared energy/derivative evaluation. */
public interface OneDimensionalLocalHamiltonian extends Hamiltonian {
    double kineticCoefficient();
    double potential(double xBohr);
}
