package totah.lab.aether.matrix;

import java.util.List;
import java.util.Objects;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ScientificStatus;

/**
 * Immutable supplied real symmetric AO density, in the doubled closed-shell convention.
 * Entries are dimensionless. No trace, positivity, idempotency or orbital-occupation
 * assertion is made: arbitrary symmetric inputs are allowed for contraction validation.
 */
public final class DensityMatrix {
    public static final String CONVENTION = "P_mu_nu=2*sum_occupied(C_mu_i*C_nu_i);real;symmetric;spin-summed";
    public static final String IMPLEMENTATION = "aether-supplied-density-1";
    public static final String PROTOCOL = "s-only;bohr-basis;dimensionless-density;binary64;"
            + "supplied-row-major;exact-symmetry;" + CONVENTION;
    private final QuantumSystem system;
    private final String systemHash;
    private final IntegralMatrixData data;

    private DensityMatrix(QuantumSystem system, List<ContractedGaussian> functions, List<Double> rowMajor) {
        this.system = Objects.requireNonNull(system);
        if (system.multiplicity() != 1) throw new IllegalArgumentException("Closed-shell density requires a singlet system");
        var basis = List.copyOf(functions);
        var entries = List.copyOf(rowMajor);
        int n = basis.size();
        if (n == 0 || entries.size() != (long) n * n) throw new IllegalArgumentException("Density dimensions must match the ordered basis");
        for (double value : entries) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Density entries must be finite");
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (entries.get(i * n + j).doubleValue() != entries.get(j * n + i).doubleValue()) {
                    throw new IllegalArgumentException("Density must be exactly symmetric; no implicit averaging");
                }
            }
        }
        String protocol = IntegralMatrixData.protocol(PROTOCOL, basis);
        systemHash = IntegralMatrixData.systemHash(system);
        data = IntegralMatrixData.compose(basis, (i, j) -> entries.get(i * n + j), IMPLEMENTATION,
                protocol, "aether-P-dimensionless-v1", "Supplied density only; physical admissibility is not asserted",
                "\naether-density-system-v1\n" + systemHash);
    }

    /** Consume all n*n row-major entries, verify both triangles, and retain an immutable typed value. */
    public static DensityMatrix fromRowMajor(QuantumSystem system, List<ContractedGaussian> functions, List<Double> rowMajor) {
        return new DensityMatrix(system, functions, rowMajor);
    }

    public int size() { return data.size(); }
    public double get(int row, int column) { return data.get(row, column); }
    public List<ContractedGaussian> functions() { return data.functions(); }
    public QuantumSystem system() { return system; }
    public ScientificStatus status() { return ScientificStatus.SCREENING_ONLY; }
    /** Commits to all values, system, ordered basis and the frozen density convention. */
    public String densityHash() { return data.identity().receiptHash(); }
    String systemHash() { return systemHash; }
    String basisGeometryHash() { return data.identity().basisGeometryHash(); }
}
