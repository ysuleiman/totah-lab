package totah.lab.math.linear;

/**
 * Linear solver interface for QEq KKT systems.
 */
public interface LinearSolver {
    /**
     * Solve [H 1; 1^T 0] · [q; μ] = [V; Q]
     * @param H  Sparse hardness matrix (n×n), will be augmented internally
     * @param V  Voltage vector length n+1 (last element is total charge)
     * @return  Solution vector length n+1 (charges + Lagrange multiplier)
     */
    double[] solve(SparseMatrix H, double[] V);
}