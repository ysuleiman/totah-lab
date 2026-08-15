package totah.lab.prometheus.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.neural.GeometryConditionedHydrogenMoleculeState;
import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.HydrogenMoleculeImportanceBatches;
import totah.lab.prometheus.variational.ParameterVector;

/** Locked reference-assisted Taylor/secant diagnostic; not a production VMC optimizer. */
final class DerivativeAwarePesDiagnosticOptimizer {
    static final double ENERGY_SCALE_HARTREE=.015;
    static final double FORCE_SCALE_HARTREE_PER_BOHR=.030;
    static final double FORCE_RADIUS_BOHR=1.0;
    static final double DIAGNOSTIC_DELTA_BOHR=.05;
    static final double FORCE_REFERENCE=.3621964426997232;
    static final int BATCH_SIZE=512;
    private static final double MINIMUM_NORM=1e-14;
    private final Configuration configuration;
    private final double[] radii;
    private final double[] references;

    DerivativeAwarePesDiagnosticOptimizer(Configuration configuration,double[] radii,double[] references) {
        this.configuration=Objects.requireNonNull(configuration);
        this.radii=radii.clone();this.references=references.clone();
        if(radii.length==0||radii.length!=references.length)throw new IllegalArgumentException("matching radii/references required");
    }

    Result optimize(ParameterVector initial) {
        Objects.requireNonNull(initial);ParameterVector current=initial;List<Double> history=new ArrayList<>();
        long stateEvaluations=0,localEnergyEvaluations=0,forceEvaluations=0,displacedEvaluations=0,samples=0;
        long peakHeap=usedHeap(),start=System.nanoTime();int stale=0,completed=0;double best=Double.POSITIVE_INFINITY;
        for(int iteration=0;iteration<configuration.maximumIterations;iteration++) {
            Statistics statistics=statistics(current);history.add(statistics.loss);stateEvaluations+=statistics.stateEvaluations;
            localEnergyEvaluations+=statistics.localEnergyEvaluations;forceEvaluations++;displacedEvaluations+=statistics.displacedStateEvaluations;
            samples+=statistics.sampleCount;peakHeap=Math.max(peakHeap,usedHeap());
            double[] direction=solve(statistics.covariance,statistics.scaledGradient,configuration.diagonalRegularization);
            List<Double> next=new ArrayList<>(current.values());
            for(int i=0;i<next.size();i++) {double update=-configuration.learningRate*direction[i];
                update=Math.max(-configuration.maximumAbsoluteUpdate,Math.min(configuration.maximumAbsoluteUpdate,update));
                next.set(i,next.get(i)+update);}
            current=new ParameterVector(next);completed=iteration+1;
            if(best-statistics.loss>configuration.improvementTolerance){best=statistics.loss;stale=0;}else stale++;
            if(completed>=configuration.minimumIterations&&stale>=configuration.patience)break;
        }
        Statistics finalStatistics=statistics(current);history.add(finalStatistics.loss);stateEvaluations+=finalStatistics.stateEvaluations;
        localEnergyEvaluations+=finalStatistics.localEnergyEvaluations;forceEvaluations++;displacedEvaluations+=finalStatistics.displacedStateEvaluations;
        samples+=finalStatistics.sampleCount;peakHeap=Math.max(peakHeap,usedHeap());
        return new Result(current,finalStatistics.loss,finalStatistics.energyLoss,finalStatistics.forceLoss,
                finalStatistics.force,finalStatistics.meanEnergy,completed,completed+1,stateEvaluations,
                localEnergyEvaluations,forceEvaluations,displacedEvaluations,samples,System.nanoTime()-start,
                peakHeap,completed<configuration.maximumIterations,List.copyOf(history));
    }

    ObjectiveEvaluation objectiveEvaluation(ParameterVector parameters) {
        Statistics value=statistics(parameters);
        return new ObjectiveEvaluation(value.loss,value.rawGradient.clone(),value.scaledGradient.clone(),
                value.force,value.stateEvaluations,value.localEnergyEvaluations,value.displacedStateEvaluations,value.sampleCount);
    }

