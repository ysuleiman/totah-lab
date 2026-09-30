package totah.lab.aether.matrix;

import java.util.Objects;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.OneShotRhfReceipt;
import totah.lab.aether.provenance.ScientificStatus;

/** One Fock assembly and explicit Lowdin orbital solve using a supplied density. */
public final class OneShotRhfCalculator {
    public static final String IMPLEMENTATION = "aether-one-shot-rhf-1";
    public static final String NUMERICAL_BACKEND = "Apache Commons Math 3.6.1 EigenDecomposition; pure Java; serial";
    public static final String PROTOCOL = "s-only;bohr;hartree;binary64;" + DensityMatrix.CONVENTION
            + ";F=(Hcore+J)-0.5*K;Lowdin:X=U*diag(s^-1/2)*U^T;Fprime=X^T*F*X;C=X*Cprime;"
            + "S-exact-symmetric;F-relative-symmetry=1e-12;average-within-tolerance;"
            + "rank-cutoff=max(1e-12,1e-10*smax);no-regularization;validation=1e-10;"
            + "ascending-eigenvalues;equal-values-backend-index-order;"
            + "AO-column-sign=largest-absolute-entry-positive,first-index-exact-tie;" + NUMERICAL_BACKEND;
    private static final String REASON = "One-shot supplied-density Fock/orbital evidence only; no self-consistency or total energy";

    private OneShotRhfCalculator() {}

    public static OneShotRhfResult solve(DensityMatrix density, OverlapMatrix overlap,
                                         CoreHamiltonianMatrix core, JkCalculator.Result jk) {
        String protocol = IntegralMatrixData.protocol(PROTOCOL, density.functions());
        long started = System.nanoTime();
        Objects.requireNonNull(density); Objects.requireNonNull(overlap); Objects.requireNonNull(core); Objects.requireNonNull(jk);
        requireEqual(density.basisGeometryHash(), overlap.receipt().basisGeometryHash(), "overlap basis/geometry/order");
        requireEqual(density.basisGeometryHash(), core.receipt().basisGeometryHash(), "core basis/geometry/order");
        requireEqual(density.basisGeometryHash(), jk.receipt().basisGeometryHash(), "J/K basis/geometry/order");
        requireEqual(density.systemHash(), core.receipt().systemHash(), "core QuantumSystem");
        requireEqual(density.systemHash(), jk.receipt().systemHash(), "J/K QuantumSystem");
        requireEqual(density.densityHash(), jk.receipt().densityHash(), "supplied J/K density");
        requireEqual(OverlapMatrix.IMPLEMENTATION, overlap.receipt().implementation(), "overlap implementation");
        requireEqual(IntegralMatrixData.protocol(OverlapMatrix.PROTOCOL, density.functions()), overlap.receipt().protocol(), "overlap units/protocol");
        requireEqual(CoreHamiltonianMatrix.IMPLEMENTATION, core.receipt().implementation(), "core implementation");
        requireEqual(IntegralMatrixData.protocol(CoreHamiltonianMatrix.PROTOCOL, density.functions()), core.receipt().protocol(), "core units/protocol");
        requireEqual(JkCalculator.IMPLEMENTATION, jk.receipt().implementation(), "J/K implementation");
        requireEqual(IntegralMatrixData.protocol(JkCalculator.PROTOCOL, density.functions()), jk.receipt().protocol(), "J/K units/density convention");
        int n = density.size();
        RealMatrix s = new Array2DRowRealMatrix(n,n);
        RealMatrix f = new Array2DRowRealMatrix(n,n);
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            s.setEntry(i,j,overlap.get(i,j));
            f.setEntry(i,j,MeanFieldArithmetic.rhfFock(core.get(i,j),jk.coulomb().get(i,j),jk.exchange().get(i,j)));
        }
        var solution = OneShotNumerics.solve(s, f);
        String sources = "\naether-one-shot-inputs-v1\n" + density.systemHash() + "\n" + density.densityHash()
                + "\n" + overlap.receipt().receiptHash() + "\n" + core.receipt().receiptHash() + "\n" + jk.receipt().receiptHash();
        var fock = capture(density, solution.fock(), "aether-F-hartree-v1", sources);
        var x = capture(density, solution.orthogonalization(), "aether-X-dimensionless-v1", sources);
        var fp = capture(density, solution.orthogonalFock(), "aether-Fprime-hartree-v1", sources);
        var coefficients = capture(density, solution.coefficients(), "aether-C-dimensionless-v1", sources);
        var overlapValues = SpectralValues.capture(solution.overlapEigenvalues(), "aether-overlap-eigenvalues-v1");
        var energies = SpectralValues.capture(solution.energies(), "aether-orbital-energies-hartree-v1");
        String canonicalResult = "aether-one-shot-evidence-v1\n" + fock.identity().resultHash() + "\n"
                + overlapValues.resultHash() + "\n" + x.identity().resultHash() + "\n" + fp.identity().resultHash()
                + "\n" + coefficients.identity().resultHash() + "\n" + energies.resultHash() + "\n"
                + ContentHash.number(solution.generalizedResidual()) + "\n" + ContentHash.number(solution.orthonormalityError())
                + "\n" + ContentHash.number(solution.orthogonalizationError()) + "\n";
        var identity = IntegralMatrixData.identity(density.basisGeometryHash(), IMPLEMENTATION, protocol, REASON, sources, canonicalResult);
        var receipt = new OneShotRhfReceipt(IMPLEMENTATION, protocol, density.basisGeometryHash(), density.systemHash(), density.densityHash(),
                overlap.receipt(), core.receipt(), jk.receipt(), fock.identity().resultHash(), overlapValues.resultHash(),
                x.identity().resultHash(), fp.identity().resultHash(), coefficients.identity().resultHash(), energies.resultHash(),
                identity.calculationHash(), identity.resultHash(), ScientificStatus.SCREENING_ONLY, REASON, identity.receiptHash());
        return new OneShotRhfResult(density, new FockMatrix(fock, receipt), new OverlapEigenvalues(overlapValues, receipt),
                new OrthogonalizationMatrix(x, receipt), new OrthogonalFockMatrix(fp, receipt),
                new MolecularOrbitalCoefficients(coefficients, receipt), new OrbitalEnergies(energies, receipt), receipt,
                new OneShotRhfResult.Diagnostics(solution.generalizedResidual(), solution.orthonormalityError(), solution.orthogonalizationError()),
                new OneShotRhfResult.PerformanceCounters(n, solution.overlapTime(), solution.transformTime(), solution.fockTime(), System.nanoTime() - started));
    }

    private static IntegralMatrixData capture(DensityMatrix density, RealMatrix matrix, String domain, String sources) {
        return IntegralMatrixData.capture(density.functions(), matrix::getEntry, IMPLEMENTATION, IntegralMatrixData.protocol(PROTOCOL, density.functions()), domain, REASON, sources);
    }

    private static void requireEqual(String expected, String actual, String component) {
        if (!expected.equals(actual)) throw new IllegalArgumentException("Incompatible " + component + " provenance");
    }
}
