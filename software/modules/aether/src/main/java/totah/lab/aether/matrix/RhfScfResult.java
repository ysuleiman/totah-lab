package totah.lab.aether.matrix;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import totah.lab.aether.provenance.ScientificStatus;

/** Immutable SCF trajectory. Only convergedState() exposes an explicitly converged state. */
public final class RhfScfResult {
    public enum Status { CONVERGED, MAX_ITERATIONS, NUMERICAL_FAILURE, UNSUPPORTED_SYSTEM }

    /** Energy and Fock use inputDensity; outputDensity is the occupied density from orbitals. */
    public record Iteration(int number, OccupiedDensityCalculator.Result inputDensity,
                            OneShotRhfResult orbitals, OccupiedDensityCalculator.Result outputDensity,
                            RhfEnergyCalculator.Result energy, IterationReceipt receipt) {}

    /** First iteration has no previous physical-state energy, so deltaEnergy is empty and cannot pass. */
    public record IterationReceipt(int iterationNumber, double electronicEnergy, double totalEnergy,
                                   OptionalDouble deltaEnergy, double densityResidual,
                                   List<Double> orbitalEnergies, String densityHash, String fockHash,
                                   String outputDensityHash, String orbitalReceiptHash, String energyReceiptHash,
                                   String inputConstructionHash, String outputConstructionHash,
                                   boolean energyCriterionPassed, boolean densityCriterionPassed) {
        public IterationReceipt { orbitalEnergies = List.copyOf(orbitalEnergies); }
        public ScientificStatus scientificStatus() { return ScientificStatus.SCREENING_ONLY; }
    }

    public record Receipt(String implementation, String protocol, String systemHash, String basisGeometryHash,
                          String initialGuess, String initialDensityReceiptHash,
                          double energyThreshold, double densityThreshold, int maximumIterations,
                          List<IterationReceipt> iterations, Status termination, String reason,
                          String calculationHash, String resultHash, String receiptHash) {
        public Receipt { iterations = List.copyOf(iterations); }
        public ScientificStatus scientificStatus() { return ScientificStatus.SCREENING_ONLY; }
    }

    /** Wall-clock timings include the core guess; no timing contributes to a receipt. */
    public record PerformanceCounters(int systemDimension, int iterationCount, long totalJkNanos,
                                       long totalEigensolveNanos, long totalScfNanos,
                                       OptionalDouble finalDeltaEnergy, OptionalDouble finalDensityResidual) {}

    private final List<Iteration> iterations;
    private final Optional<OccupiedDensityCalculator.Result> initialDensity;
    private final Receipt receipt;
    private final PerformanceCounters performanceCounters;

    RhfScfResult(List<Iteration> iterations, Optional<OccupiedDensityCalculator.Result> initialDensity,
                 Receipt receipt, PerformanceCounters performanceCounters) {
        this.iterations = List.copyOf(iterations); this.initialDensity = initialDensity;
        this.receipt = receipt; this.performanceCounters = performanceCounters;
    }
    public Status status() { return receipt.termination(); }
    public ScientificStatus scientificStatus() { return ScientificStatus.SCREENING_ONLY; }
    public List<Iteration> iterations() { return iterations; }
    public Optional<OccupiedDensityCalculator.Result> initialDensity() { return initialDensity; }
    public Optional<Iteration> convergedState() {
        return status() == Status.CONVERGED ? Optional.of(iterations.getLast()) : Optional.empty();
    }
    public Receipt receipt() { return receipt; }
    public PerformanceCounters performanceCounters() { return performanceCounters; }
}
