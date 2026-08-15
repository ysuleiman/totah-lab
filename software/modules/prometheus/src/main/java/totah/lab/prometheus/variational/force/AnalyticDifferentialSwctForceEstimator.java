package totah.lab.prometheus.variational.force;

import java.util.Objects;
import java.util.function.Consumer;

import totah.lab.prometheus.neural.GeometryConditionedHydrogenMoleculeDirectionalEvaluator;
import totah.lab.prometheus.neural.GeometryConditionedHydrogenMoleculeState;
import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.HydrogenMoleculeImportanceBatches;
import totah.lab.prometheus.variational.QuantumCoordinates;

/** Sorella-Capriotti Eqs. 14-16 using one fused directional state graph. */
public final class AnalyticDifferentialSwctForceEstimator {
    public Result evaluate(GeometryConditionedHydrogenMoleculeState state,HydrogenMoleculeHamiltonian hamiltonian,
            HydrogenMoleculeImportanceBatches batches){return evaluate(state,hamiltonian,batches,ignored->{ });}
    public Result evaluate(GeometryConditionedHydrogenMoleculeState state,HydrogenMoleculeHamiltonian hamiltonian,
            HydrogenMoleculeImportanceBatches batches,Consumer<Contribution> consumer){
        Objects.requireNonNull(state);Objects.requireNonNull(hamiltonian);Objects.requireNonNull(batches);Objects.requireNonNull(consumer);
        double radius=hamiltonian.bondLengthBohr();if(Double.doubleToLongBits(radius)!=Double.doubleToLongBits(state.geometryCoordinateBohr()))throw new IllegalArgumentException("state/Hamiltonian geometry mismatch");
        Accumulator a=new Accumulator();var evaluator=new GeometryConditionedHydrogenMoleculeDirectionalEvaluator();
        batches.forEachBatch(batch->{a.peakBatch=Math.max(a.peakBatch,batch.size());batch.forEach(point->accumulate(point.coordinates(),point.weight(),state,hamiltonian,evaluator,a,consumer));});
        if(!(a.norm>1e-14)||!Double.isFinite(a.norm))throw new IllegalArgumentException("zero sampled norm");double energy=a.energy/a.norm;
        double force=a.base/a.norm+2*energy*a.logDerivative/a.norm;double bare=a.bareForce/a.norm;double totalHfm=a.totalHfm/a.norm;
        double variance=Math.max(0,(a.baseSquare+4*energy*a.baseLog+4*energy*energy*a.logSquare)/a.norm-force*force);
        return new Result(force,bare,totalHfm-bare,force-totalHfm,energy,variance,a.configurations,a.stateTraversals,
                a.localEnergyEvaluations,a.directionalGraphPasses,a.peakBatch,"ANALYTIC_DIRECTIONAL_FORWARD_OVER_SPATIAL_SECOND_ORDER","hartree/bohr");
    }
    private static void accumulate(QuantumCoordinates coordinates,double quadratureWeight,GeometryConditionedHydrogenMoleculeState state,
            HydrogenMoleculeHamiltonian h,GeometryConditionedHydrogenMoleculeDirectionalEvaluator evaluator,Accumulator a,Consumer<Contribution> consumer){
        double half=h.bondLengthBohr()/2;double[] velocity=new double[2];double divergence=0;
        for(int i=0;i<2;i++){var wd=HydrogenMoleculeSpaceWarp.weightAndDerivative(coordinates.particles().get(i),half);velocity[i]=wd.weightAtPositiveNucleus()-.5;divergence+=wd.zDerivative();}
        var evaluation=evaluator.evaluate(h.bondLengthBohr(),state.parameters(),coordinates,velocity);a.stateTraversals++;a.directionalGraphPasses++;
        double psi=evaluation.value();if(!Double.isFinite(psi)||Math.abs(psi)<1e-14)return;double lap=evaluation.laplacian();
        double potential=h.potential(coordinates),local=-.5*lap/psi+potential;a.localEnergyEvaluations++;
        double[] localDerivative=new double[2];double barePotential=h.potentialDerivativeBondLength(coordinates);
        double totalPotential=barePotential+potentialWarpDerivative(coordinates,h.bondLengthBohr(),velocity);
        for(int direction=0;direction<2;direction++){double dpsi=evaluation.valueDirectionalDerivatives()[direction],dlap=evaluation.laplacianDirectionalDerivatives()[direction];double dpotential=direction==0?totalPotential:barePotential;localDerivative[direction]=-.5*(dlap*psi-lap*dpsi)/(psi*psi)+dpotential;}
        double logDerivative=evaluation.valueDirectionalDerivatives()[0]/psi+.5*divergence;double hfm=-localDerivative[0];double base=hfm-2*local*logDerivative;double weight=quadratureWeight*psi*psi;
        if(!Double.isFinite(weight)||!Double.isFinite(local)||!Double.isFinite(base)||!Double.isFinite(logDerivative)||!Double.isFinite(localDerivative[1]))throw new IllegalArgumentException("non-finite analytic SWCT contribution");
        a.norm+=weight;a.energy+=weight*local;a.base+=weight*base;a.logDerivative+=weight*logDerivative;a.totalHfm+=weight*hfm;a.bareForce+=weight*-localDerivative[1];a.baseSquare+=weight*base*base;a.baseLog+=weight*base*logDerivative;a.logSquare+=weight*logDerivative*logDerivative;a.configurations++;
        consumer.accept(new Contribution(weight,local,localDerivative[0],localDerivative[1],logDerivative,base));
    }
    private static double potentialWarpDerivative(QuantumCoordinates c,double radius,double[] velocity){var one=c.particles().get(0);var two=c.particles().get(1);double half=radius/2;double dz=one.zBohr()-two.zBohr();double r12=cubeRootDistance(one.xBohr()-two.xBohr(),one.yBohr()-two.yBohr(),dz);double g1=(one.zBohr()+half)/cubeDistance(one.xBohr(),one.yBohr(),one.zBohr()+half)+(one.zBohr()-half)/cubeDistance(one.xBohr(),one.yBohr(),one.zBohr()-half)-dz/r12;double g2=(two.zBohr()+half)/cubeDistance(two.xBohr(),two.yBohr(),two.zBohr()+half)+(two.zBohr()-half)/cubeDistance(two.xBohr(),two.yBohr(),two.zBohr()-half)+dz/r12;return velocity[0]*g1+velocity[1]*g2;}
    private static double cubeDistance(double x,double y,double z){double r2=x*x+y*y+z*z;return r2*Math.sqrt(r2);}
    private static double cubeRootDistance(double x,double y,double z){return cubeDistance(x,y,z);}
    public record Contribution(double importanceWeight,double localEnergy,double totalLocalEnergyDerivative,double bareLocalEnergyDerivative,double logJacobianStateDerivative,double baseForce){ }
    public record Result(double forceHartreePerBohr,double bareHfmForceHartreePerBohr,double warpHfmForceHartreePerBohr,double pulayForceHartreePerBohr,double energyHartree,double forceEstimatorVarianceHartree2PerBohr2,long configurations,long stateTraversals,long localEnergyEvaluations,long directionalGraphPasses,int peakBatchSize,String derivativeImplementation,String forceUnits){ }
    private static final class Accumulator{double norm,energy,base,logDerivative,totalHfm,bareForce,baseSquare,baseLog,logSquare;long configurations,stateTraversals,localEnergyEvaluations,directionalGraphPasses;int peakBatch;}
}
