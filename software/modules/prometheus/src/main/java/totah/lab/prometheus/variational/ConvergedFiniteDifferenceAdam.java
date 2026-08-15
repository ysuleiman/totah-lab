package totah.lab.prometheus.variational;

import java.util.ArrayList;
import java.util.List;

/** Deterministic Adam with preregistered convergence stopping and execution accounting. */
public final class ConvergedFiniteDifferenceAdam {
    private final int maximumIterations,minimumIterations,patience;
    private final double learningRate,differenceStep,improvementTolerance;
    public ConvergedFiniteDifferenceAdam(int maximumIterations,int minimumIterations,int patience,
            double learningRate,double differenceStep,double improvementTolerance) {
        this.maximumIterations=maximumIterations;this.minimumIterations=minimumIterations;this.patience=patience;
        this.learningRate=learningRate;this.differenceStep=differenceStep;this.improvementTolerance=improvementTolerance;
    }
    public OptimizationResult optimize(DifferentiableQuantumState initial,Hamiltonian hamiltonian,
            VariationalFunctional functional,CollocationPointSet points) {
        long start=System.nanoTime(); List<Double> current=new ArrayList<>(initial.parameters().values());
        double[] first=new double[current.size()],second=new double[current.size()]; long evaluations=0;
        double best=objective(initial,current,hamiltonian,functional,points); evaluations++;
        List<Double> bestParameters=new ArrayList<>(current); int stale=0,iteration;
        for(iteration=1;iteration<=maximumIterations;iteration++) {
            double[] gradient=new double[current.size()];
            for(int parameter=0;parameter<gradient.length;parameter++) {
                double original=current.get(parameter);
                current.set(parameter,original+differenceStep);double plus=objective(initial,current,hamiltonian,functional,points);
                current.set(parameter,original-differenceStep);double minus=objective(initial,current,hamiltonian,functional,points);
                current.set(parameter,original);evaluations+=2;gradient[parameter]=(plus-minus)/(2*differenceStep);
            }
            for(int parameter=0;parameter<gradient.length;parameter++) {
                double clipped=Math.max(-10,Math.min(10,gradient[parameter]));
                first[parameter]=0.9*first[parameter]+0.1*clipped;second[parameter]=0.999*second[parameter]+0.001*clipped*clipped;
                double correctedFirst=first[parameter]/(1-Math.pow(0.9,iteration));
                double correctedSecond=second[parameter]/(1-Math.pow(0.999,iteration));
                current.set(parameter,current.get(parameter)-learningRate*correctedFirst/(Math.sqrt(correctedSecond)+1e-8));
            }
            double candidate=objective(initial,current,hamiltonian,functional,points);evaluations++;
            if(best-candidate>improvementTolerance) {best=candidate;bestParameters=new ArrayList<>(current);stale=0;} else stale++;
            if(iteration>=minimumIterations&&stale>=patience) break;
        }
        return new OptimizationResult(new ParameterVector(bestParameters),best,iteration,evaluations,
                System.nanoTime()-start,iteration<=maximumIterations);
    }
    private static double objective(DifferentiableQuantumState initial,List<Double> parameters,Hamiltonian hamiltonian,
            VariationalFunctional functional,CollocationPointSet points) {
        return functional.evaluate(initial.withParameters(new ParameterVector(parameters)),hamiltonian,points).objective();
    }
    public record OptimizationResult(ParameterVector parameters,double objective,int iterations,long objectiveEvaluations,
            long wallTimeNanos,boolean converged) { }
}
