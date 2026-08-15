package totah.lab.prometheus.variational.force;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.variational.GeometryDifferentiableQuantumState;
import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.HydrogenMoleculeImportanceBatches;

/** Locked central PES derivative with paired deterministic configurations. */
public final class CorrelatedSamplingFiniteDifferenceForceEstimator {
    public static final double STEP_BOHR=1e-3;
    public Result evaluate(GeometryDifferentiableQuantumState center,HydrogenMoleculeImportanceBatches batches){
        Objects.requireNonNull(center);Objects.requireNonNull(batches);double r=center.geometryCoordinateBohr();
        var plus=center.atGeometry(r+STEP_BOHR);var minus=center.atGeometry(r-STEP_BOHR);
        var hp=new HydrogenMoleculeHamiltonian(r+STEP_BOHR);var hm=new HydrogenMoleculeHamiltonian(r-STEP_BOHR);
        List<Raw> raw=new ArrayList<>(batches.count());double dp=0,dm=0,np=0,nm=0;long evaluations=0;int peak=0;
        final double[] sums=new double[4];final long[] count={0};final int[] max={0};
        batches.forEachBatch(batch->{max[0]=Math.max(max[0],batch.size());batch.forEach(point->{var ep=plus.evaluateWithDerivatives(point.coordinates());
            var em=minus.evaluateWithDerivatives(point.coordinates());count[0]+=2;double pp=ep.value().real(),pm=em.value().real();
            if(Math.abs(pp)<1e-14||Math.abs(pm)<1e-14)return;double wp=point.weight()*pp*pp,wm=point.weight()*pm*pm;
            double lp=-.5*ep.coordinateLaplacian().value().real()/pp+hp.potential(point.coordinates());
            double lm=-.5*em.coordinateLaplacian().value().real()/pm+hm.potential(point.coordinates());
            sums[0]+=wp;sums[1]+=wm;sums[2]+=wp*lp;sums[3]+=wm*lm;raw.add(new Raw(wp,wm,lp,lm));});});
        dp=sums[0];dm=sums[1];np=sums[2];nm=sums[3];evaluations=count[0];peak=max[0];double ep=np/dp,em=nm/dm;
        double force=-(ep-em)/(2*STEP_BOHR);List<Double> sampleForces=new ArrayList<>(raw.size());int n=raw.size();
        for(Raw value:raw)sampleForces.add(-n*(value.wp()*value.lp()/dp-value.wm()*value.lm()/dm)/(2*STEP_BOHR));
        return new Result(force,ep,em,List.copyOf(sampleForces),evaluations,evaluations,raw.size(),peak,STEP_BOHR,"hartree/bohr");}
    public record Result(double forceHartreePerBohr,double plusEnergyHartree,double minusEnergyHartree,
            List<Double> equalWeightPairedForceContributions,long stateEvaluations,long localEnergyEvaluations,
            long pairedSamples,int peakBatchSize,double stepBohr,String units){public Result{equalWeightPairedForceContributions=List.copyOf(equalWeightPairedForceContributions);}}
    private record Raw(double wp,double wm,double lp,double lm){ }
}
