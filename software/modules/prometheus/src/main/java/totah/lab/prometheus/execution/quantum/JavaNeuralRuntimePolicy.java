package totah.lab.prometheus.execution.quantum;

/** Frozen Step-0 numerical policy. Diagnostic reference implementations are not selectable. */
public final class JavaNeuralRuntimePolicy {
    public static final String BACKEND_ID = "prometheus-java-neural-v1";
    public static final String BACKEND_VERSION = "step0-java-runtime-v1";
    public static final String OPTIMIZER = "BLOCK_PRECONDITIONED_MATRIX_FREE_SR";
    public static final String FORCE_ESTIMATOR = "ANALYTIC_DIFFERENTIAL_SWCT";
    public static final String DENSE_SOLVER = "REFERENCE_ORACLE_NOT_SELECTABLE";
    public static final String NUMERICAL_SWCT = "REFERENCE_ORACLE_NOT_SELECTABLE";

    private JavaNeuralRuntimePolicy() { }
}
