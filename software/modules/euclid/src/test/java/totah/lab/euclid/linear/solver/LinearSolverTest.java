package totah.lab.euclid.linear.solver;

import org.junit.jupiter.api.Test;
import totah.lab.euclid.linear.DenseDirectSolver;
import totah.lab.euclid.linear.HybridSolver;
import totah.lab.euclid.linear.SparseMatrix;
import totah.lab.euclid.linear.SparsePCGSolver;
import totah.lab.euclid.linear.preconditioner.JacobiPreconditioner;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
    void pcgRejectsMismatchedDimensions() {
        SparseMatrix matrix = matrix();

        assertThrows(
                IllegalArgumentException.class,
                () -> new SparsePCGSolver(
                        new JacobiPreconditioner(matrix.getDiagonal()))
                        .solve(matrix, new double[2]));
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
