package totah.lab.aether.matrix;

import totah.lab.aether.provenance.OneShotRhfReceipt;

/** Immutable evidence from one supplied density; no density or occupations are built. */
public final class OneShotRhfResult {
    private final DensityMatrix density;
    private final FockMatrix fock;
    private final OverlapEigenvalues overlapEigenvalues;
    private final OrthogonalizationMatrix orthogonalization;
    private final OrthogonalFockMatrix orthogonalFock;
    private final MolecularOrbitalCoefficients coefficients;
    private final OrbitalEnergies energies;
    private final OneShotRhfReceipt receipt;
    private final Diagnostics diagnostics;
    private final PerformanceCounters performanceCounters;

    OneShotRhfResult(DensityMatrix density, FockMatrix fock, OverlapEigenvalues overlapEigenvalues,
                     OrthogonalizationMatrix orthogonalization, OrthogonalFockMatrix orthogonalFock,
                     MolecularOrbitalCoefficients coefficients, OrbitalEnergies energies,
                     OneShotRhfReceipt receipt, Diagnostics diagnostics, PerformanceCounters performanceCounters) {
        this.density = density; this.fock = fock; this.overlapEigenvalues = overlapEigenvalues;
        this.orthogonalization = orthogonalization; this.orthogonalFock = orthogonalFock;
        this.coefficients = coefficients; this.energies = energies; this.receipt = receipt;
        this.diagnostics = diagnostics; this.performanceCounters = performanceCounters;
    }

    public DensityMatrix density() { return density; }
    public FockMatrix fock() { return fock; }
    public OverlapEigenvalues overlapEigenvalues() { return overlapEigenvalues; }
    public OrthogonalizationMatrix orthogonalization() { return orthogonalization; }
    public OrthogonalFockMatrix orthogonalFock() { return orthogonalFock; }
    public MolecularOrbitalCoefficients coefficients() { return coefficients; }
    public OrbitalEnergies energies() { return energies; }
    public OneShotRhfReceipt receipt() { return receipt; }
    public Diagnostics diagnostics() { return diagnostics; }
    public PerformanceCounters performanceCounters() { return performanceCounters; }

    public record Diagnostics(double maxGeneralizedEigenResidual, double maxOrthonormalityError,
                              double maxOrthogonalizationError) {}
    public record PerformanceCounters(int matrixDimension, long overlapEigendecompositionNanos,
                                      long fockTransformNanos, long fockEigendecompositionNanos,
                                      long totalOneShotNanos) {}
}
