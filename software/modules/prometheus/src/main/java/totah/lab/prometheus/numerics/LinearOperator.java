package totah.lab.prometheus.numerics;

/** Immutable-dimension matrix action that need not materialize a matrix. */
public interface LinearOperator {
    int dimension();
    double[] apply(double[] vector);
}
