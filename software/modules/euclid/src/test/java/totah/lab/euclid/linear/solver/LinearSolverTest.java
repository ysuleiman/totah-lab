package totah.lab.euclid.linear.solver;

import org.junit.jupiter.api.Test;
import totah.lab.euclid.linear.DenseDirectSolver;
import totah.lab.euclid.linear.HybridSolver;
import totah.lab.euclid.linear.SparseMatrix;
import totah.lab.euclid.linear.SparsePCGSolver;
import totah.lab.euclid.linear.preconditioner.JacobiPreconditioner;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LinearSolverTest {

    private static final double[] EXPECTED = {1.0, 2.0, 3.0};

    @Test
    void denseDirectSolvesSymmetricPositiveDefiniteSystem() {
        assertArrayEquals(
                EXPECTED,
                new DenseDirectSolver().solve(matrix(), rightHandSide()),
                1.0e-9);
    }

    @Test
    void pcgSolvesSymmetricPositiveDefiniteSystem() {
        SparseMatrix matrix = matrix();

        double[] solution = new SparsePCGSolver(
                new JacobiPreconditioner(matrix.getDiagonal()))
                .solve(matrix, rightHandSide());

        assertArrayEquals(EXPECTED, solution, 1.0e-6);
    }

    @Test
    void hybridUsesBothSolverPaths() {
        assertArrayEquals(
                EXPECTED,
                new HybridSolver(3).solve(matrix(), rightHandSide()),
                1.0e-9);
        assertArrayEquals(
                EXPECTED,
                new HybridSolver(2).solve(matrix(), rightHandSide()),
                1.0e-6);
    }

    @Test
    void pcgReturnsZeroForZeroRightHandSide() {
        SparseMatrix matrix = matrix();

        double[] solution = new SparsePCGSolver(
                new JacobiPreconditioner(matrix.getDiagonal()))
                .solve(matrix, new double[3]);

        assertArrayEquals(new double[3], solution);
    }

    @Test
    void pcgThrowsBreakdownWhenPreconditionerAnnihilatesResidual() {
        SparseMatrix matrix = matrix();

        IllegalStateException e = assertThrows(
                IllegalStateException.class,
                () -> new SparsePCGSolver(r -> new double[3])
                        .solve(matrix, rightHandSide()));

        org.junit.jupiter.api.Assertions.assertTrue(
                e.getMessage().contains("breakdown"),
                "a zero r·z must throw the breakdown error, not 'did not converge'");
    }

    @Test
    void pcgRejectsMismatchedDimensions() {
        SparseMatrix matrix = matrix();

        assertThrows(
                IllegalArgumentException.class,
                () -> new SparsePCGSolver(
                        new JacobiPreconditioner(matrix.getDiagonal()))
                        .solve(matrix, new double[2]));
    }

    @Test
    void denseDirectRejectsMismatchedDimensions() {
        SparseMatrix matrix = matrix();

        assertThrows(
                IllegalArgumentException.class,
                () -> new DenseDirectSolver().solve(matrix, new double[2]));
    }

    @Test
    void hybridRejectsMismatchedDimensions() {
        SparseMatrix matrix = matrix();

        assertThrows(
                IllegalArgumentException.class,
                () -> new HybridSolver(3).solve(matrix, new double[2]));
        assertThrows(
                IllegalArgumentException.class,
                () -> new HybridSolver(2).solve(matrix, new double[2]));
    }

    @Test
    void denseDirectKeepsCorrectLuSolutionOfLargeSystem() {
        // With ||b|| ~ 1e11, LU roundoff exceeds an absolute 1e-6 threshold;
        // the residual check must scale with ||b|| so the correct LU solution
        // is kept instead of being discarded for the SVD fallback
        int n = 30;
        SparseMatrix matrix = new SparseMatrix(n);
        for (int i = 0; i < n; i++) {
            matrix.set(i, i, 2.0e10);
            if (i + 1 < n) {
                matrix.set(i, i + 1, -1.0e9);
                matrix.set(i + 1, i, -1.0e9);
            }
        }
        double[] expected = new double[n];
        Arrays.fill(expected, 1.0);
        double[] rightHandSide = matrix.multiply(expected);

        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured));
        double[] solution;
        try {
            solution = new DenseDirectSolver().solve(matrix, rightHandSide);
        } finally {
            System.setErr(originalErr);
        }

        assertArrayEquals(expected, solution, 1.0e-6);
        assertFalse(captured.toString().contains("trying SVD"),
                "correct LU solution of a large system must not fall back to SVD");
    }

    private static SparseMatrix matrix() {
        SparseMatrix matrix = new SparseMatrix(3);
        matrix.set(0, 0, 4.0);
        matrix.set(0, 1, 1.0);
        matrix.set(1, 0, 1.0);
        matrix.set(1, 1, 3.0);
        matrix.set(1, 2, 1.0);
        matrix.set(2, 1, 1.0);
        matrix.set(2, 2, 2.0);
        return matrix;
    }

    private static double[] rightHandSide() {
        return new double[]{6.0, 10.0, 8.0};
    }
}
