package totah.lab.prometheus.variational;

/** Stateless/thread-safe optimizer of an explicitly defined variational problem. */
public interface ParameterOptimizer {
    String optimizerId();

    VariationalResult optimize(VariationalProblem problem);
}
