package totah.lab.aether.matrix;

import java.util.Objects;
import totah.lab.aether.provenance.JkReceipt;
import totah.lab.aether.provenance.ScientificStatus;

/** Direct unscreened closed-shell J/K contractions from a supplied density and packed ERIs. */
public final class JkCalculator {
    public static final String IMPLEMENTATION = "aether-jk-1";
    /** Convention only; no Fock matrix is constructed in this milestone. */
    public static final String FUTURE_FOCK_CONVENTION = "F=Hcore+J-0.5*K";
    public static final String PROTOCOL = "s-only;bohr;hartree;binary64;ordered-lambda-then-sigma-sum;"
            + "upper-output-triangle;packed-eri-access;no-screening;no-density-shortcuts;"
            + "J_mu_nu=sum(P_lambda_sigma*(mu nu|lambda sigma));"
            + "K_mu_nu=sum(P_lambda_sigma*(mu lambda|nu sigma));"
            + DensityMatrix.CONVENTION + ";future-convention=" + FUTURE_FOCK_CONVENTION;

    private JkCalculator() {}

    public static Result calculate(DensityMatrix density, ElectronRepulsionTensor eri) {
        long started = System.nanoTime();
        Objects.requireNonNull(density);
        Objects.requireNonNull(eri);
        requireEqual(density.systemHash(), eri.receipt().systemHash(), "QuantumSystem");
        requireEqual(density.basisGeometryHash(), eri.receipt().basisGeometryHash(), "basis/geometry/order");
        requireEqual(ElectronRepulsionTensor.IMPLEMENTATION, eri.receipt().implementation(), "ERI implementation");
        requireEqual(IntegralMatrixData.protocol(ElectronRepulsionTensor.PROTOCOL, density.functions()), eri.receipt().protocol(), "ERI units/numerical protocol");
        String protocol = IntegralMatrixData.protocol(PROTOCOL, density.functions());
        String reason = "Supplied-density J/K matrices only; arbitrary inputs are not reference-validated";
        String sources = "\naether-jk-inputs-v1\n" + density.systemHash() + "\n" + density.densityHash()
                + "\n" + eri.receipt().receiptHash();
        long[] accumulations = new long[2];
        var j = IntegralMatrixData.compose(density.functions(),
                (mu, nu) -> contract(density, (lambda, sigma) -> eri.get(mu, nu, lambda, sigma), accumulations, 0),
                IMPLEMENTATION, protocol, "aether-J-hartree-v1", reason, sources);
        var k = IntegralMatrixData.compose(density.functions(),
                (mu, nu) -> contract(density, (lambda, sigma) -> eri.get(mu, lambda, nu, sigma), accumulations, 1),
                IMPLEMENTATION, protocol, "aether-K-hartree-v1", reason, sources);
        var identity = IntegralMatrixData.identity(density.basisGeometryHash(), IMPLEMENTATION, protocol,
                reason, sources, "aether-JK-hartree-v1\n" + j.identity().resultHash() + "\n" + k.identity().resultHash() + "\n");
        var receipt = new JkReceipt(IMPLEMENTATION, protocol, density.basisGeometryHash(), density.systemHash(),
                density.densityHash(), eri.receipt(), j.identity().resultHash(), k.identity().resultHash(),
                identity.calculationHash(), identity.resultHash(), ScientificStatus.SCREENING_ONLY, reason, identity.receiptHash());
        var coulomb = new CoulombMatrix(j, density.system(), receipt);
        var exchange = new ExchangeMatrix(k, density.system(), receipt);
        var counters = new PerformanceCounters(density.size(), accumulations[0] + accumulations[1],
                accumulations[0], accumulations[1], System.nanoTime() - started);
        return new Result(coulomb, exchange, counters);
    }

    static double contract(DensityMatrix density, IntegralMatrixData.Entry integral, long[] counters, int counter) {
        double sum = 0;
        for (int lambda = 0; lambda < density.size(); lambda++) {
            for (int sigma = 0; sigma < density.size(); sigma++) {
                double term = density.get(lambda, sigma) * integral.get(lambda, sigma);
                counters[counter]++;
                sum += term;
                if (!Double.isFinite(term) || !Double.isFinite(sum)) {
                    throw new ArithmeticException("NUMERICAL_FAILURE: nonfinite J/K contraction");
                }
            }
        }
        return sum;
    }

    private static void requireEqual(String expected, String actual, String component) {
        if (!expected.equals(actual)) throw new IllegalArgumentException("Incompatible " + component + " provenance");
    }

    /** Immutable paired result with one receipt covering both typed matrices. */
    public static final class Result {
        private final CoulombMatrix coulomb;
        private final ExchangeMatrix exchange;
        private final PerformanceCounters performanceCounters;

        private Result(CoulombMatrix coulomb, ExchangeMatrix exchange, PerformanceCounters performanceCounters) {
            this.coulomb = coulomb;
            this.exchange = exchange;
            this.performanceCounters = performanceCounters;
        }

        public CoulombMatrix coulomb() { return coulomb; }
        public ExchangeMatrix exchange() { return exchange; }
        public JkReceipt receipt() { return coulomb.receipt(); }
        public PerformanceCounters performanceCounters() { return performanceCounters; }
    }

    /** Counts packed get calls (including repeated slots), not distinct slot addresses; timing is not hashed. */
    public record PerformanceCounters(int densityDimension, long eriSlotsRead, long jAccumulations,
                                      long kAccumulations, long elapsedNanos) {}
}
