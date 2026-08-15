package totah.lab.prometheus.variational;

import java.util.Map;

/** Fixed-R H2 Rayleigh quotient with instrumented shared state evaluations. */
public final class HydrogenMoleculeRayleighFunctional implements VariationalFunctional {
    @Override public String functionalId() { return "h2-rayleigh-shared-evaluation-v1"; }
    @Override public FunctionalEvaluation evaluate(DifferentiableQuantumState state,Hamiltonian hamiltonian,
            CollocationPointSet points) {
        if(!(hamiltonian instanceof HydrogenMoleculeHamiltonian molecular)) {
            throw new IllegalArgumentException("H2 Hamiltonian required");
        }
        double norm=0,kinetic=0,potential=0,localEnergySquare=0; long evaluations=0;
        for(var point:points.points()) {
            var bundle=state.evaluateWithDerivatives(point.coordinates()); evaluations++;
            double psi=bundle.value().real(),weight=point.weight(),v=molecular.potential(point.coordinates());
            double kineticPsi=-0.5*bundle.coordinateLaplacian().value().real(),hPsi=kineticPsi+v*psi;
            norm+=weight*psi*psi; kinetic+=weight*psi*kineticPsi; potential+=weight*psi*psi*v;
            if(Math.abs(psi)>1e-300) localEnergySquare+=weight*psi*psi*(hPsi/psi)*(hPsi/psi);
        }
        if(norm<1e-14) return new FunctionalEvaluation(Double.MAX_VALUE,Map.of("norm",norm));
        double energy=(kinetic+potential)/norm;
        double variance=Math.max(0,localEnergySquare/norm-energy*energy);
        return new FunctionalEvaluation(energy,Map.of("norm",norm,"kinetic",kinetic/norm,
                "potential",potential/norm,"virial_ratio",-2*(kinetic/norm)/(potential/norm),
                "local_energy_variance",variance,"state_evaluations",(double)evaluations,
                "expected_state_evaluations",(double)points.points().size(),"redundant_state_evaluations",0.0));
    }
}
