package totah.lab.aether.matrix;

/** Frozen ordered physical equations shared by reference and M14 execution. */
final class MeanFieldArithmetic {
    private MeanFieldArithmetic() {}
    static double rhfFock(double h,double j,double k){return (h+j)-0.5*k;}
    static double ksFock(double h,double j,double xc){return (h+j)+xc;}
    static double rhfEnergy(DensityMatrix density,IntegralMatrixData.Entry core,IntegralMatrixData.Entry fock) {
        double electronic=0;
        for(int i=0;i<density.size();i++)for(int j=0;j<density.size();j++)
            electronic+=0.5*density.get(i,j)*(core.get(i,j)+fock.get(i,j));
        return electronic;
    }
    static double ksEnergy(DensityMatrix p,IntegralMatrixData.Entry core,IntegralMatrixData.Entry j,double xc) {
        double one=0,hartree=0;
        for(int i=0;i<p.size();i++)for(int k=0;k<p.size();k++){one+=p.get(i,k)*core.get(i,k);hartree+=p.get(i,k)*j.get(i,k);}
        return (one+.5*hartree)+xc;
    }
}
