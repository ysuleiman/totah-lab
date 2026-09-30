package totah.lab.aether.matrix;

import totah.lab.aether.provenance.OneShotRhfReceipt;

/** Immutable F = Hcore + J - 0.5 K in hartree, for the supplied density. */
public final class FockMatrix {
    private final IntegralMatrixData data;
    private final OneShotRhfReceipt receipt;

    FockMatrix(IntegralMatrixData data, OneShotRhfReceipt receipt) {
        this.data = data;
        this.receipt = receipt;
    }

    public int size() { return data.size(); }
    public double get(int row, int column) { return data.get(row, column); }
    public OneShotRhfReceipt receipt() { return receipt; }
}

