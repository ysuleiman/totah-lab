package totah.lab.aether.matrix;

/** Spin-unpolarized Dirac exchange and optional original PZ81 correlation (Libxc ids 1 and 9). */
public enum LdaFunctional {
    EXCHANGE, EXCHANGE_PZ81;
    public record Value(double energyPerElectron,double potential){}
    public String protocol(){return "Dirac1930;LDA_X-id1;Cx=3/4*(3/pi)^(1/3);epsilon=-Cx*rho^(1/3);v=4/3*epsilon;rho-total;hartree;StrictMath"
            +(this==EXCHANGE?"":";PZ81-id9-original;rs=(3/(4*pi*rho))^(1/3);rs<1:A*ln(rs)+B+C*rs*ln(rs)+D*rs;otherwise:gamma/(1+beta1*sqrt(rs)+beta2*rs);A=.0311;B=-.048;C=.0020;D=-.0116;gamma=-.1423;beta1=1.0529;beta2=.3334;vc=epsilon-rs/3*d(epsilon)/drs");}
    public Value evaluate(double rho) {
        if(!Double.isFinite(rho)||rho<0)throw new IllegalArgumentException("Density must be finite and nonnegative");
        if(rho==0)return new Value(0,0);
        double root=StrictMath.cbrt(rho),cx=.75*StrictMath.cbrt(3/StrictMath.PI);
        double e=-cx*root,v=-(4.0/3)*cx*root;
        if(this==EXCHANGE_PZ81) {
            // PZ81 Table I, unpolarized original fit (not the modified continuous-parameter fit).
            double rs=StrictMath.cbrt(3/(4*StrictMath.PI))/root,ec,derivative;
            if(rs<1) {
                double log=StrictMath.log(rs);ec=.0311*log-.048+.0020*rs*log-.0116*rs;
                derivative=.0311/rs+.0020*(log+1)-.0116;
            } else {
                double sqrt=StrictMath.sqrt(rs),denominator=1+1.0529*sqrt+.3334*rs;
                ec=-.1423/denominator;
                // Ordered division avoids overflow of denominator squared in the low-density tail.
                derivative=(-ec/denominator)*(1.0529/(2*sqrt)+.3334);
            }
            e+=ec;v+=ec-rs/3*derivative;
        }
        if(!Double.isFinite(e)||!Double.isFinite(v))throw new ArithmeticException("Nonfinite LDA value");
        return new Value(e,v);
    }
}
