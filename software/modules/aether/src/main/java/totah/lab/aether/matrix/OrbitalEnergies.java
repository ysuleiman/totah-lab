package totah.lab.aether.matrix;

import totah.lab.aether.provenance.OneShotRhfReceipt;

/** Ascending one-shot orbital eigenvalues in hartree; not total RHF energy. */
public final class OrbitalEnergies {
    private final SpectralValues data;
    private final OneShotRhfReceipt receipt;

    OrbitalEnergies(SpectralValues data, OneShotRhfReceipt receipt) {
        this.data = data;
        this.receipt = receipt;
    }

    public int size() { return data.values().size(); }
    public double get(int index) { return data.values().get(index); }
    public OneShotRhfReceipt receipt() { return receipt; }
}

