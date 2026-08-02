package totah.lab.math.linear;


/**
 * Switches between dense direct and sparse iterative based on system size.
 */
public class HybridSolver implements LinearSolver {
    private final int directMaxSize;
    private final Preconditioner.Factory preconditionerFactory;

    public HybridSolver(int directMaxSize) {
        this(directMaxSize, Preconditioner.jacobi());
    }

    public HybridSolver(int directMaxSize, Preconditioner.Factory preconditionerFactory) {
        this.directMaxSize = directMaxSize;
        this.preconditionerFactory = preconditionerFactory;
    }

    @Override
    public double[] solve(SparseMatrix H, double[] V) {
        int n = V.length - 1;
        if (n <= directMaxSize) {
            return new DenseDirectSolver().solve(H, V);
        } else {
            // The KKT system is n+1 (Lagrange multiplier appended); the
            // preconditioner must cover the full system
            return new SparsePCGSolver(preconditionerFactory.create(H, V.length)).solve(H, V);
        }
    }
}