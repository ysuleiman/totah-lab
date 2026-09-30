package totah.lab.aether.matrix;

import totah.lab.aether.provenance.OneShotRhfReceipt;

/** Immutable dimensionless AO coefficients; rows are basis functions, columns are ascending-energy orbitals. */
public final class MolecularOrbitalCoefficients {
    private final IntegralMatrixData data;
    private final OneShotRhfReceipt receipt;

    MolecularOrbitalCoefficients(IntegralMatrixData data, OneShotRhfReceipt receipt) {
        this.data = data;
        this.receipt = receipt;
    }

    public int size() { return data.size(); }
    public double get(int row, int column) { return data.get(row, column); }
    public OneShotRhfReceipt receipt() { return receipt; }
}

