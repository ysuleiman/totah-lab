package totah.lab.prometheus.variational.force;

import java.util.Objects;
import java.util.function.Consumer;

import totah.lab.prometheus.variational.GeometryDifferentiableQuantumState;
import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.HydrogenMoleculeImportanceBatches;

/** One-pass linear contribution trace for the frozen direct HF+Pulay estimator. */
public final class DirectHfPulayForceTrace {
    public Result evaluate(GeometryDifferentiableQuantumState state,HydrogenMoleculeHamiltonian h,
            HydrogenMoleculeImportanceBatches batches,Consumer<LinearContribution> consumer){
        Objects.requireNonNull(state);Objects.requireNonNull(h);Objects.requireNonNull(batches);Objects.requireNonNull(consumer);
        Accumulator a=new Accumulator();batches.forEachBatch(batch->{a.peak=Math.max(a.peak,batch.size());batch.forEach(point->{
            var geometry=state.evaluateWithGeometryDerivatives(point.coordinates());a.evaluations++;var evaluation=geometry.stateEvaluation();
            double psi=evaluation.value().real();if(!Double.isFinite(psi)||Math.abs(psi)<1e-14)return;
            double weight=point.weight()*psi*psi;double energy=-.5*evaluation.coordinateLaplacian().value().real()/psi+h.potential(point.coordinates());
            double explicit=h.potentialDerivativeBondLength(point.coordinates()),o=geometry.geometryLogDerivative();
            double constant=-explicit-2*o*energy,coefficient=2*o,bare=-explicit;
            if(!Double.isFinite(weight)||!Double.isFinite(constant)||!Double.isFinite(coefficient))throw new IllegalArgumentException("non-finite direct force trace");
            a.weight+=weight;a.energy+=weight*energy;a.constant+=weight*constant;a.coefficient+=weight*coefficient;a.bare+=weight*bare;a.accepted++;
            consumer.accept(new LinearContribution(weight,constant,coefficient,bare));});});
        double meanEnergy=a.energy/a.weight;return new Result((a.constant+meanEnergy*a.coefficient)/a.weight,a.bare/a.weight,
                meanEnergy,a.accepted,a.evaluations,a.peak,"hartree/bohr");}
    public record LinearContribution(double importanceWeight,double constantForce,double sampledMeanEnergyCoefficient,double bareForce){ }
    public record Result(double forceHartreePerBohr,double bareForceHartreePerBohr,double sampledMeanEnergyHartree,
            long acceptedSamples,long stateEvaluations,int peakBatchSize,String units){ }
    private static final class Accumulator{double weight,energy,constant,coefficient,bare;long accepted,evaluations;int peak;}
}
