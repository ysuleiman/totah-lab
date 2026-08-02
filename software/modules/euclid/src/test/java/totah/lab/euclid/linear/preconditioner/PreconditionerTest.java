package totah.lab.euclid.linear.preconditioner;

import org.junit.jupiter.api.Test;
import totah.lab.euclid.linear.Preconditioner;
import totah.lab.euclid.linear.SparseMatrix;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Preconditioners approximate A^{-1}; tests pin the exact cases (diagonal /
 * block-diagonal systems where the approximation is exact) and the fallback
 * paths (zero diagonal, singular block).
 */
public class PreconditionerTest {

    @Test
    public void jacobiScalesByInverseDiagonal() {
        Preconditioner jacobi = new JacobiPreconditioner(new double[]{2.0, 4.0, 8.0});
        double[] z = jacobi.apply(new double[]{1.0, 1.0, 1.0});
        assertArrayEquals(new double[]{0.5, 0.25, 0.125}, z, 1e-12,
                "Jacobi must return r ./ diag");
    }

    @Test
    public void jacobiTreatsZeroDiagonalAsOne() {
        // The KKT constraint row has a zero diagonal entry and must survive
        Preconditioner jacobi = new JacobiPreconditioner(new double[]{0.0, 2.0});
        double[] z = jacobi.apply(new double[]{3.0, 4.0});
        assertArrayEquals(new double[]{3.0, 2.0}, z, 1e-12,
                "zero diagonal must fall back to a factor of 1.0");
    }

    @Test
    public void blockJacobiExactlyInvertsBlockDiagonalMatrix() {
        SparseMatrix a = new SparseMatrix(3);
        a.set(0, 0, 2.0); a.set(0, 1, 1.0);
        a.set(1, 0, 1.0); a.set(1, 1, 2.0);
        a.set(2, 2, 5.0);

        Preconditioner blockJacobi =
                new BlockJacobiPreconditioner(a, List.of(new int[]{0, 1}, new int[]{2}));

        double[] r = {1.0, 2.0, 10.0};
        double[] z = blockJacobi.apply(r);

        // On a block-diagonal matrix the preconditioner is the exact inverse
        double[] az = a.multiply(z);
        assertArrayEquals(r, az, 1e-9,
                "block-Jacobi apply must satisfy A·z = r on block-diagonal A");
    }

    @Test
    public void blockJacobiFallsBackToIdentityForSingularBlock() {
        SparseMatrix a = new SparseMatrix(2);
        a.set(0, 0, 1.0); a.set(0, 1, 1.0);
        a.set(1, 0, 1.0); a.set(1, 1, 1.0);

        Preconditioner blockJacobi =
                new BlockJacobiPreconditioner(a, List.of(new int[]{0, 1}));

        double[] r = {3.0, -2.0};
        assertArrayEquals(r, blockJacobi.apply(r), 1e-12,
                "singular block must degrade to the identity, not NaN/exception");
    }

    @Test
    public void incompleteCholeskyIsExactForDiagonalSpd() {
        SparseMatrix a = new SparseMatrix(3);
        a.set(0, 0, 4.0);
        a.set(1, 1, 9.0);
        a.set(2, 2, 16.0);

        Preconditioner ic = new IncompleteCholeskyPreconditioner(a, 3);
        double[] z = ic.apply(new double[]{4.0, 9.0, 32.0});
        assertArrayEquals(new double[]{1.0, 1.0, 2.0}, z, 1e-9,
                "IC(0) must be exact on a diagonal SPD matrix");
    }

    @Test
    public void incompleteCholeskyIsExactForTridiagonalSpd() {
        // Cholesky of a tridiagonal SPD matrix has zero fill-in, so IC(0) == exact
        int n = 5;
        SparseMatrix a = new SparseMatrix(n);
        for (int i = 0; i < n; i++) {
            a.set(i, i, 2.0);
            if (i + 1 < n) {
                a.set(i, i + 1, -1.0);
                a.set(i + 1, i, -1.0);
            }
        }

        Preconditioner ic = new IncompleteCholeskyPreconditioner(a, n);
        double[] r = {1.0, -0.5, 0.25, 0.75, -1.0};
        double[] z = ic.apply(r);

        double[] az = a.multiply(z);
        assertArrayEquals(r, az, 1e-9,
                "IC(0) must be exact on a tridiagonal SPD matrix (no fill-in)");
    }
}