    private Statistics statistics(ParameterVector parameters) {
        int p=parameters.values().size();double[][] covariance=new double[p][p];double[] energyGradient=new double[p];
        double[] diagnosticGradient=new double[p];double energyLoss=0,meanEnergy=0;long evaluations=0,localEvals=0,samples=0;
        for(int r=0;r<radii.length;r++) {Single value=single(parameters,radii[r],batches(configuration.sampleCount,radii[r]));
            evaluations+=value.stateEvaluations;localEvals+=value.localEnergyEvaluations;samples+=value.sampleCount;meanEnergy+=value.energy/radii.length;
            double residual=value.energy-references[r];energyLoss+=residual*residual/(ENERGY_SCALE_HARTREE*ENERGY_SCALE_HARTREE*radii.length);
            for(int i=0;i<p;i++) {energyGradient[i]+=value.gradient[i]/radii.length;
                diagnosticGradient[i]+=2*residual*value.gradient[i]/(ENERGY_SCALE_HARTREE*ENERGY_SCALE_HARTREE*radii.length);
                for(int j=0;j<p;j++)covariance[i][j]+=value.covariance[i][j]/radii.length;}}
        HydrogenMoleculeImportanceBatches common=batches(configuration.sampleCount,FORCE_RADIUS_BOHR);
        Single minus=single(parameters,FORCE_RADIUS_BOHR-DIAGNOSTIC_DELTA_BOHR,common);
        Single plus=single(parameters,FORCE_RADIUS_BOHR+DIAGNOSTIC_DELTA_BOHR,common);
        evaluations+=minus.stateEvaluations+plus.stateEvaluations;localEvals+=minus.localEnergyEvaluations+plus.localEnergyEvaluations;
        samples+=minus.sampleCount+plus.sampleCount;
        double force=-(plus.energy-minus.energy)/(2*DIAGNOSTIC_DELTA_BOHR);double forceResidual=force-FORCE_REFERENCE;
        double forceLoss=forceResidual*forceResidual/(FORCE_SCALE_HARTREE_PER_BOHR*FORCE_SCALE_HARTREE_PER_BOHR);
        for(int i=0;i<p;i++) {double forceGradient=-(plus.gradient[i]-minus.gradient[i])/(2*DIAGNOSTIC_DELTA_BOHR);
            diagnosticGradient[i]+=2*forceResidual*forceGradient/(FORCE_SCALE_HARTREE_PER_BOHR*FORCE_SCALE_HARTREE_PER_BOHR);}
        double diagnosticNorm=norm(diagnosticGradient),energyNorm=norm(energyGradient);double[] scaled=diagnosticGradient.clone();
        if(diagnosticNorm>=MINIMUM_NORM&&energyNorm>=MINIMUM_NORM) {double scale=energyNorm/diagnosticNorm;
            for(int i=0;i<p;i++)scaled[i]*=scale;}
        return new Statistics(energyLoss+forceLoss,energyLoss,forceLoss,force,meanEnergy,diagnosticGradient,scaled,
                covariance,evaluations,localEvals,minus.stateEvaluations+plus.stateEvaluations,samples);
    }

    private Single single(ParameterVector parameters,double radius,HydrogenMoleculeImportanceBatches batches) {
        var state=new GeometryConditionedHydrogenMoleculeState(radius,parameters);var h=new HydrogenMoleculeHamiltonian(radius);
        int p=parameters.values().size();Mutable m=new Mutable(p);
        batches.forEachBatch(batch->batch.forEach(point->{var bundle=state.evaluateWithGeometryDerivatives(point.coordinates()).stateEvaluation();
            m.evaluations++;m.localEnergyEvaluations++;m.samples++;double psi=bundle.value().real();
            if(!Double.isFinite(psi)||Math.abs(psi)<MINIMUM_NORM)return;double weight=point.weight()*psi*psi;
            double local=-.5*bundle.coordinateLaplacian().value().real()/psi+h.potential(point.coordinates());
            double[] observable=new double[p];for(int i=0;i<p;i++)observable[i]=bundle.parameterGradient().derivatives().get(i).real()/psi;
            m.norm+=weight;m.energy+=weight*local;for(int i=0;i<p;i++){m.o[i]+=weight*observable[i];m.oe[i]+=weight*observable[i]*local;
                for(int j=0;j<p;j++)m.oo[i][j]+=weight*observable[i]*observable[j];}}));
        if(!Double.isFinite(m.norm)||m.norm<MINIMUM_NORM)throw new IllegalArgumentException("invalid diagnostic norm");
        double energy=m.energy/m.norm;double[] gradient=new double[p];double[][] covariance=new double[p][p];
        for(int i=0;i<p;i++){double mean=m.o[i]/m.norm;gradient[i]=2*(m.oe[i]/m.norm-mean*energy);
            for(int j=0;j<p;j++)covariance[i][j]=m.oo[i][j]/m.norm-mean*(m.o[j]/m.norm);}
        return new Single(energy,gradient,covariance,m.evaluations,m.localEnergyEvaluations,m.samples);
    }

