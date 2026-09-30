package totah.lab.aether.matrix;

import java.util.List;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.provenance.ScientificStatus;

/** Immutable M14 operator evidence; no backend array escapes the scientific API. */
public final class ExecutionMatrix {
    public enum Kind { COULOMB, EXCHANGE, FOCK, XC_POTENTIAL }
    private final Kind kind;
    private final IntegralMatrixData data;
    private final String systemHash, densityHash;
    ExecutionMatrix(Kind kind, List<ContractedGaussian> basis, String systemHash, String densityHash,
                    String protocol, String sources, IntegralMatrixData.Entry entries) {
        this.kind=java.util.Objects.requireNonNull(kind);this.systemHash=systemHash;this.densityHash=densityHash;
        data=IntegralMatrixData.compose(basis,entries,"aether-exact-execution-matrix-1",protocol,
                "aether-M14-"+kind+"-hartree-v1","SCREENING_ONLY",systemHash+"\n"+densityHash+"\n"+sources);
    }
    public Kind kind(){return kind;}
    public int size(){return data.size();}
    public double get(int row,int column){return data.get(row,column);}
    public String receiptHash(){return data.identity().receiptHash();}
    public String systemHash(){return systemHash;}
    public String basisGeometryHash(){return data.identity().basisGeometryHash();}
    public String densityHash(){return densityHash;}
    public ScientificStatus status(){return ScientificStatus.SCREENING_ONLY;}
}
