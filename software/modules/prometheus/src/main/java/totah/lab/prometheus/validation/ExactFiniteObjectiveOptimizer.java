package totah.lab.prometheus.validation;

import java.util.ArrayList;
import java.util.List;

import totah.lab.prometheus.variational.ParameterVector;

/** Locked SR optimizer whose RHS is the exact finite-objective derivative. */
final class ExactFiniteObjectiveOptimizer {
    private static final double MINIMUM_NORM=1e-14;
    private final Configuration configuration;
    private final ExactFiniteObjectiveDifferentiator objective;

    ExactFiniteObjectiveOptimizer(Configuration configuration,double[] radii,double[] references) {
        this.configuration=configuration;this.objective=new ExactFiniteObjectiveDifferentiator(radii,references,configuration.sampleCount);
    }

    Result optimize(ParameterVector initial) {
        ParameterVector current=initial;List<Double> history=new ArrayList<>();double best=Double.POSITIVE_INFINITY;int stale=0,completed=0;
        long stateEvaluations=0,localEnergyEvaluations=0,sampleCount=0,peakHeap=usedHeap(),start=System.nanoTime();
        for(int iteration=0;iteration<configuration.maximumIterations;iteration++) {
            var value=objective.evaluate(current);history.add(value.loss());stateEvaluations+=value.stateEvaluations();
            localEnergyEvaluations+=value.localEnergyEvaluations();sampleCount+=value.sampleCount();peakHeap=Math.max(peakHeap,usedHeap());
            double[] rhs=rescale(value.gradient(),value.energyGradient());double[] direction=solve(value.covariance(),rhs,configuration.diagonalRegularization);
            List<Double> next=new ArrayList<>(current.values());
            for(int i=0;i<next.size();i++){double update=-configuration.learningRate*direction[i];
                update=Math.max(-configuration.maximumAbsoluteUpdate,Math.min(configuration.maximumAbsoluteUpdate,update));next.set(i,next.get(i)+update);}
            current=new ParameterVector(next);completed=iteration+1;
            if(best-value.loss()>configuration.improvementTolerance){best=value.loss();stale=0;}else stale++;
            if(completed>=configuration.minimumIterations&&stale>=configuration.patience)break;
        }
        var value=objective.evaluate(current);history.add(value.loss());stateEvaluations+=value.stateEvaluations();
        localEnergyEvaluations+=value.localEnergyEvaluations();sampleCount+=value.sampleCount();peakHeap=Math.max(peakHeap,usedHeap());
        return new Result(current,value.loss(),value.energyLoss(),value.forceLoss(),value.force(),value.meanEnergy(),completed,
                completed+1,stateEvaluations,localEnergyEvaluations,sampleCount,System.nanoTime()-start,peakHeap,
                completed<configuration.maximumIterations,List.copyOf(history));
    }

    private static double[] rescale(double[] diagnostic,double[] energy){double diagnosticNorm=norm(diagnostic),energyNorm=norm(energy),scale=1;
        if(diagnosticNorm>=MINIMUM_NORM&&energyNorm>=MINIMUM_NORM)scale=energyNorm/diagnosticNorm;double[] result=diagnostic.clone();for(int i=0;i<result.length;i++)result[i]*=scale;return result;}
    private static double norm(double[] values){double sum=0;for(double value:values)sum+=value*value;return Math.sqrt(sum);}
    private static long usedHeap(){Runtime runtime=Runtime.getRuntime();return runtime.totalMemory()-runtime.freeMemory();}
    private static double[] solve(double[][] matrix,double[] rhs,double regularization){int n=rhs.length;double[][] a=new double[n][n+1];
        for(int i=0;i<n;i++){System.arraycopy(matrix[i],0,a[i],0,n);a[i][i]+=regularization;a[i][n]=rhs[i];}
        for(int pivot=0;pivot<n;pivot++){int selected=pivot;for(int row=pivot+1;row<n;row++)if(Math.abs(a[row][pivot])>Math.abs(a[selected][pivot]))selected=row;
            double[] swap=a[pivot];a[pivot]=a[selected];a[selected]=swap;if(Math.abs(a[pivot][pivot])<1e-15)throw new IllegalArgumentException("singular exact-objective SR covariance");
            for(int row=pivot+1;row<n;row++){double factor=a[row][pivot]/a[pivot][pivot];for(int column=pivot;column<=n;column++)a[row][column]-=factor*a[pivot][column];}}
        double[] result=new double[n];for(int row=n-1;row>=0;row--){double value=a[row][n];for(int column=row+1;column<n;column++)value-=a[row][column]*result[column];result[row]=value/a[row][row];}return result;}

    record Configuration(int maximumIterations,int minimumIterations,int patience,double learningRate,double diagonalRegularization,
            double improvementTolerance,double maximumAbsoluteUpdate,int sampleCount) { }
    record Result(ParameterVector parameters,double loss,double energyLoss,double forceLoss,double diagnosticForce,double meanEnergy,
            int iterations,long objectiveEvaluations,long stateEvaluations,long localEnergyEvaluations,long sampleCount,long wallTimeNanos,
            long peakObservedHeapBytes,boolean converged,List<Double> history) { }
}
