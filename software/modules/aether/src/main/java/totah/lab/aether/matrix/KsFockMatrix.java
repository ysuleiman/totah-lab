package totah.lab.aether.matrix;

import totah.lab.aether.provenance.ScientificStatus;

/** Pure restricted LDA operator: Hcore + J + Vxc. No HF exchange contribution. */
public final class KsFockMatrix {
    public static final String PROTOCOL="aether-KS-F-v1;F=Hcore+J+Vxc;pure-LDA;total-doubled-density;hartree;no-HF-exchange";
    private final IntegralMatrixData data;
    private KsFockMatrix(IntegralMatrixData data){this.data=data;}
    public static KsFockMatrix assemble(DensityMatrix p,CoreHamiltonianMatrix h,CoulombMatrix j,XcIntegration.Result xc) {
        OccupiedDensityCalculator.requireEqual(p.systemHash(),h.receipt().systemHash(),"KS H/system");
        OccupiedDensityCalculator.requireEqual(p.basisGeometryHash(),h.receipt().basisGeometryHash(),"KS H/basis");
        OccupiedDensityCalculator.requireEqual(p.systemHash(),j.receipt().systemHash(),"KS J/system");
        OccupiedDensityCalculator.requireEqual(p.basisGeometryHash(),j.receipt().basisGeometryHash(),"KS J/basis");
        OccupiedDensityCalculator.requireEqual(p.densityHash(),j.receipt().densityHash(),"KS J/density");
        OccupiedDensityCalculator.requireEqual(p.systemHash(),xc.density().systemHash(),"KS XC/system");
        OccupiedDensityCalculator.requireEqual(p.basisGeometryHash(),xc.density().basisGeometryHash(),"KS XC/basis");
        OccupiedDensityCalculator.requireEqual(p.densityHash(),xc.density().densityHash(),"KS XC/density");
        OccupiedDensityCalculator.requireEqual(IntegralMatrixData.protocol(CoreHamiltonianMatrix.PROTOCOL,p.functions()),h.receipt().protocol(),"KS H/protocol");
        OccupiedDensityCalculator.requireEqual(IntegralMatrixData.protocol(JkCalculator.PROTOCOL,p.functions()),j.receipt().protocol(),"KS J/protocol");
        String sources=p.systemHash()+"\n"+p.densityHash()+"\n"+h.receipt().receiptHash()+"\n"+j.receipt().receiptHash()+"\n"+xc.receiptHash();
        return new KsFockMatrix(IntegralMatrixData.compose(p.functions(),(a,b)->MeanFieldArithmetic.ksFock(h.get(a,b),j.get(a,b),xc.potential().get(a,b)),"aether-KS-F-1",PROTOCOL,"aether-KS-F-hartree-v1","SCREENING_ONLY",sources));
    }
    public int size(){return data.size();}public double get(int i,int j){return data.get(i,j);}
    public String receiptHash(){return data.identity().receiptHash();}public ScientificStatus status(){return ScientificStatus.SCREENING_ONLY;}
}
