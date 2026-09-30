package totah.lab.aether.matrix;

import totah.lab.aether.provenance.ScientificStatus;

/** Typed XC derivative matrix; creation is restricted to checked numerical integration. */
public final class XcPotentialMatrix {
    private final IntegralMatrixData data;
    XcPotentialMatrix(IntegralMatrixData data){this.data=data;}
    public int size(){return data.functions().size();}public double get(int i,int j){return data.get(i,j);}
    public String receiptHash(){return data.identity().receiptHash();}public ScientificStatus status(){return ScientificStatus.SCREENING_ONLY;}
}
