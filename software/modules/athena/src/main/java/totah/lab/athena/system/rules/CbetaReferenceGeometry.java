package totah.lab.athena.system.rules;

/** Approved V11-only uncorrected reference reconstruction. Never creates a source atom. */
final class CbetaReferenceGeometry {
    static final String PROFILE="ATHENA_JAVA21_STRICT_BINARY64_CB_RECONSTRUCTION/1";
    static final double GUARD=0.000001;
    record V(double x,double y,double z) {
        V add(V b){return new V(x+b.x,y+b.y,z+b.z);}
        V sub(V b){return new V(x-b.x,y-b.y,z-b.z);}
        V mul(double q){return new V(x*q,y*q,z*q);}
        V div(double q){return new V(x/q,y/q,z/q);}
        V cross(V b){return new V(y*b.z-b.y*z,z*b.x-b.z*x,x*b.y-b.x*y);}
        double norm(){double r=0;r+=x*x;r+=y*y;r+=z*z;return StrictMath.sqrt(r);}
        boolean finite(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
    }
    record Result(V first,V second,V ideal,Double deviation,String degeneracy) { }
    private static final class Undefined extends Exception {
        final String reason;Undefined(String reason){this.reason=reason;}
    }
    private CbetaReferenceGeometry() { }
    static double[] parameters(String identity) {
        return switch(identity){case "SER"->new double[]{1.530,110.1,122.8,110.5,-122.6,111.2};case "THR","VAL"->new double[]{1.540,109.1,123.4,111.5,-122.0,111.2};default->throw new IllegalArgumentException("V11 exact residue domain");};
    }
    static String category(double deviation){if(!Double.isFinite(deviation)||deviation<0)throw new IllegalArgumentException("Finite nonnegative deviation");return deviation>=0.25?"OUTLIER":"NON_OUTLIER";}
    static Result calculate(String identity,V n,V ca,V c,V cb) {
        double[] p=parameters(identity);
        if(n==null||ca==null||c==null||cb==null)return unknown("INCOMPLETE_TUPLE");
        if(!n.finite()||!ca.finite()||!c.finite()||!cb.finite())return unknown("NONFINITE");
        try {
            V first=construct(n,c,ca,p[0],p[1],p[2]);
            V second=construct(c,n,ca,p[0],p[3],p[4]);
            return finish(first,second,ca,cb,p[0]);
        }catch(Undefined u){return unknown(u.reason);}
    }
    static Result finish(V first,V second,V ca,V cb,double dist) {
        V mean=first.add(second).div(2);double betadist=ca.sub(mean).norm();
        if(!mean.finite()||!Double.isFinite(betadist))return unknown("NONFINITE");
        if(betadist==0)return unknown("ZERO_MEAN_DISTANCE");
        V ideal=betadist!=dist?ca.add(mean.sub(ca).mul(dist).div(betadist)):mean;
        double deviation=cb.sub(ideal).norm();
        if(!ideal.finite()||!Double.isFinite(deviation))return unknown("NONFINITE");
        return new Result(first,second,ideal,deviation,"NONE");
    }
    static boolean normalizable(double norm){return Double.isFinite(norm)&&norm>GUARD;}
    private static V construct(V res0,V res1,V res2,double dist,double angle,double dihedral)throws Undefined {
        V a=res2.sub(res1),b=res0.sub(res1),c=a.cross(b);double cmag=c.norm();
        if(!Double.isFinite(cmag))throw new Undefined("NONFINITE");
        if(!normalizable(cmag))throw new Undefined("NORMALIZATION_GUARD");
        c=c.mul(dist/cmag);c=c.add(res2);
        V newD=rotate(res1,res2,c,dihedral-90);
        a=newD.sub(res2);b=res1.sub(res2);c=a.cross(b);cmag=c.norm();
        if(!Double.isFinite(cmag))throw new Undefined("NONFINITE");
        if(!normalizable(cmag))throw new Undefined("NORMALIZATION_GUARD");
        c=c.mul(dist/cmag);c=c.add(res2);
        return rotate(res2,c,newD,90-angle);
    }
    private static V rotate(V a,V b,V p,double angle)throws Undefined {
        angle*=Math.PI/180.;double xl=b.x-a.x,yl=b.y-a.y,zl=b.z-a.z;
        double xlsq=xl*xl,ylsq=yl*yl,zlsq=zl*zl,dlsq=xlsq+ylsq+zlsq;
        if(!Double.isFinite(dlsq))throw new Undefined("NONFINITE");
        if(dlsq==0)throw new Undefined("ZERO_ROTATION_AXIS");
        double dl=StrictMath.sqrt(dlsq),ca=StrictMath.cos(angle),dsa=StrictMath.sin(angle)/dl,oca=(1-ca)/dlsq;
        double xlylo=xl*yl*oca,xlzlo=xl*zl*oca,ylzlo=yl*zl*oca,xma=p.x-a.x,yma=p.y-a.y,zma=p.z-a.z;
        double m1=xlsq*oca+ca,m2=xlylo-zl*dsa,m3=xlzlo+yl*dsa,m4=xlylo+zl*dsa,m5=ylsq*oca+ca,m6=ylzlo-xl*dsa,m7=xlzlo-yl*dsa,m8=ylzlo+xl*dsa,m9=zlsq*oca+ca;
        V result=new V(xma*m1+yma*m2+zma*m3+a.x,xma*m4+yma*m5+zma*m6+a.y,xma*m7+yma*m8+zma*m9+a.z);
        if(!result.finite())throw new Undefined("NONFINITE");return result;
    }
    private static Result unknown(String why){return new Result(null,null,null,null,why);}
}
