package totah.lab.aether.matrix;

import java.util.List;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.JkReceipt;

/** Immutable K_mu_nu = sum_lambda_sigma P_lambda_sigma (mu lambda|nu sigma), hartree. */
public final class ExchangeMatrix {
    private final IntegralMatrixData data;
    private final QuantumSystem system;
    private final JkReceipt receipt;

    ExchangeMatrix(IntegralMatrixData data, QuantumSystem system, JkReceipt receipt) {
        this.data = data;
        this.system = system;
        this.receipt = receipt;
    }

    public int size() { return data.size(); }
    public double get(int row, int column) { return data.get(row, column); }
    public List<ContractedGaussian> functions() { return data.functions(); }
    public QuantumSystem system() { return system; }
    public JkReceipt receipt() { return receipt; }
}
