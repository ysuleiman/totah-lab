package totah.lab.math.linear;


import totah.lab.math.linear.preconditioner.BlockJacobiPreconditioner;
import totah.lab.math.linear.preconditioner.IncompleteCholeskyPreconditioner;
import totah.lab.math.linear.preconditioner.JacobiPreconditioner;

/**
 * Preconditioner interface for iterative solvers.
 */
public interface Preconditioner {
    /** Apply M^{-1} to vector r, return z */
    double[] apply(double[] r);

    /**
     * Factory for creating preconditioners from a matrix.
     */
    interface Factory {
        Preconditioner create(SparseMatrix A, int n);
    }

    // Convenience factories
    static Factory jacobi() {
        return (A, n) -> new JacobiPreconditioner(A.getDiagonal());
    }

    static Factory blockJacobi(java.util.List<int[]> blocks) {
        return (A, n) -> new BlockJacobiPreconditioner(A, blocks);
    }

    static Factory incompleteCholesky() {
        return IncompleteCholeskyPreconditioner::new;
    }
}