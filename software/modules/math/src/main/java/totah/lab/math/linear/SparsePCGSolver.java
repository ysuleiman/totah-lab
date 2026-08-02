package totah.lab.math.linear;

/**
 * Preconditioned Conjugate Gradient for QEq KKT systems.
 * Handles charge constraint via nullspace projection.
 */
public class SparsePCGSolver implements LinearSolver {
    private final Preconditioner preconditioner;
    private final double residualThreshold;
    private final int maxIter;

    public SparsePCGSolver(Preconditioner preconditioner) {
        this(preconditioner, 1e-6, 10000);
    }

    public SparsePCGSolver(Preconditioner preconditioner, double residualThreshold, int maxIter) {
        this.preconditioner = preconditioner;
        this.residualThreshold = residualThreshold;
        this.maxIter = maxIter;
    }

    @Override
    public double[] solve(SparseMatrix H, double[] b) {
        int n = b.length - 1; // number of atoms (excluding Lagrange multiplier)
        int N = b.length;

        // Initial guess
        double[] x = new double[N];
        x[n] = java.util.Arrays.stream(b, 0, n).average().orElse(0.0);
        projectConstraint(x, n, b[n]);

        double[] r = vectorSubtract(b, H.multiply(x));
        projectConstraint(r, n, 0.0);

        double[] z = preconditioner.apply(r);
        projectConstraint(z, n, 0.0);

        double[] p = z.clone();
        double rsold = dot(r, z);
        double tol = residualThreshold * residualThreshold * dot(b, b);

        for (int iter = 0; iter < maxIter; iter++) {
            double[] Ap = H.multiply(p);
            projectConstraint(Ap, n, 0.0);

            double denom = dot(p, Ap);
            double alpha = Math.abs(denom) > 1e-30 ? rsold / denom : 0.0;

            for (int i = 0; i < N; i++) x[i] += alpha * p[i];
            for (int i = 0; i < N; i++) r[i] -= alpha * Ap[i];

            double resNorm = dot(r, r);
            if (resNorm < tol) {
                projectConstraint(x, n, b[n]);
                return x;
            }

            z = preconditioner.apply(r);
            projectConstraint(z, n, 0.0);

            double rsnew = dot(r, z);
            double beta = rsnew / rsold;

            for (int i = 0; i < N; i++) p[i] = z[i] + beta * p[i];
            rsold = rsnew;
        }

        // Exhausted maxIter (or stalled on alpha = 0 for an indefinite KKT
        // system): report the true residual instead of silently returning
        // an unconverged vector
        double[] residual = vectorSubtract(b, H.multiply(x));
        projectConstraint(residual, n, 0.0);
        System.err.println("SparsePCGSolver: did not converge within " + maxIter
                + " iterations (residual norm " + Math.sqrt(dot(residual, residual))
                + ", tolerance " + Math.sqrt(tol) + ")");

        projectConstraint(x, n, b[n]);
        return x;
    }

    private void projectConstraint(double[] v, int n, double target) {
        double sum = 0.0;
        for (int i = 0; i < n; i++) sum += v[i];
        double delta = (sum - target) / n;
        for (int i = 0; i < n; i++) v[i] -= delta;
    }

    private double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    private double[] vectorSubtract(double[] a, double[] b) {
        double[] c = new double[a.length];
        for (int i = 0; i < a.length; i++) c[i] = a[i] - b[i];
        return c;
    }
}
