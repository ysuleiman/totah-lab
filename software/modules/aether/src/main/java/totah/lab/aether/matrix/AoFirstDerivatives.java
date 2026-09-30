package totah.lab.aether.matrix;

import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.gaia.geometry.Point3D;

/** Analytic derivative of the existing normalized Cartesian contraction, including d shells. */
public final class AoFirstDerivatives {
    public static final String PROTOCOL="aether-AO-first-derivative-15-1;d(x^l*exp(-a*r2))=(l*x^(l-1)-2*a*x^(l+1))*exp(-a*r2);existing-normalization;StrictMath";
    private AoFirstDerivatives(){}
    public record Gradient(double x,double y,double z){}
    public static Gradient evaluate(ContractedGaussian f,Point3D point){return evaluate(f,point,AoGrid.prepare(f));}
    static Gradient evaluate(ContractedGaussian f,Point3D point,double[] products) {
        var c=f.terms().getFirst().primitive().centerBohr();var l=f.angularMomentum();
        double x=point.x()-c.x(),y=point.y()-c.y(),z=point.z()-c.z(),r2=x*x+y*y+z*z;
        double px=power(x,l.x()),py=power(y,l.y()),pz=power(z,l.z()),v=0,a=0;
        for(int i=0;i<products.length;i++) {
            double alpha=f.terms().get(i).primitive().exponent(),e=products[i]*StrictMath.exp(-alpha*r2);
            v+=e;a+=(-2*alpha)*e;
        }
        double gx=f.normalization()*py*pz*(lower(x,l.x())*v+px*x*a);
        double gy=f.normalization()*px*pz*(lower(y,l.y())*v+py*y*a);
        double gz=f.normalization()*px*py*(lower(z,l.z())*v+pz*z*a);
        if(!Double.isFinite(gx)||!Double.isFinite(gy)||!Double.isFinite(gz))throw new ArithmeticException("Nonfinite AO derivative");
        return new Gradient(gx,gy,gz);
    }
    private static double power(double x,int l){return StrictMath.pow(x,l);}
    private static double lower(double x,int l){return l==0?0:l*power(x,l-1);}
}
