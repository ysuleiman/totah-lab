package totah.lab.aether.matrix;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import totah.lab.aether.provenance.ScientificStatus;

/** Policy-aware evidence: physical Fock/orbitals are distinct from DIIS update operators. */
public final class RhfScfRun {
    public record DiisUpdate(List<Integer> historyIterations, List<String> historyFockHashes,
                             List<String> historyErrorHashes, List<Double> coefficients, List<String> events,
                             double errorMaximum, double reciprocalCondition, boolean extrapolated,
                             String physicalOrbitalReceiptHash, String updateFockHash, String updateCoefficientsHash,
                             String updateEnergiesHash, String occupationHash, String outputDensityHash,
                             double tracePS, double idempotencyError, double orthonormalityError) {
        public DiisUpdate { historyIterations=List.copyOf(historyIterations); historyFockHashes=List.copyOf(historyFockHashes);
            historyErrorHashes=List.copyOf(historyErrorHashes);coefficients=List.copyOf(coefficients);events=List.copyOf(events); }
    }
    public record Iteration(int number, DensityMatrix density, OneShotRhfResult physicalOrbitals,
                            RhfEnergyCalculator.Result energy, DensityMatrix nextDensity,
                            OptionalDouble deltaEnergy, double densityResidual, boolean energyCriterionPassed,
                            boolean densityCriterionPassed, Optional<DiisUpdate> update) {}
    public record Receipt(ScfPolicy policy, String protocol, String systemHash, String basisGeometryHash,
                          String initialDensityReceiptHash, int maximumIterations, RhfScfResult.Status termination,
                          String reason, String trajectory, String calculationHash, String resultHash, String receiptHash) {
        public ScientificStatus scientificStatus() { return ScientificStatus.SCREENING_ONLY; }
    }
    private final List<Iteration> iterations;
    private final Receipt receipt;
    private final RhfScfResult.PerformanceCounters performanceCounters;
    private final Optional<RhfScfResult> plainResult;
    private final Optional<BasisPerformance> basisPerformance;
    RhfScfRun(List<Iteration> iterations, Receipt receipt, RhfScfResult.PerformanceCounters counters,
              Optional<RhfScfResult> plainResult) {
        this(iterations,receipt,counters,plainResult,Optional.empty());
    }
    private RhfScfRun(List<Iteration> iterations,Receipt receipt,RhfScfResult.PerformanceCounters counters,
            Optional<RhfScfResult> plainResult,Optional<BasisPerformance> basisPerformance) {
        this.basisPerformance=basisPerformance;
        this.iterations=List.copyOf(iterations);this.receipt=receipt;this.performanceCounters=counters;this.plainResult=plainResult;
    }
    RhfScfRun withBasisPerformance(BasisPerformance profile) {
        return new RhfScfRun(iterations,receipt,performanceCounters,plainResult,Optional.ofNullable(profile));
    }
    public Optional<BasisPerformance> basisPerformance() { return basisPerformance; }
    public List<Iteration> iterations() { return iterations; }
    public RhfScfResult.Status status() { return receipt.termination(); }
    public ScientificStatus scientificStatus() { return ScientificStatus.SCREENING_ONLY; }
    public Optional<Iteration> convergedState() { return status()==RhfScfResult.Status.CONVERGED?Optional.of(iterations.getLast()):Optional.empty(); }
    public Receipt receipt() { return receipt; }
    public RhfScfResult.PerformanceCounters performanceCounters() { return performanceCounters; }
    public Optional<RhfScfResult> plainResult() { return plainResult; }
    static RhfScfRun plain(RhfScfResult result) {
        var rows=result.iterations().stream().map(i->new Iteration(i.number(),i.inputDensity().density(),i.orbitals(),i.energy(),
                i.outputDensity().density(),i.receipt().deltaEnergy(),i.receipt().densityResidual(),
                i.receipt().energyCriterionPassed(),i.receipt().densityCriterionPassed(),Optional.<DiisUpdate>empty())).toList();
        var r=result.receipt();
        return new RhfScfRun(rows,new Receipt(ScfPolicy.PLAIN,r.protocol(),r.systemHash(),r.basisGeometryHash(),
                r.initialDensityReceiptHash(),r.maximumIterations(),r.termination(),r.reason(),r.toString(),
                r.calculationHash(),r.resultHash(),r.receiptHash()),result.performanceCounters(),Optional.of(result));
    }
}
