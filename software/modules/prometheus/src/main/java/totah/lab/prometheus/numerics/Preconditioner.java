package totah.lab.prometheus.numerics;

/** Fixed linear inverse approximation used by a Krylov solver. */
public interface Preconditioner {
    int dimension();
    double[] apply(double[] residual);
    String identity();
}
