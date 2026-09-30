package totah.lab.aether.matrix;

import java.util.List;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.integral.SNuclearAttraction;
import totah.lab.aether.model.NuclearCenter;
import totah.lab.aether.provenance.NuclearAttractionReceipt;
import totah.lab.aether.provenance.ScientificStatus;

/** Immutable ordered s/p nuclear-attraction matrix in hartree, with fixed point nuclei. */
public final class NuclearAttractionMatrix {
    public static final String IMPLEMENTATION = "aether-nuclear-attraction-1";
    public static final String PROTOCOL = "s-only;bohr;hartree;point-nuclei;operator=-sum-Z/r;"
            + "normalized-primitives;normalized-contractions;min-norm2=1e-14;"
            + "StrictMath;ordered-sums;no-screening;binary64;log-scale-fallback;"
            + "F0=taylor-to-0.5,positive-series-to-36,asymptotic-from-36;series-rel=1e-16;max-terms=256";
    private final IntegralMatrixData data;
    private final List<NuclearCenter> nuclei;
    private final NuclearAttractionReceipt receipt;

    private NuclearAttractionMatrix(List<ContractedGaussian> functions, List<NuclearCenter> nuclei) {
        this.nuclei = List.copyOf(nuclei);
        if (this.nuclei.isEmpty()) throw new IllegalArgumentException("At least one nuclear center is required");
        String nucleiHash = IntegralMatrixData.nuclearCentersHash(this.nuclei);
        String protocol = IntegralMatrixData.protocol(PROTOCOL, functions);
        String reason = "Nuclear-attraction integrals only; arbitrary inputs are not reference-validated";
        data = new IntegralMatrixData(functions,
                (a, b) -> SNuclearAttraction.between(a, b, this.nuclei), IMPLEMENTATION, protocol,
                "aether-V-hartree-v1", reason, "\naether-point-nuclei-v1\n" + nucleiHash);
        var identity = data.identity();
        receipt = new NuclearAttractionReceipt(IMPLEMENTATION, protocol, identity.basisGeometryHash(),
                nucleiHash, identity.calculationHash(), identity.resultHash(), ScientificStatus.SCREENING_ONLY,
                reason, identity.receiptHash());
    }

    public static NuclearAttractionMatrix compute(List<ContractedGaussian> functions, List<NuclearCenter> nuclei) {
        return new NuclearAttractionMatrix(functions, nuclei);
    }

    public int size() { return data.size(); }
    public double get(int row, int column) { return data.get(row, column); }
    public List<ContractedGaussian> functions() { return data.functions(); }
    public List<NuclearCenter> nuclei() { return nuclei; }
    public NuclearAttractionReceipt receipt() { return receipt; }
}
