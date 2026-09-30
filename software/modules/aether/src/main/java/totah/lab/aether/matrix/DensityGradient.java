package totah.lab.aether.matrix;

/** Bounded pointwise density contraction. AO rows are internal to the block implementation. */
public record DensityGradient(double rho,double x,double y,double z,double sigma,boolean roundoffClamped) {
    static DensityGradient evaluate(DensityMatrix p,double[] ao,double[] dx,double[] dy,double[] dz,int offset) {
        double rho=0,x=0,y=0,z=0,bound=0;
        for(int i=0;i<p.size();i++) {
            double v=0;for(int j=0;j<p.size();j++){double term=p.get(i,j)*ao[offset+j];v+=term;bound+=StrictMath.abs(ao[offset+i]*term);}
            rho+=ao[offset+i]*v;
            x+=2*dx[offset+i]*v;y+=2*dy[offset+i]*v;z+=2*dz[offset+i]*v;
        }
        double sigma=x*x+y*y+z*z;
        if(!Double.isFinite(rho)||!Double.isFinite(sigma)||!Double.isFinite(bound)||rho< -1e-14*bound)throw new ArithmeticException("Invalid density gradient");
        return new DensityGradient(Math.max(0,rho),x,y,z,sigma,rho<0);
    }
}
