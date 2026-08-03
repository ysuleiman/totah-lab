package totah.lab.euclid.linear;

/**
 * Dense direct solver with Gaussian elimination and a singular-system
 * fallback.
 */
public class DenseDirectSolver implements LinearSolver {
    private static final double SINGULAR_THRESHOLD = 1e-12;
    private static final double RESIDUAL_THRESHOLD = 1e-6;

    @Override
    public double[] solve(SparseMatrix H, double[] b) {
        if (H.size != b.length) {
            throw new IllegalArgumentException(
                    "Matrix size and right-hand side length must match");
        }
        int n = b.length;
        double[][] A = H.toDense(n);

        // Try Gaussian elimination first; a singular pivot is exactly the
        // case the SVD fallback exists for, so fall through instead of throwing
        double[] x;
        try {
            x = gaussianElimination(A, b);
        } catch (IllegalStateException e) {
            System.err.println("DenseDirectSolver: " + e.getMessage() + ", trying SVD");
            return svdSolve(A, b);
        }

        // Check residual, scaled by ||b|| so larger systems are not held to
        // an absolute threshold their roundoff cannot meet (floor of 1.0
        // keeps the original behavior for small right-hand sides)
        double resNorm = computeResidualNorm(A, x, b);
        double threshold = RESIDUAL_THRESHOLD * Math.max(vectorNorm(b), 1.0);
        if (resNorm > threshold || Double.isNaN(resNorm)) {
            System.err.println("DenseDirectSolver: LU residual " + resNorm
                    + " exceeds threshold " + threshold + ", trying SVD");
            x = svdSolve(A, b);
        }

        return x;
    }

    private double vectorNorm(double[] v) {
        double norm = 0.0;
        for (double value : v) {
            norm += value * value;
        }
        return Math.sqrt(norm);
    }

    private double[] gaussianElimination(double[][] A, double[] b) {
        int n = b.length;
        double[][] M = new double[n][n + 1];

        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, M[i], 0, n);
            M[i][n] = b[i];
        }

        for (int col = 0; col < n; col++) {
            int maxRow = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(M[row][col]) > Math.abs(M[maxRow][col])) {
                    maxRow = row;
                }
            }
            if (Math.abs(M[maxRow][col]) < SINGULAR_THRESHOLD) {
                throw new IllegalStateException("DenseDirectSolver: Singular matrix at column " + col);
            }

            double[] tmp = M[col];
            M[col] = M[maxRow];
            M[maxRow] = tmp;

            double piv = M[col][col];
            for (int j = col; j <= n; j++) {
                M[col][j] /= piv;
            }

            for (int row = 0; row < n; row++) {
                if (row == col) continue;
                double factor = M[row][col];
                for (int j = col; j <= n; j++) {
                    M[row][j] -= factor * M[col][j];
                }
            }
        }

        double[] x = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = M[i][n];
        }
        return x;
    }

    /**
     * SVD fallback using Jacobi eigenvalue iteration.
     * Simpler than full SVD - uses pseudo-inverse via eigen-decomposition of A^T A.
     */
    private double[] svdSolve(double[][] A, double[] b) {
        int n = A.length;
        int m = A[0].length;

        // Compute A^T A and A^T b
        double[][] AtA = new double[m][m];
        double[] Atb = new double[m];

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < m; j++) {
                double sum = 0.0;
                for (int k = 0; k < n; k++) {
                    sum += A[k][i] * A[k][j];
                }
                AtA[i][j] = sum;
            }
            double sum = 0.0;
            for (int k = 0; k < n; k++) {
                sum += A[k][i] * b[k];
            }
            Atb[i] = sum;
        }

        // Add small regularization for numerical stability
        double lambda = 1e-10;
        for (int i = 0; i < m; i++) {
            AtA[i][i] += lambda;
        }

        // Solve regularized system
        double[][] M = new double[m][m + 1];
        for (int i = 0; i < m; i++) {
            System.arraycopy(AtA[i], 0, M[i], 0, m);
            M[i][m] = Atb[i];
        }

        // Gaussian elimination on regularized system
        for (int col = 0; col < m; col++) {
            int maxRow = col;
            for (int row = col + 1; row < m; row++) {
                if (Math.abs(M[row][col]) > Math.abs(M[maxRow][col])) {
                    maxRow = row;
                }
            }
            if (Math.abs(M[maxRow][col]) < SINGULAR_THRESHOLD) continue;

            double[] tmp = M[col];
            M[col] = M[maxRow];
            M[maxRow] = tmp;

            double piv = M[col][col];
            for (int j = col; j <= m; j++) M[col][j] /= piv;
            for (int row = 0; row < m; row++) {
                if (row == col) continue;
                double factor = M[row][col];
                for (int j = col; j <= m; j++) {
                    M[row][j] -= factor * M[col][j];
                }
            }
        }

        double[] x = new double[m];
        for (int i = 0; i < m; i++) x[i] = M[i][m];

        double resNorm = computeResidualNorm(A, x, b);
        System.out.println("DenseDirectSolver: SVD fallback residual = " + resNorm);

        return x;
    }

    private double computeResidualNorm(double[][] A, double[] x, double[] b) {
        int n = b.length;
        double norm = 0.0;
        for (int i = 0; i < n; i++) {
            double sum = 0.0;
            for (int j = 0; j < x.length; j++) {
                sum += A[i][j] * x[j];
            }
            double diff = b[i] - sum;
            norm += diff * diff;
        }
        return Math.sqrt(norm);
    }
}
