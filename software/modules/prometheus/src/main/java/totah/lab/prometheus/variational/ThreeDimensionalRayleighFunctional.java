package totah.lab.prometheus.variational;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Three-dimensional Rayleigh quotient using volume-weighted points and shared state evaluations. */
public final class ThreeDimensionalRayleighFunctional implements VariationalFunctional {
    private static final double MIN_NORM=1e-14;
    @Override public String functionalId() { return "rayleigh-quotient-3d-coulomb-v1"; }

    @Override public FunctionalEvaluation evaluate(DifferentiableQuantumState state,Hamiltonian hamiltonian,
            CollocationPointSet points) {
        if(!(hamiltonian instanceof HydrogenAtomHamiltonian hydrogen)) {
            throw new IllegalArgumentException("hydrogenic Hamiltonian is required");
        }
        double norm=0.0,kinetic=0.0,potential=0.0;
        List<ResidualTerm> terms=new ArrayList<>(points.points().size());
        for(var point:points.points()) {
            var evaluation=state.evaluateWithDerivatives(point.coordinates());
            var coordinate=point.coordinates().particles().getFirst();
            double r=Math.sqrt(coordinate.xBohr()*coordinate.xBohr()+coordinate.yBohr()*coordinate.yBohr()
                    +coordinate.zBohr()*coordinate.zBohr());
            double psi=evaluation.value().real();
            var gradient=evaluation.coordinateGradient().particleGradients().getFirst();
            double gradientSquared=gradient.x().real()*gradient.x().real()+gradient.y().real()*gradient.y().real()
                    +gradient.z().real()*gradient.z().real();
            double potentialValue=-hydrogen.nuclearCharge()/r;
            double hPsi=-0.5*evaluation.coordinateLaplacian().value().real()+potentialValue*psi;
            norm+=point.weight()*psi*psi;
            kinetic+=point.weight()*0.5*gradientSquared;
            potential+=point.weight()*potentialValue*psi*psi;
            terms.add(new ResidualTerm(point.weight(),psi,hPsi));
        }
        if(norm<MIN_NORM) return new FunctionalEvaluation(Double.MAX_VALUE,Map.of("norm",norm));
        double energy=(kinetic+potential)/norm,residual=0.0;
        for(var term:terms) {
            double difference=term.hPsi()-energy*term.psi();
            residual+=term.weight()*difference*difference;
        }
        return new FunctionalEvaluation(energy,Map.of("norm",norm,"kinetic",kinetic/norm,
                "potential",potential/norm,"residual_rms",Math.sqrt(residual/norm)));
    }

    private record ResidualTerm(double weight,double psi,double hPsi) { }
}
