package totah.lab.euclid.linear;

/** Solves a linear system represented by a sparse matrix. */
public interface LinearSolver {
    /**
     * Solves {@code A x = b}.
     *
     * @param matrix square coefficient matrix
     * @param rightHandSide right-hand-side vector
     * @return solution vector
     */
    double[] solve(SparseMatrix matrix, double[] rightHandSide);
}
