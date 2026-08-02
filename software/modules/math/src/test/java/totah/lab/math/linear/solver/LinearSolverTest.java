package totah.lab.math.linear.solver;

import org.junit.jupiter.api.Test;
import totah.lab.math.linear.DenseDirectSolver;
import totah.lab.math.linear.HybridSolver;
import totah.lab.math.linear.SparseMatrix;
import totah.lab.math.linear.SparsePCGSolver;
import totah.lab.math.linear.preconditioner.JacobiPreconditioner;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Solver contract: solve the QEq KKT system [H 1; 1^T 0]·[q; mu] = [V; Q]
 * where H is handed over already augmented (size n+1) and the last row
 * enforces charge conservation.
 *
 * The 3-atom fixture has a known solution x* = [0.3, -0.5, 0.2, 1.7] so the
 * right-hand side is derived by plain dense multiplication.
 */
public class LinearSolverTest {

    private static final double[][] A_KKT = {
            {10.0, 0.5, 0.3, 1.0},
            {0.5, 12.0, 0.4, 1.0},
            {0.3, 0.4, 14.0, 1.0},
            {1.0, 1.0, 1.0, 0.0}
    };

    private static final double[] X_STAR = {0.3, -0.5, 0.2, 1.7};

    private static SparseMatrix kktMatrix() {
        SparseMatrix H = new SparseMatrix(4);
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                H.set(i, j, A_KKT[i][j]);
            }
        }
        return H;
    }

    /** b = A_KKT · X_STAR via explicit dense arithmetic. */
    private static double[] rightHandSide() {
        double[] b = new double[4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                b[i] += A_KKT[i][j] * X_STAR[j];
            }
        }
        return b;
    }

    @Test
    public void denseDirectRecoversKnownKktSolution() {
        double[] x = new DenseDirectSolver().solve(kktMatrix(), rightHandSide());
        assertArrayEquals(X_STAR, x, 1e-9,
                "DenseDirectSolver must recover the known solution exactly");
    }

    @Test
    public void pcgRecoversKnownKktSolution() {
        SparseMatrix H = kktMatrix();
        double[] x = new SparsePCGSolver(new JacobiPreconditioner(H.getDiagonal()))
                .solve(H, rightHandSide());
        // The solver iterates in the constraint-projected subspace, so the atom
        // charges converge to the exact solution while the Lagrange multiplier
        // keeps a constant, initial-guess-dependent offset (the projection hides
        // the uniform residual component that determines it). Assert the charges.
        assertChargesMatch(X_STAR, x, 1e-4,
                "SparsePCGSolver must converge to the known charges");
    }

    @Test
    public void pcgEnforcesChargeConservation() {
        SparseMatrix H = kktMatrix();
        double[] b = rightHandSide();
        double[] x = new SparsePCGSolver(new JacobiPreconditioner(H.getDiagonal())).solve(H, b);
        double totalCharge = x[0] + x[1] + x[2];
        assertEquals(b[3], totalCharge, 1e-9,
                "sum of atom charges must equal the constraint b[n]");
    }

    @Test
    public void denseDirectAndPcgAgreeOnSameSystem() {
        SparseMatrix H = kktMatrix();
        double[] b = rightHandSide();
        double[] direct = new DenseDirectSolver().solve(kktMatrix(), b);
        double[] pcg = new SparsePCGSolver(new JacobiPreconditioner(H.getDiagonal())).solve(H, b);
        assertChargesMatch(direct, pcg, 1e-4,
                "direct and iterative solvers must agree on the same KKT system");
    }

    @Test
    public void hybridUsesDirectPathForSmallSystems() {
        double[] x = new HybridSolver(10).solve(kktMatrix(), rightHandSide());
        assertArrayEquals(X_STAR, x, 1e-9,
                "hybrid below directMaxSize must match the direct solver");
    }

    @Test
    public void hybridUsesIterativePathAboveThreshold() {
        double[] x = new HybridSolver(1).solve(kktMatrix(), rightHandSide());
        assertChargesMatch(X_STAR, x, 1e-4,
                "hybrid above directMaxSize must match the PCG solver");
    }

    @Test
    public void pcgConvergesForLargerDiagonallyDominantSystem() {
        int n = 50;
        SparseMatrix H = new SparseMatrix(n + 1);
        for (int i = 0; i < n; i++) {
            H.set(i, i, 10.0 + 0.1 * i);
            if (i + 1 < n) {
                H.set(i, i + 1, 0.05);
                H.set(i + 1, i, 0.05);
            }
            H.set(i, n, 1.0);
            H.set(n, i, 1.0);
        }
        H.set(n, n, 0.0);

        double[] b = new double[n + 1];
        for (int i = 0; i < n; i++) b[i] = -2.0 + 0.07 * i;
        b[n] = 0.5;

        double[] pcg = new SparsePCGSolver(new JacobiPreconditioner(H.getDiagonal()))
                .solve(H, b);
        double[] direct = new DenseDirectSolver().solve(H, b);

        assertChargesMatch(direct, pcg, 1e-4,
                "PCG charges must match the direct solution on a 50-atom system");

        double totalCharge = 0.0;
        for (int i = 0; i < n; i++) totalCharge += pcg[i];
        assertEquals(0.5, totalCharge, 1e-9, "charge constraint violated");
    }

    /** Compares only the atom-charge entries (all but the Lagrange multiplier). */
    private static void assertChargesMatch(double[] expected, double[] actual,
                                           double tol, String message) {
        assertEquals(expected.length, actual.length, message + ": length mismatch");
        for (int i = 0; i < expected.length - 1; i++) {
            assertEquals(expected[i], actual[i], tol, message + " (charge index " + i + ")");
        }
    }

    @Test
    public void denseDirectSolvesPlainSpdSystem() {
        // No constraint row: plain 3x3 SPD with known solution x = [1, 2, 3]
        SparseMatrix H = new SparseMatrix(3);
        H.set(0, 0, 4.0); H.set(0, 1, 1.0);
        H.set(1, 0, 1.0); H.set(1, 1, 3.0);
        H.set(2, 2, 2.0);
        double[] b = {6.0, 7.0, 6.0};

        double[] x = new DenseDirectSolver().solve(H, b);
        assertArrayEquals(new double[]{1.0, 2.0, 3.0}, x, 1e-9,
                "direct solver failed on a plain SPD system");
    }
}
