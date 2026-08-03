package totah.lab.euclid.linear;


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
        if (H.size != V.length) {
            throw new IllegalArgumentException(
                    "Matrix size and right-hand side length must match");
        }
        int n = V.length;
        if (n <= directMaxSize) {
            return new DenseDirectSolver().solve(H, V);
        } else {
            return new SparsePCGSolver(preconditionerFactory.create(H, V.length)).solve(H, V);
        }
    }
}
