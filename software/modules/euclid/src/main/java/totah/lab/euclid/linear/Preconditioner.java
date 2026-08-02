package totah.lab.euclid.linear;


import totah.lab.euclid.linear.preconditioner.BlockJacobiPreconditioner;
import totah.lab.euclid.linear.preconditioner.IncompleteCholeskyPreconditioner;
import totah.lab.euclid.linear.preconditioner.JacobiPreconditioner;

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