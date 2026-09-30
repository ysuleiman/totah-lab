package totah.lab.aether.matrix;

import totah.lab.aether.provenance.OneShotRhfReceipt;

/** Ascending eigenvalues of S; dimensionless, strictly above the frozen rank cutoff. */
public final class OverlapEigenvalues {
    private final SpectralValues data;
    private final OneShotRhfReceipt receipt;

    OverlapEigenvalues(SpectralValues data, OneShotRhfReceipt receipt) {
        this.data = data;
        this.receipt = receipt;
    }

    public int size() { return data.values().size(); }
    public double get(int index) { return data.values().get(index); }
    public OneShotRhfReceipt receipt() { return receipt; }
}

