package totah.lab.prometheus.variational;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Importance-weighted helium energy, virial components, residual, and local-energy variance. */
public final class HeliumRayleighFunctional implements VariationalFunctional {
    @Override public String functionalId() { return "helium-rayleigh-importance-v1"; }
    @Override public FunctionalEvaluation evaluate(DifferentiableQuantumState state,Hamiltonian hamiltonian,
            CollocationPointSet points) {
        if(!(hamiltonian instanceof HeliumHamiltonian)) throw new IllegalArgumentException("helium Hamiltonian required");
        double norm=0,kinetic=0,potential=0; List<Term> terms=new ArrayList<>(points.points().size());
        for(var point:points.points()) {
            var evaluation=state.evaluateWithDerivatives(point.coordinates());
            var first=point.coordinates().particles().get(0); var second=point.coordinates().particles().get(1);
            double r1=radius(first.xBohr(),first.yBohr(),first.zBohr());
            double r2=radius(second.xBohr(),second.yBohr(),second.zBohr());
            double r12=radius(first.xBohr()-second.xBohr(),first.yBohr()-second.yBohr(),first.zBohr()-second.zBohr());
            double psi=evaluation.value().real(),weight=point.weight();
            double potentialValue=-2.0/r1-2.0/r2+1.0/r12;
            double kineticPsi=-0.5*evaluation.coordinateLaplacian().value().real();
            double hPsi=kineticPsi+potentialValue*psi;
            norm+=weight*psi*psi; kinetic+=weight*psi*kineticPsi; potential+=weight*psi*psi*potentialValue;
            terms.add(new Term(weight,psi,hPsi));
        }
        if(norm<1e-14) return new FunctionalEvaluation(Double.MAX_VALUE,Map.of("norm",norm));
        double energy=(kinetic+potential)/norm,variance=0,residual=0;
        for(var term:terms) {
            double local=term.hPsi/term.psi,difference=local-energy;
            variance+=term.weight*term.psi*term.psi*difference*difference;
            double equationResidual=term.hPsi-energy*term.psi;
            residual+=term.weight*equationResidual*equationResidual;
        }
        return new FunctionalEvaluation(energy,Map.of("norm",norm,"kinetic",kinetic/norm,
                "potential",potential/norm,"virial_ratio",-2.0*(kinetic/norm)/(potential/norm),
                "local_energy_variance",variance/norm,"residual_rms",Math.sqrt(residual/norm),
                "state_evaluations",(double)points.points().size()));
    }
    private static double radius(double x,double y,double z) { return Math.sqrt(x*x+y*y+z*z); }
    private record Term(double weight,double psi,double hPsi) { }
}
