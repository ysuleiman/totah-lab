package totah.lab.euclid.linear;

/** Preconditioned conjugate gradient solver for symmetric positive-definite systems. */
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
    public double[] solve(SparseMatrix matrix, double[] b) {
        if (matrix.size != b.length) {
            throw new IllegalArgumentException(
                    "Matrix size and right-hand side length must match");
        }

        double[] x = new double[b.length];
        double[] r = b.clone();

        double[] z = preconditioner.apply(r);
        double[] p = z.clone();
        double rsold = dot(r, z);
        double bNormSquared = dot(b, b);
        if (bNormSquared == 0.0) {
            return x;
        }
        if (!Double.isFinite(rsold) || Math.abs(rsold) <= 1e-30) {
            // A preconditioner that annihilates a nonzero residual (r·z == 0)
            // makes beta = rsnew / rsold NaN, which evades the breakdown check
            throw new IllegalStateException(
                    "Preconditioned conjugate gradient breakdown");
        }
        double toleranceSquared = residualThreshold
                * residualThreshold * bNormSquared;

        for (int iter = 0; iter < maxIter; iter++) {
            double[] matrixTimesDirection = matrix.multiply(p);

            double denominator = dot(p, matrixTimesDirection);
            if (Math.abs(denominator) <= 1e-30) {
                throw new IllegalStateException(
                        "Preconditioned conjugate gradient breakdown");
            }
            double alpha = rsold / denominator;

            for (int i = 0; i < x.length; i++) {
                x[i] += alpha * p[i];
                r[i] -= alpha * matrixTimesDirection[i];
            }

            if (dot(r, r) <= toleranceSquared) {
                return x;
            }

            z = preconditioner.apply(r);
            double rsnew = dot(r, z);
            if (!Double.isFinite(rsnew) || Math.abs(rsnew) <= 1e-30) {
                // r has not converged but r·z vanished: beta would be NaN and
                // the loop would burn all remaining iterations on NaN arithmetic
                throw new IllegalStateException(
                        "Preconditioned conjugate gradient breakdown");
            }
            double beta = rsnew / rsold;

            for (int i = 0; i < p.length; i++) {
                p[i] = z[i] + beta * p[i];
            }
            rsold = rsnew;
        }

        throw new IllegalStateException(
                "Preconditioned conjugate gradient did not converge within "
                        + maxIter + " iterations");
    }

    private double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }
}
