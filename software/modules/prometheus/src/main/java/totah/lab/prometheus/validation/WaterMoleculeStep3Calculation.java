package totah.lab.prometheus.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.molecular.GeneralMolecularCoulombHamiltonian;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.neural.GeneralSlaterJastrowState;
import totah.lab.prometheus.variational.GeneralMolecularImportanceBatches;
import totah.lab.prometheus.variational.GeneralMolecularMatrixFreeSrOptimizer;
import totah.lab.prometheus.variational.force.GeneralAnalyticDifferentialSwctForceEstimator;

/** Frozen Step-3 H2O calculation: existing general state, BLOCK SR, and vector-force estimator. */
public final class WaterMoleculeStep3Calculation {
    private static final int[] EVALUATION_SKIPS={1009,2017,3019,4027};

    public Result run(Molecule molecule) {
        Objects.requireNonNull(molecule);long start=System.nanoTime();long heapBefore=usedHeap(),peakHeap=heapBefore;
        var state=GeneralSlaterJastrowState.cuspInitialized(molecule);
        var hamiltonian=new GeneralMolecularCoulombHamiltonian(molecule);
        var optimizer=new GeneralMolecularMatrixFreeSrOptimizer();
        var configuration=new GeneralMolecularMatrixFreeSrOptimizer.Configuration(.02,.01,.10,2,200,1e-10,1e-12);
        List<Double> residuals=new ArrayList<>();long stateEvaluations=0,operatorPasses=0;
        for(int iteration=0;iteration<4;iteration++) {
            var training=new GeneralMolecularImportanceBatches(molecule,512,4.0,101,64);
            var optimized=optimizer.oneIteration(state,hamiltonian,training,configuration);
            state=optimized.state();residuals.add(optimized.relativeTrueResidual());
            stateEvaluations+=optimized.initialStateEvaluations();operatorPasses+=optimized.streamedOperatorPasses();
            peakHeap=Math.max(peakHeap,usedHeap());
        }
        List<Double> blockEnergies=new ArrayList<>();List<List<GeneralAnalyticDifferentialSwctForceEstimator.NuclearForce>> blockForces=new ArrayList<>();
        long localEnergyEvaluations=0,directionalPasses=0,forceTraversals=0;
        for(int skip:EVALUATION_SKIPS) {
            var source=new GeneralMolecularImportanceBatches(molecule,128,4.0,skip,64);
            var evaluated=new GeneralAnalyticDifferentialSwctForceEstimator().evaluate(state,source);
            blockEnergies.add(evaluated.energyHartree());blockForces.add(evaluated.forces());
            localEnergyEvaluations+=evaluated.localEnergyEvaluations();directionalPasses+=evaluated.directionalAdPasses();forceTraversals+=evaluated.stateTraversals();
            peakHeap=Math.max(peakHeap,evaluated.peakHeapBytes());
        }
        double energy=mean(blockEnergies),energyVariance=sampleVariance(blockEnergies),energySe=Math.sqrt(energyVariance/blockEnergies.size());
        List<GeneralAnalyticDifferentialSwctForceEstimator.NuclearForce> forces=new ArrayList<>();List<ForceUncertainty> uncertainty=new ArrayList<>();
        for(int nucleus=0;nucleus<molecule.nuclei().size();nucleus++) {
            double[] x=new double[blockForces.size()],y=x.clone(),z=x.clone();
            for(int block=0;block<blockForces.size();block++){var f=blockForces.get(block).get(nucleus);x[block]=f.fx();y[block]=f.fy();z[block]=f.fz();}
            forces.add(new GeneralAnalyticDifferentialSwctForceEstimator.NuclearForce(nucleus,mean(x),mean(y),mean(z),Double.NaN,Double.NaN,Double.NaN));
            uncertainty.add(new ForceUncertainty(nucleus,se(x),se(y),se(z)));
        }
        peakHeap=Math.max(peakHeap,usedHeap());
        return new Result(energy,energyVariance,energySe,forces,uncertainty,List.copyOf(residuals),state.parameters().values(),
                stateEvaluations,operatorPasses,forceTraversals,localEnergyEvaluations,directionalPasses,
                System.nanoTime()-start,Math.max(0,peakHeap-heapBefore),"BLOCK_PRECONDITIONED_MATRIX_FREE_SR","GENERAL_ANALYTIC_DIFFERENTIAL_SWCT");
    }

    private static long usedHeap(){Runtime runtime=Runtime.getRuntime();return runtime.totalMemory()-runtime.freeMemory();}
    private static double mean(List<Double> values){return values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();}
    private static double mean(double[] values){double sum=0;for(double value:values)sum+=value;return sum/values.length;}
    private static double sampleVariance(List<Double> values){double mean=mean(values),sum=0;for(double value:values)sum+=(value-mean)*(value-mean);return sum/(values.size()-1);}
    private static double sampleVariance(double[] values){double mean=mean(values),sum=0;for(double value:values)sum+=(value-mean)*(value-mean);return sum/(values.length-1);}
    private static double se(double[] values){return Math.sqrt(sampleVariance(values)/values.length);}

    public record ForceUncertainty(int canonicalNucleusIndex,double fxStandardError,double fyStandardError,double fzStandardError) { }
    public record Result(double energyHartree,double energyBlockVariance,double energyStandardError,
            List<GeneralAnalyticDifferentialSwctForceEstimator.NuclearForce> forces,List<ForceUncertainty> forceUncertainty,
            List<Double> srRelativeTrueResiduals,List<Double> parameters,long optimizationStateEvaluations,
            long streamedOperatorPasses,long forceStateTraversals,long localEnergyEvaluations,long directionalAdPasses,
            long wallTimeNanos,long peakHeapGrowthBytes,String optimizer,String forceEstimator) {
        public Result { forces=List.copyOf(forces);forceUncertainty=List.copyOf(forceUncertainty);srRelativeTrueResiduals=List.copyOf(srRelativeTrueResiduals);parameters=List.copyOf(parameters); }
    }
}
