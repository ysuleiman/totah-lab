package totah.lab.aether.matrix;

import java.util.List;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.CoreHamiltonianReceipt;
import totah.lab.aether.provenance.ScientificStatus;

/** Immutable one-electron Hcore = T + V, in hartree; not an electronic energy. */
public final class CoreHamiltonianMatrix {
    public static final String IMPLEMENTATION = "aether-core-hamiltonian-1";
    public static final String PROTOCOL = "s-only;bohr;hartree;operator=T+V;binary64;"
            + "element-wise-T-then-V;checked-system-basis-order;no-screening;"
            + "T={" + KineticMatrix.PROTOCOL + "};V={" + NuclearAttractionMatrix.PROTOCOL + "}";
    private final IntegralMatrixData data;
    private final QuantumSystem system;
    private final CoreHamiltonianReceipt receipt;

    CoreHamiltonianMatrix(QuantumSystem system, String systemHash,
                          KineticMatrix kinetic, NuclearAttractionMatrix attraction) {
        this.system = system;
        String protocol = IntegralMatrixData.protocol(PROTOCOL, kinetic.functions());
        String reason = "One-electron core Hamiltonian only; arbitrary inputs are not reference-validated";
        String sources = "\naether-core-inputs-v1\n" + systemHash + "\n"
                + kinetic.receipt().receiptHash() + "\n" + attraction.receipt().receiptHash();
        data = IntegralMatrixData.compose(kinetic.functions(), (row, column) -> {
            double value = kinetic.get(row, column) + attraction.get(row, column);
            if (!Double.isFinite(value)) throw new ArithmeticException("NUMERICAL_FAILURE: nonfinite core Hamiltonian");
            return value;
        }, IMPLEMENTATION, protocol, "aether-Hcore-hartree-v1", reason, sources);
        var identity = data.identity();
        receipt = new CoreHamiltonianReceipt(IMPLEMENTATION, protocol, identity.basisGeometryHash(),
                systemHash, kinetic.receipt(), attraction.receipt(), identity.calculationHash(),
                identity.resultHash(), ScientificStatus.SCREENING_ONLY, reason, identity.receiptHash());
    }

    public int size() { return data.size(); }
    public double get(int row, int column) { return data.get(row, column); }
    public List<ContractedGaussian> functions() { return data.functions(); }
    public QuantumSystem system() { return system; }
    public CoreHamiltonianReceipt receipt() { return receipt; }
}
