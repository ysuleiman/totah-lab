package totah.lab.prometheus.numerics;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic conventional PCG for a fixed SPD operator and preconditioner.
 */
public final class PreconditionedConjugateGradientSolver {
    public static double dot(double[] a, double[] b) {
        if (a.length != b.length) throw new IllegalArgumentException("vector dimensions disagree");
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }

    public static double norm(double[] a) {
        return Math.sqrt(dot(a, a));
    }

    public Result solve(LinearOperator operator, Preconditioner preconditioner, double[] rhs, Configuration configuration) {
        if (operator.dimension() != rhs.length || preconditioner.dimension() != rhs.length)
            throw new IllegalArgumentException("solver dimensions disagree");
        int n = rhs.length;
        double[] x = new double[n], r = rhs.clone(), z = preconditioner.apply(r), p = z.clone();
        double rz = dot(r, z), rhsNorm = norm(rhs);
        if (!Double.isFinite(rz) || rz < 0) throw new IllegalArgumentException("non-positive preconditioned residual");
        List<Double> history = new ArrayList<>();
        history.add(norm(r));
        int applications = 0, iterations = 0;
        double threshold = Math.max(configuration.absoluteTolerance, configuration.relativeTolerance * rhsNorm);
        if (history.getFirst() <= threshold)
            return new Result(x, 0, 0, history.getFirst(), rhsNorm == 0 ? 0 : history.getFirst() / rhsNorm, List.copyOf(history), true);
        for (int iteration = 0; iteration < configuration.maximumIterations; iteration++) {
            double[] ap = operator.apply(p);
            applications++;
            double curvature = dot(p, ap);
            if (!Double.isFinite(curvature) || curvature <= 0)
                throw new IllegalArgumentException("PCG non-positive curvature");
            double alpha = rz / curvature;
            for (int i = 0; i < n; i++) {
                x[i] += alpha * p[i];
                r[i] -= alpha * ap[i];
            }
            double residual = norm(r);
            history.add(residual);
            iterations = iteration + 1;
            if (residual <= threshold)
                return new Result(x, iterations, applications, residual, rhsNorm == 0 ? 0 : residual / rhsNorm, List.copyOf(history), true);
            double[] nextZ = preconditioner.apply(r);
            double nextRz = dot(r, nextZ);
            if (!Double.isFinite(nextRz) || nextRz < 0)
                throw new IllegalArgumentException("PCG invalid preconditioned residual");
            double beta = nextRz / rz;
            for (int i = 0; i < n; i++) p[i] = nextZ[i] + beta * p[i];
            z = nextZ;
            rz = nextRz;
        }
        double residual = history.getLast();
        return new Result(x, iterations, applications, residual, rhsNorm == 0 ? 0 : residual / rhsNorm, List.copyOf(history), false);
    }

    public record Configuration(int maximumIterations, double relativeTolerance, double absoluteTolerance) {
        public Configuration {
            if (maximumIterations < 1 || relativeTolerance <= 0 || absoluteTolerance <= 0)
                throw new IllegalArgumentException("invalid PCG configuration");
        }
    }

    public record Result(double[] solution, int iterations, int operatorApplications, double absoluteResidual,
                         double relativeResidual, List<Double> residualHistory, boolean converged) {
        public Result {
            solution = solution.clone();
            residualHistory = List.copyOf(residualHistory);
        }

        @Override
        public double[] solution() {
            return solution.clone();
        }
    }
}
