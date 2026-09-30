package totah.lab.aether.matrix;

import totah.lab.aether.provenance.OneShotRhfReceipt;

/** Immutable F prime = X^T F X in hartree. */
public final class OrthogonalFockMatrix {
    private final IntegralMatrixData data;
    private final OneShotRhfReceipt receipt;

    OrthogonalFockMatrix(IntegralMatrixData data, OneShotRhfReceipt receipt) {
        this.data = data;
        this.receipt = receipt;
    }

    public int size() { return data.size(); }
    public double get(int row, int column) { return data.get(row, column); }
    public OneShotRhfReceipt receipt() { return receipt; }
}

