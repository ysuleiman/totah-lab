package totah.lab.aether.matrix;

import totah.lab.aether.provenance.OneShotRhfReceipt;

/** Immutable symmetric Lowdin X = S^-1/2; dimensionless. */
public final class OrthogonalizationMatrix {
    private final IntegralMatrixData data;
    private final OneShotRhfReceipt receipt;

    OrthogonalizationMatrix(IntegralMatrixData data, OneShotRhfReceipt receipt) {
        this.data = data;
        this.receipt = receipt;
    }

    public int size() { return data.size(); }
    public double get(int row, int column) { return data.get(row, column); }
    public OneShotRhfReceipt receipt() { return receipt; }
}

