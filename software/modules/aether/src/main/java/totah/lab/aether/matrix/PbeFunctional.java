package totah.lab.aether.matrix;

/** Restricted PBE, total density and sigma=grad(rho)^2. See PBE_PROTOCOL.md for equations. */
public final class PbeFunctional {
    public static final double DENSITY_FLOOR=2e-15;
    public static final double CORRELATION_DENSITY_FLOOR=1e-12;
    public static final String PROTOCOL="aether-PBE-15-2;Libxc101+130;restricted-total-density;PW92-mod-A=.0310907;kappa=.804;mu=.2195149727645171;beta=.06672455060314922;gamma=(1-ln2)/pi^2;exchange-rho-floor-inclusive=2e-15;correlation-rho-floor=1e-12;analytic-forward-chain-rule;StrictMath";
    private static final double GAMMA=(1-StrictMath.log(2))/(StrictMath.PI*StrictMath.PI);
    private PbeFunctional(){}
    /** Energy densities (not per-electron energies), and derivatives of their sum. */
    public record Value(double exchangeDensity,double correlationDensity,double vrho,double vsigma) {
        public Value {if(!Double.isFinite(exchangeDensity)||!Double.isFinite(correlationDensity)||!Double.isFinite(vrho)||!Double.isFinite(vsigma))throw new ArithmeticException("Nonfinite PBE result");}
        public double energyDensity(){return exchangeDensity+correlationDensity;}
    }
    public static Value evaluate(double rho,double sigma) {
        if(!Double.isFinite(rho)||!Double.isFinite(sigma)||rho<0||sigma<0)throw new IllegalArgumentException("Finite nonnegative rho/sigma required");
        if(rho<=DENSITY_FLOOR)return new Value(0,0,0,0);
        var n=new D(rho,1,0);var g=new D(sigma,0,1);
        var kf=n.mul(3*StrictMath.PI*StrictMath.PI).pow(1.0/3);
        var reduced=g.div(kf.mul(kf).mul(n).mul(n).mul(4));
        var enhancement=D.of(1.804).sub(D.of(.804).div(reduced.mul(.2195149727645171/.804).add(1)));
        var ex=n.pow(4.0/3).mul(-.75*StrictMath.cbrt(3/StrictMath.PI)).mul(enhancement);
        if(rho<CORRELATION_DENSITY_FLOOR)return new Value(ex.v,0,ex.r,ex.s);
        var rs=D.of(3/(4*StrictMath.PI)).div(n).pow(1.0/3);
        var q=rs.pow(.5).mul(7.5957).add(rs.mul(3.5876)).add(rs.pow(1.5).mul(1.6382)).add(rs.mul(rs).mul(.49294)).mul(2*.0310907);
        var ec=rs.mul(.21370).add(1).mul(-2*.0310907).mul(D.of(1).div(q).log1p());
        var a=D.of(.06672455060314922/GAMMA).div(ec.mul(-1/GAMMA).expm1());
        var t2=g.div(kf.mul(16/StrictMath.PI).mul(n).mul(n));
        var at=a.mul(t2);
        var h=t2.mul(.06672455060314922/GAMMA).mul(at.add(1)).div(at.mul(at).add(at).add(1)).log1p().mul(GAMMA);
        var corr=n.mul(ec.add(h));var total=ex.add(corr);
        if(!Double.isFinite(total.v)||!Double.isFinite(total.r)||!Double.isFinite(total.s))throw new ArithmeticException("Unrepresentable PBE density/gradient");
        return new Value(ex.v,corr.v,total.r,total.s);
    }
    /** Local analytic chain rule in two independent variables; no numerical differencing. */
    private record D(double v,double r,double s) {
        static D of(double v){return new D(v,0,0);}
        D add(double x){return new D(v+x,r,s);} D add(D x){return new D(v+x.v,r+x.r,s+x.s);}
        D sub(D x){return add(x.mul(-1));} D mul(double x){return new D(v*x,r*x,s*x);}
        D mul(D x){return new D(v*x.v,r*x.v+v*x.r,s*x.v+v*x.s);}
        D div(D x){double z=v/x.v;return new D(z,(r-z*x.r)/x.v,(s-z*x.s)/x.v);}
        D pow(double x){double z=StrictMath.pow(v,x),d=x*z/v;return new D(z,d*r,d*s);}
        D log1p(){return new D(StrictMath.log1p(v),r/(1+v),s/(1+v));}
        D expm1(){double z=StrictMath.expm1(v),d=z+1;return new D(z,d*r,d*s);}
    }
}
