package totah.lab.aether.matrix;

import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;

/** Pointwise total density, XC energy and its symmetric AO derivative on a frozen grid. */
public final class XcIntegration {
    public static final String PROTOCOL="aether-xc-grid-v1;rho=AO^T*P*AO;Exc=sum(w*rho*epsilon);Vxc=sum(w*v*AO*AO);ordered-sums;negative-rho-roundoff<=1e-14*sumAbsTerms-clamped;no-density-screening";
    private XcIntegration(){}
    public static Result evaluate(AoGrid ao,DensityMatrix density,LdaFunctional functional) {
        OccupiedDensityCalculator.requireEqual(IntegralMatrixData.systemHash(ao.grid().system()),density.systemHash(),"grid/density system");
        OccupiedDensityCalculator.requireEqual(ao.basisGeometryHash(),density.basisGeometryHash(),"AO/density basis order");
        int n=density.size();double[] rho=new double[ao.grid().size()],row=new double[n];
        var sum=new XcAccumulator(density,functional);
        for(int g=0;g<rho.length;g++) {
            for(int i=0;i<n;i++)row[i]=ao.get(g,i);
            rho[g]=sum.add(ao.grid().weight(g),row,0);
        }
        String rhoHash=sum.finish();double[][] matrix=sum.matrix;double energy=sum.energy,electrons=sum.electrons;int clamped=sum.clamped;
        String sources=ao.receiptHash()+"\n"+density.densityHash()+"\n"+density.systemHash()+"\n"+rhoHash;
        var data=IntegralMatrixData.compose(ao.functions(),(i,j)->matrix[i][j],"aether-xc-1",PROTOCOL+";"+functional.protocol(),"aether-Vxc-v1","SCREENING_ONLY",sources);
        String hash=ContentHash.sha256(PROTOCOL+"\n"+functional.protocol()+"\n"+sources+"\n"+ContentHash.number(energy)+"\n"+ContentHash.number(electrons)+"\n"+clamped+"\n"+data.identity().receiptHash());
        return new Result(ao,density,functional,rho,electrons,energy,clamped,new XcPotentialMatrix(data),hash);
    }
    public static final class Result {
        private final AoGrid ao;private final DensityMatrix density;private final LdaFunctional functional;private final double[] rho;
        private final double electrons,energy;private final int clamped;private final XcPotentialMatrix matrix;private final String hash;
        private Result(AoGrid ao,DensityMatrix density,LdaFunctional functional,double[] rho,double electrons,double energy,int clamped,XcPotentialMatrix matrix,String hash){this.ao=ao;this.density=density;this.functional=functional;this.rho=rho;this.electrons=electrons;this.energy=energy;this.clamped=clamped;this.matrix=matrix;this.hash=hash;}
        public double densityAt(int point){return rho[point];}public double integratedElectrons(){return electrons;}public double energyHartree(){return energy;}
        public int roundoffClampedPoints(){return clamped;}public XcPotentialMatrix potential(){return matrix;}public String receiptHash(){return hash;}
        public AoGrid ao(){return ao;}public DensityMatrix density(){return density;}public LdaFunctional functional(){return functional;}
        public ScientificStatus status(){return ScientificStatus.SCREENING_ONLY;}
    }
}
