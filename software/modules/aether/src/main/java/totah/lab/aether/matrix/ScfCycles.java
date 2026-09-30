package totah.lab.aether.matrix;

import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.function.Consumer;
import totah.lab.aether.model.QuantumSystem;

/** Shared deterministic SCF state transition, convergence, Pulay history and occupied update. */
final class ScfCycles {
    private ScfCycles(){}
    record Evaluation<T>(double totalEnergy,DensityMatrix plainNext,IntegralMatrixData.Entry fock,T evidence){}
    record Cycle<T>(int number,DensityMatrix input,Evaluation<T> evaluation,DensityMatrix output,OptionalDouble deltaEnergy,
                    double residual,boolean energyPassed,boolean densityPassed,PulayDiis.Step diis,
                    OneShotNumerics.Solution update,OccupiedDensityCalculator.Construction construction,long updateNanos){}
    static <T> RhfScfResult.Status run(QuantumSystem system,OverlapMatrix overlap,DensityMatrix initial,int cap,
                                      Function<DensityMatrix,Evaluation<T>> evaluate,Consumer<Cycle<T>> record) {
        return run(system,overlap,initial,cap,evaluate,record,null);
    }
    static <T> RhfScfResult.Status run(QuantumSystem system,OverlapMatrix overlap,DensityMatrix initial,int cap,
                                      Function<DensityMatrix,Evaluation<T>> evaluate,Consumer<Cycle<T>> record,
                                      Function<OneShotNumerics.Solution,DensityMatrix> validatedUpdate) {
        if(cap<1)throw new IllegalArgumentException("SCF iteration cap must be positive");
        var s=DiisRhfScf.matrix(overlap.size(),overlap::get);var history=new PulayDiis();
        var density=initial;OptionalDouble previous=OptionalDouble.empty();
        for(int iteration=1;iteration<=cap;iteration++) {
            var state=evaluate.apply(density);
            var delta=previous.isPresent()?OptionalDouble.of(Math.abs(state.totalEnergy()-previous.getAsDouble())):OptionalDouble.empty();
            double residual=RhfScfCalculator.densityResidual(density,state.plainNext());
            if(!Double.isFinite(state.totalEnergy())||!Double.isFinite(residual)||(delta.isPresent()&&!Double.isFinite(delta.getAsDouble())))throw new ArithmeticException("Nonfinite SCF metric");
            boolean energyPassed=delta.isPresent()&&delta.getAsDouble()<=RhfScfCalculator.ENERGY_THRESHOLD;
            boolean densityPassed=residual<=RhfScfCalculator.DENSITY_THRESHOLD;
            if(energyPassed&&densityPassed) {
                record.accept(new Cycle<>(iteration,density,state,state.plainNext(),delta,residual,true,true,null,null,null,0));
                return RhfScfResult.Status.CONVERGED;
            }
            var f=DiisRhfScf.matrix(overlap.size(),state.fock());
            var step=history.update(iteration,f,PulayDiis.error(f,DiisRhfScf.matrix(overlap.size(),density::get),s));
            OneShotNumerics.Solution update=null;OccupiedDensityCalculator.Construction construction=null;long nanos=0;
            var next=state.plainNext();
            if(step.extrapolated()) {
                long timer=System.nanoTime();update=OneShotNumerics.solve(s,step.fock());nanos=System.nanoTime()-timer;
                var solved=update;
                if(validatedUpdate==null) {
                    construction=OccupiedDensityCalculator.construct(system,overlap,overlap.size(),update.energies().length,update.coefficients()::getEntry,i->solved.energies()[i]);
                    next=construction.density();
                } else next=validatedUpdate.apply(update);
            }
            record.accept(new Cycle<>(iteration,density,state,next,delta,residual,energyPassed,densityPassed,step,update,construction,nanos));
            previous=OptionalDouble.of(state.totalEnergy());density=next;
        }
        return RhfScfResult.Status.MAX_ITERATIONS;
    }
}
