package totah.lab.prometheus.variational;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Deterministic pure-Java Adam optimizer with central finite-difference objective derivatives. */
public final class FiniteDifferenceAdamOptimizer implements ParameterOptimizer {
    private final int iterations; private final double learningRate; private final double differenceStep;

    public FiniteDifferenceAdamOptimizer(int iterations,double learningRate,double differenceStep) {
        if(iterations<1||learningRate<=0||differenceStep<=0) throw new IllegalArgumentException("invalid optimizer settings");
        this.iterations=iterations;this.learningRate=learningRate;this.differenceStep=differenceStep;
    }

    @Override public String optimizerId() { return "prometheus-finite-difference-adam-v1"; }

    @Override public VariationalResult optimize(VariationalProblem problem) {
        List<Double> current=new ArrayList<>(problem.initialState().parameters().values());
        double[] firstMoment=new double[current.size()],secondMoment=new double[current.size()];
        List<Double> best=new ArrayList<>(current); double bestObjective=objective(problem,current);
        for(int iteration=1;iteration<=iterations;iteration++) {
            double[] gradient=new double[current.size()];
            for(int parameter=0;parameter<gradient.length;parameter++) {
                double original=current.get(parameter);
                current.set(parameter,original+differenceStep); double plus=objective(problem,current);
                current.set(parameter,original-differenceStep); double minus=objective(problem,current);
                current.set(parameter,original); gradient[parameter]=(plus-minus)/(2.0*differenceStep);
            }
            for(int parameter=0;parameter<gradient.length;parameter++) {
                double clipped=Math.max(-10.0,Math.min(10.0,gradient[parameter]));
                firstMoment[parameter]=0.9*firstMoment[parameter]+0.1*clipped;
                secondMoment[parameter]=0.999*secondMoment[parameter]+0.001*clipped*clipped;
                double correctedFirst=firstMoment[parameter]/(1.0-Math.pow(0.9,iteration));
                double correctedSecond=secondMoment[parameter]/(1.0-Math.pow(0.999,iteration));
                current.set(parameter,current.get(parameter)-learningRate*correctedFirst/(Math.sqrt(correctedSecond)+1e-8));
            }
            double candidate=objective(problem,current);
            if(candidate<bestObjective) {bestObjective=candidate;best=new ArrayList<>(current);}
        }
        return new VariationalResult(problem.scientificIdentity(),new ParameterVector(best),bestObjective,true,
                problem.acceptanceGates(),List.of(),Map.of("optimizer",optimizerId(),"iterations",Integer.toString(iterations),
                        "learning_rate",Double.toString(learningRate),"difference_step",Double.toString(differenceStep)));
    }

    private static double objective(VariationalProblem problem,List<Double> parameters) {
        DifferentiableQuantumState state=problem.initialState().withParameters(new ParameterVector(parameters));
        return problem.functional().evaluate(state,problem.hamiltonian(),problem.points()).objective();
    }
}
