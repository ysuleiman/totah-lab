package totah.lab.aether.matrix;

import java.util.List;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.integral.SOverlap;
import totah.lab.aether.provenance.OverlapReceipt;
import totah.lab.aether.provenance.ScientificStatus;

/** Immutable, ordered, dimensionless overlap matrix. No mutable backend escapes. */
public final class OverlapMatrix {
    public static final String IMPLEMENTATION = "aether-overlap-1";
    public static final String PROTOCOL = "s-only;bohr;normalized-primitives;normalized-contractions;"
            + "min-norm2=1e-14;StrictMath;ordered-sum;no-screening;binary64";
    private final IntegralMatrixData data;
    private final OverlapReceipt receipt;

    private OverlapMatrix(List<ContractedGaussian> functions) {
        String protocol = IntegralMatrixData.protocol(PROTOCOL, functions);
        String reason = "Overlap-only implementation; arbitrary inputs are not reference-validated";
        data = new IntegralMatrixData(functions, SOverlap::between, IMPLEMENTATION, protocol,
                "aether-S-v1", reason);
        var identity = data.identity();
        receipt = new OverlapReceipt(IMPLEMENTATION, protocol, identity.basisGeometryHash(),
                identity.calculationHash(), identity.resultHash(), ScientificStatus.SCREENING_ONLY,
                reason, identity.receiptHash());
    }

    public static OverlapMatrix compute(List<ContractedGaussian> functions) {
        return new OverlapMatrix(functions);
    }

    public int size() { return data.size(); }
    public double get(int row, int column) { return data.get(row, column); }
    public List<ContractedGaussian> functions() { return data.functions(); }
    public OverlapReceipt receipt() { return receipt; }
}
