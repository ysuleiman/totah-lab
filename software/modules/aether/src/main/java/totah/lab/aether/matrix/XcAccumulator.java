package totah.lab.aether.matrix;

/** One shared ordered pointwise XC kernel for dense and blocked execution. */
final class XcAccumulator {
    final double[][] matrix;
    double energy,electrons;
    int clamped;
    private final DensityMatrix density;
    private final LdaFunctional functional;
    private final NumericalEvidenceHash digest=new NumericalEvidenceHash("aether-rho-grid-v1");
    XcAccumulator(DensityMatrix density,LdaFunctional functional){this.density=density;this.functional=functional;matrix=new double[density.size()][density.size()];}
    record PointValue(double rho,double energyPerElectron,double potential,boolean clamped) {}
    PointValue evaluate(double[] ao,int offset) {
        int n=density.size();double value=0,bound=0;
        for(int i=0;i<n;i++)for(int j=0;j<n;j++){double term=ao[offset+i]*density.get(i,j)*ao[offset+j];value+=term;bound+=StrictMath.abs(term);}
        if(!Double.isFinite(value)||!Double.isFinite(bound)||value< -1e-14*bound)throw new ArithmeticException("Nonfinite or negative grid density");
        boolean clamp=value<0;if(clamp)value=0;
        var xc=functional.evaluate(value);
        return new PointValue(value,xc.energyPerElectron(),xc.potential(),clamp);
    }
    double add(double weight,double[] ao,int offset) {return accept(weight,ao,offset,evaluate(ao,offset));}
    double accept(double weight,double[] ao,int offset,PointValue xc) {
        int n=density.size();double value=xc.rho();if(xc.clamped())clamped++;digest.add(value);
        electrons+=weight*value;energy+=weight*value*xc.energyPerElectron();
        for(int i=0;i<n;i++)for(int j=i;j<n;j++)matrix[i][j]+=weight*xc.potential()*ao[offset+i]*ao[offset+j];
        return value;
    }
    String finish() {
        if(!Double.isFinite(energy)||!Double.isFinite(electrons))throw new ArithmeticException("Nonfinite XC integral");
        return digest.finish();
    }
}