    private HydrogenMoleculeImportanceBatches batches(int count,double radius) {
        return new HydrogenMoleculeImportanceBatches(count,radius,1.15,43,BATCH_SIZE);
    }
    private static double norm(double[] values){double sum=0;for(double value:values)sum+=value*value;return Math.sqrt(sum);}
    private static long usedHeap(){Runtime runtime=Runtime.getRuntime();return runtime.totalMemory()-runtime.freeMemory();}
    private static double[] solve(double[][] matrix,double[] rhs,double regularization){int n=rhs.length;double[][] a=new double[n][n+1];
        for(int i=0;i<n;i++){System.arraycopy(matrix[i],0,a[i],0,n);a[i][i]+=regularization;a[i][n]=rhs[i];}
        for(int pivot=0;pivot<n;pivot++){int selected=pivot;for(int row=pivot+1;row<n;row++)if(Math.abs(a[row][pivot])>Math.abs(a[selected][pivot]))selected=row;
            double[] swap=a[pivot];a[pivot]=a[selected];a[selected]=swap;if(Math.abs(a[pivot][pivot])<1e-15)throw new IllegalArgumentException("singular diagnostic SR covariance");
            for(int row=pivot+1;row<n;row++){double factor=a[row][pivot]/a[pivot][pivot];for(int column=pivot;column<=n;column++)a[row][column]-=factor*a[pivot][column];}}
        double[] result=new double[n];for(int row=n-1;row>=0;row--){double value=a[row][n];for(int column=row+1;column<n;column++)value-=a[row][column]*result[column];result[row]=value/a[row][row];}return result;}

    record Configuration(int maximumIterations,int minimumIterations,int patience,double learningRate,
            double diagonalRegularization,double improvementTolerance,double maximumAbsoluteUpdate,int sampleCount){Configuration{
        if(maximumIterations<1||minimumIterations<1||minimumIterations>maximumIterations||patience<1||sampleCount<1)throw new IllegalArgumentException("invalid iteration settings");
        if(learningRate<=0||diagonalRegularization<=0||improvementTolerance<0||maximumAbsoluteUpdate<=0)throw new IllegalArgumentException("invalid diagnostic settings");}}
    record Result(ParameterVector parameters,double loss,double energyLoss,double forceLoss,double diagnosticForce,
            double meanEnergy,int iterations,long objectiveEvaluations,long stateEvaluations,long localEnergyEvaluations,
            long forceEvaluations,long displacedStateEvaluations,long sampleCount,long wallTimeNanos,long peakObservedHeapBytes,
            boolean converged,List<Double> lossHistory){Result{lossHistory=List.copyOf(lossHistory);}}
    record ObjectiveEvaluation(double loss,double[] rawGradient,double[] scaledGradient,double force,long stateEvaluations,
            long localEnergyEvaluations,long displacedStateEvaluations,long sampleCount) { }
    private record Single(double energy,double[] gradient,double[][] covariance,long stateEvaluations,long localEnergyEvaluations,long sampleCount) { }
    private record Statistics(double loss,double energyLoss,double forceLoss,double force,double meanEnergy,double[] rawGradient,
            double[] scaledGradient,double[][] covariance,long stateEvaluations,long localEnergyEvaluations,
            long displacedStateEvaluations,long sampleCount) { }
    private static final class Mutable {double norm,energy;long evaluations,localEnergyEvaluations,samples;final double[] o,oe;final double[][] oo;
        Mutable(int p){o=new double[p];oe=new double[p];oo=new double[p][p];}}
}
