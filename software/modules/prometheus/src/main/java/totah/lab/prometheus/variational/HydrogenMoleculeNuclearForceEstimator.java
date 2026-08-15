package totah.lab.prometheus.variational;

import java.util.Objects;

/** Streaming normalized-Rayleigh nuclear-force estimator for fixed-axis H2. */
public final class HydrogenMoleculeNuclearForceEstimator {
    public Result evaluate(GeometryDifferentiableQuantumState state,
            HydrogenMoleculeHamiltonian hamiltonian, HydrogenMoleculeImportanceBatches batches) {
        Objects.requireNonNull(state,"state"); Objects.requireNonNull(hamiltonian,"hamiltonian");
        Objects.requireNonNull(batches,"batches");
        if (Double.doubleToLongBits(state.geometryCoordinateBohr())
                != Double.doubleToLongBits(hamiltonian.bondLengthBohr())) {
            throw new IllegalArgumentException("state and Hamiltonian bond lengths must match exactly");
        }
        Accumulator a=new Accumulator();
        batches.forEachBatch(batch -> batch.forEach(point -> {
            GeometryStateEvaluation geometryBundle=state.evaluateWithGeometryDerivatives(point.coordinates());
            a.evaluations++;
            DifferentiableStateEvaluation bundle=geometryBundle.stateEvaluation();
            double psi=bundle.value().real();
            if(!Double.isFinite(psi)||Math.abs(psi)<1e-14) return;
            double weight=point.weight()*psi*psi;
            double localEnergy=-0.5*bundle.coordinateLaplacian().value().real()/psi
                    +hamiltonian.potential(point.coordinates());
            double response=geometryBundle.geometryLogDerivative();
            double explicitDerivative=hamiltonian.potentialDerivativeBondLength(point.coordinates());
            if(!Double.isFinite(weight)||!Double.isFinite(localEnergy)||!Double.isFinite(response)
                    ||!Double.isFinite(explicitDerivative)) {
                throw new IllegalArgumentException("non-finite H2 force contribution");
            }
            a.norm+=weight;a.energy+=weight*localEnergy;a.explicit+=weight*explicitDerivative;
            a.response+=weight*response;a.responseEnergy+=weight*response*localEnergy;
            double rawDerivative=explicitDerivative+2*response*localEnergy;
            a.rawDerivativeSquare+=weight*rawDerivative*rawDerivative;
            a.rawDerivativeResponse+=weight*rawDerivative*response;
            a.responseSquare+=weight*response*response;
        }));
        if(!Double.isFinite(a.norm)||a.norm<1e-14) throw new IllegalArgumentException("zero sampled norm");
        double energy=a.energy/a.norm;
        double hellmannFeynman=a.explicit/a.norm;
        double pulay=2*(a.responseEnergy/a.norm-(a.response/a.norm)*energy);
        double derivative=hellmannFeynman+pulay;
        double secondMoment=a.rawDerivativeSquare/a.norm
                -4*energy*a.rawDerivativeResponse/a.norm
                +4*energy*energy*a.responseSquare/a.norm;
        double variance=Math.max(0,secondMoment-derivative*derivative);
        return new Result(-derivative,-hellmannFeynman,-pulay,derivative,hellmannFeynman,pulay,
                energy,variance,a.evaluations,batches.count(),0,"hartree/bohr");
    }

    /** Force is -dE/dR; component fields expose both force and energy-derivative signs. */
    public record Result(double forceHartreePerBohr,double hellmannFeynmanForceHartreePerBohr,
            double pulayForceHartreePerBohr,double energyDerivativeHartreePerBohr,
            double explicitEnergyDerivativeHartreePerBohr,double wavefunctionResponseEnergyDerivativeHartreePerBohr,
            double energyHartree,double forceEstimatorVarianceHartree2PerBohr2,
            long stateEvaluations,long expectedStateEvaluations,
            long redundantStateEvaluations,String forceUnits) {
        public Result { Objects.requireNonNull(forceUnits,"forceUnits"); }
    }
    private static final class Accumulator {
        private double norm,energy,explicit,response,responseEnergy;
        private double rawDerivativeSquare,rawDerivativeResponse,responseSquare;
        private long evaluations;
    }
}
