package totah.lab.prometheus.numerics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fixed-preconditioner CG whose convergence decision always uses an
 * independently recomputed {@code b-Ax} residual.
 */
public final class TrueResidualPreconditionedConjugateGradientSolver {

    public Result solve(LinearOperator operator, Preconditioner preconditioner,
            double[] rightHandSide, Configuration configuration) {
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(preconditioner, "preconditioner");
        Objects.requireNonNull(rightHandSide, "rightHandSide");
        Objects.requireNonNull(configuration, "configuration");
        int dimension = rightHandSide.length;
        if (operator.dimension() != dimension || preconditioner.dimension() != dimension) {
            throw new IllegalArgumentException("solver dimensions disagree");
        }
        double[] solution = new double[dimension];
        double[] residual = rightHandSide.clone();
        double[] preconditioned = preconditioner.apply(residual);
        double[] direction = preconditioned.clone();
        double residualPreconditioned = dot(residual, preconditioned);
        double rhsNorm = norm(rightHandSide);
        double threshold = Math.max(configuration.absoluteTolerance(),
                configuration.relativeTolerance() * rhsNorm);
        List<Double> trueResidualHistory = new ArrayList<>();
        trueResidualHistory.add(rhsNorm);
        int applications = 0;
        for (int iteration = 1; iteration <= configuration.maximumIterations(); iteration++) {
            double[] action = operator.apply(direction); applications++;
            double curvature = dot(direction, action);
            if (!(curvature > 0.0) || !Double.isFinite(curvature)) {
                throw new IllegalArgumentException("PCG non-positive curvature");
            }
            double alpha = residualPreconditioned / curvature;
            for (int i = 0; i < dimension; i++) solution[i] += alpha * direction[i];

            double[] appliedSolution = operator.apply(solution); applications++;
            double[] trueResidual = new double[dimension];
            for (int i = 0; i < dimension; i++) trueResidual[i] = rightHandSide[i] - appliedSolution[i];
            double trueNorm = norm(trueResidual); trueResidualHistory.add(trueNorm);
            if (trueNorm <= threshold) {
                return new Result(solution, iteration, applications, trueNorm,
                        rhsNorm == 0.0 ? 0.0 : trueNorm / rhsNorm,
                        List.copyOf(trueResidualHistory), true);
            }
            double[] nextPreconditioned = preconditioner.apply(trueResidual);
            double nextResidualPreconditioned = dot(trueResidual, nextPreconditioned);
            if (!(nextResidualPreconditioned >= 0.0) || !Double.isFinite(nextResidualPreconditioned)) {
                throw new IllegalArgumentException("PCG invalid preconditioned residual");
            }
            double beta = nextResidualPreconditioned / residualPreconditioned;
            for (int i = 0; i < dimension; i++) {
                direction[i] = nextPreconditioned[i] + beta * direction[i];
            }
            residual = trueResidual; preconditioned = nextPreconditioned;
            residualPreconditioned = nextResidualPreconditioned;
        }
        double finalResidual = trueResidualHistory.getLast();
        return new Result(solution, configuration.maximumIterations(), applications,
                finalResidual, rhsNorm == 0.0 ? 0.0 : finalResidual / rhsNorm,
                List.copyOf(trueResidualHistory), false);
    }

    private static double dot(double[] first, double[] second) {
        if (first.length != second.length) throw new IllegalArgumentException("vector dimensions disagree");
        double value = 0.0;
        for (int i = 0; i < first.length; i++) value += first[i] * second[i];
        return value;
    }

    private static double norm(double[] vector) { return Math.sqrt(dot(vector, vector)); }

    public record Configuration(int maximumIterations, double relativeTolerance, double absoluteTolerance) {
        public Configuration {
            if (maximumIterations < 1 || !(relativeTolerance > 0.0) || !(absoluteTolerance > 0.0)) {
                throw new IllegalArgumentException("invalid PCG configuration");
            }
        }
    }

    public record Result(double[] solution, int iterations, int operatorApplications,
            double absoluteTrueResidual, double relativeTrueResidual,
            List<Double> trueResidualHistory, boolean converged) {
        public Result {
            solution = solution.clone();
            trueResidualHistory = List.copyOf(trueResidualHistory);
        }
    }
}
