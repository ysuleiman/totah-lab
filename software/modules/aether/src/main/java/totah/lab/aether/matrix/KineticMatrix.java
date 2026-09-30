package totah.lab.aether.matrix;

import java.util.List;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.integral.SKinetic;
import totah.lab.aether.provenance.KineticReceipt;
import totah.lab.aether.provenance.ScientificStatus;

/** Immutable, ordered s/p-function kinetic integral matrix; every entry is in hartree. */
public final class KineticMatrix {
    public static final String IMPLEMENTATION = "aether-kinetic-1";
    public static final String PROTOCOL = "s-only;bohr;hartree;operator=-0.5-laplacian;"
            + "normalized-primitives;normalized-contractions;min-norm2=1e-14;"
            + "StrictMath;ordered-sum;no-screening;binary64;log-scale-fallback";
    private final IntegralMatrixData data;
    private final KineticReceipt receipt;

    private KineticMatrix(List<ContractedGaussian> functions) {
        String protocol = IntegralMatrixData.protocol(PROTOCOL, functions);
        String reason = "Kinetic integrals only; arbitrary inputs are not reference-validated";
        data = new IntegralMatrixData(functions, SKinetic::between, IMPLEMENTATION, protocol,
                "aether-T-hartree-v1", reason);
        var identity = data.identity();
        receipt = new KineticReceipt(IMPLEMENTATION, protocol, identity.basisGeometryHash(),
                identity.calculationHash(), identity.resultHash(), ScientificStatus.SCREENING_ONLY,
                reason, identity.receiptHash());
    }

    public static KineticMatrix compute(List<ContractedGaussian> functions) {
        return new KineticMatrix(functions);
    }

    public int size() { return data.size(); }
    public double get(int row, int column) { return data.get(row, column); }
    public List<ContractedGaussian> functions() { return data.functions(); }
    public KineticReceipt receipt() { return receipt; }
}
