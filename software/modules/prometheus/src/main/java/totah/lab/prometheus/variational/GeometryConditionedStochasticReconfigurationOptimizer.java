package totah.lab.prometheus.variational;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.neural.GeometryConditionedHydrogenMoleculeState;

/** Equal-geometry-weighted SR optimizer for one shared H2 geometry-conditioned state. */
public final class GeometryConditionedStochasticReconfigurationOptimizer {
    private static final double MINIMUM_NORM=1e-14;
    private final Configuration configuration;
    public GeometryConditionedStochasticReconfigurationOptimizer(Configuration configuration){this.configuration=Objects.requireNonNull(configuration);}
    public Result optimize(ParameterVector initial,List<Double> radii){
        Objects.requireNonNull(initial);radii=List.copyOf(radii);if(radii.isEmpty())throw new IllegalArgumentException("radii required");
        ParameterVector current=initial;List<Double> history=new ArrayList<>();long stateEvaluations=0;int stale=0,completed=0;
        double best=Double.POSITIVE_INFINITY;long start=System.nanoTime();
        for(int iteration=0;iteration<configuration.maximumIterations;iteration++){
            Combined statistics=statistics(current,radii);stateEvaluations+=statistics.stateEvaluations;history.add(statistics.energy);
            double[] direction=solve(statistics.covariance,statistics.gradient,configuration.diagonalRegularization);
            List<Double> next=new ArrayList<>(current.values());for(int i=0;i<next.size();i++){double update=-configuration.learningRate*direction[i];
                update=Math.max(-configuration.maximumAbsoluteUpdate,Math.min(configuration.maximumAbsoluteUpdate,update));next.set(i,next.get(i)+update);}
            current=new ParameterVector(next);completed=iteration+1;
            if(best-statistics.energy>configuration.improvementTolerance){best=statistics.energy;stale=0;}else stale++;
            if(completed>=configuration.minimumIterations&&stale>=configuration.patience)break;
        }
        Combined finalStatistics=statistics(current,radii);stateEvaluations+=finalStatistics.stateEvaluations;history.add(finalStatistics.energy);
        return new Result(current,finalStatistics.energy,finalStatistics.perGeometryEnergy,completed,completed+1,stateEvaluations,
                System.nanoTime()-start,completed<configuration.maximumIterations,history);
    }
    private static Combined statistics(ParameterVector parameters,List<Double> radii){int count=parameters.values().size();
        double[][] covariance=new double[count][count];double[] gradient=new double[count];List<Double> energies=new ArrayList<>();long evaluations=0;
        for(double radius:radii){Single single=single(parameters,radius);energies.add(single.energy);evaluations+=single.stateEvaluations;
            for(int i=0;i<count;i++){gradient[i]+=single.gradient[i]/radii.size();for(int j=0;j<count;j++)covariance[i][j]+=single.covariance[i][j]/radii.size();}}
        return new Combined(energies.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),List.copyOf(energies),gradient,covariance,evaluations);}
    private static Single single(ParameterVector parameters,double radius){var state=new GeometryConditionedHydrogenMoleculeState(radius,parameters);
        var h=new HydrogenMoleculeHamiltonian(radius);var batches=new HydrogenMoleculeImportanceBatches(2500,radius,1.15,43,512);
        int p=parameters.values().size();Mutable m=new Mutable(p);batches.forEachBatch(batch->batch.forEach(point->{
            var bundle=state.evaluateWithGeometryDerivatives(point.coordinates()).stateEvaluation();m.evaluations++;
            double psi=bundle.value().real();if(!Double.isFinite(psi)||Math.abs(psi)<MINIMUM_NORM)return;
            double weight=point.weight()*psi*psi;double local=-.5*bundle.coordinateLaplacian().value().real()/psi+h.potential(point.coordinates());
            double[] observable=new double[p];for(int i=0;i<p;i++)observable[i]=bundle.parameterGradient().derivatives().get(i).real()/psi;
            m.norm+=weight;m.energy+=weight*local;for(int i=0;i<p;i++){m.o[i]+=weight*observable[i];m.oe[i]+=weight*observable[i]*local;
                for(int j=0;j<p;j++)m.oo[i][j]+=weight*observable[i]*observable[j];}}));
        if(!Double.isFinite(m.norm)||m.norm<MINIMUM_NORM)throw new IllegalArgumentException("invalid shared-state norm");
        double energy=m.energy/m.norm;double[] gradient=new double[p];double[][] covariance=new double[p][p];
        for(int i=0;i<p;i++){double mean=m.o[i]/m.norm;gradient[i]=2*(m.oe[i]/m.norm-mean*energy);
            for(int j=0;j<p;j++)covariance[i][j]=m.oo[i][j]/m.norm-mean*(m.o[j]/m.norm);}
        return new Single(energy,gradient,covariance,m.evaluations);}
    private static double[] solve(double[][] matrix,double[] rhs,double regularization){int n=rhs.length;double[][] a=new double[n][n+1];
        for(int i=0;i<n;i++){System.arraycopy(matrix[i],0,a[i],0,n);a[i][i]+=regularization;a[i][n]=rhs[i];}
        for(int pivot=0;pivot<n;pivot++){int selected=pivot;for(int row=pivot+1;row<n;row++)if(Math.abs(a[row][pivot])>Math.abs(a[selected][pivot]))selected=row;
            double[] swap=a[pivot];a[pivot]=a[selected];a[selected]=swap;if(Math.abs(a[pivot][pivot])<1e-15)throw new IllegalArgumentException("singular SR covariance");
            for(int row=pivot+1;row<n;row++){double factor=a[row][pivot]/a[pivot][pivot];for(int column=pivot;column<=n;column++)a[row][column]-=factor*a[pivot][column];}}
        double[] result=new double[n];for(int row=n-1;row>=0;row--){double value=a[row][n];for(int column=row+1;column<n;column++)value-=a[row][column]*result[column];result[row]=value/a[row][row];}return result;}
    public record Configuration(int maximumIterations,int minimumIterations,int patience,double learningRate,
            double diagonalRegularization,double improvementTolerance,double maximumAbsoluteUpdate){public Configuration{
        if(maximumIterations<1||minimumIterations<1||minimumIterations>maximumIterations||patience<1)throw new IllegalArgumentException("invalid iteration settings");
        if(learningRate<=0||diagonalRegularization<=0||improvementTolerance<0||maximumAbsoluteUpdate<=0)throw new IllegalArgumentException("invalid SR settings");}}
    public record Result(ParameterVector parameters,double meanEnergy,List<Double> perGeometryTrainingEnergies,int iterations,
            long objectiveEvaluations,long stateEvaluations,long wallTimeNanos,boolean converged,List<Double> energyHistory){public Result{
        perGeometryTrainingEnergies=List.copyOf(perGeometryTrainingEnergies);energyHistory=List.copyOf(energyHistory);}}
    private record Single(double energy,double[] gradient,double[][] covariance,long stateEvaluations){}
    private record Combined(double energy,List<Double> perGeometryEnergy,double[] gradient,double[][] covariance,long stateEvaluations){}
    private static final class Mutable{double norm,energy;long evaluations;final double[] o,oe;final double[][] oo;
        Mutable(int p){o=new double[p];oe=new double[p];oo=new double[p][p];}}
}
