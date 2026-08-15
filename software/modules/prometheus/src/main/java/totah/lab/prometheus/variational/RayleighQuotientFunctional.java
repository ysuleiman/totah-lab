package totah.lab.prometheus.variational;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Rayleigh quotient for a one-dimensional state, using shared state derivatives. */
public final class RayleighQuotientFunctional implements VariationalFunctional {
    private static final double MIN_NORM=1e-14;
    @Override public String functionalId() { return "rayleigh-quotient-1d-v1"; }

    @Override public FunctionalEvaluation evaluate(DifferentiableQuantumState state, Hamiltonian hamiltonian,
            CollocationPointSet points) {
        if (!(hamiltonian instanceof OneDimensionalLocalHamiltonian local)) {
            throw new IllegalArgumentException("one-dimensional local Hamiltonian is required");
        }
        double norm=0.0,kinetic=0.0,potential=0.0,residualAccumulator=0.0;
        List<ResidualTerm> residualTerms=new ArrayList<>(points.points().size());
        for(var point:points.points()) {
            DifferentiableStateEvaluation evaluation=state.evaluateWithDerivatives(point.coordinates());
            double psi=evaluation.value().real();
            double derivative=evaluation.coordinateGradient().particleGradients().getFirst().x().real();
            double x=point.coordinates().particles().getFirst().xBohr();
            double hPsi=-local.kineticCoefficient()*evaluation.coordinateLaplacian().value().real()
                    +local.potential(x)*psi;
            norm+=point.weight()*psi*psi; kinetic+=point.weight()*local.kineticCoefficient()*derivative*derivative;
            potential+=point.weight()*local.potential(x)*psi*psi;
            residualTerms.add(new ResidualTerm(point.weight(),psi,hPsi));
        }
        if(norm<MIN_NORM) return new FunctionalEvaluation(Double.MAX_VALUE,Map.of("norm",norm));
        double energy=(kinetic+potential)/norm;
        for(ResidualTerm term:residualTerms) {
            double residual=term.hPsi()-energy*term.psi();
            residualAccumulator+=term.weight()*residual*residual;
        }
        return new FunctionalEvaluation(energy,Map.of("norm",norm,"kinetic",kinetic/norm,
                "potential",potential/norm,"residual_rms",Math.sqrt(residualAccumulator)));
    }

    private record ResidualTerm(double weight,double psi,double hPsi) { }
}
