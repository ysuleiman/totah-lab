package totah.lab.prometheus.numerics;

import java.util.Arrays;

/** Value, Cartesian gradient, and Hessian diagonal propagated by forward automatic differentiation. */
public final class SecondOrderJet {
    private final double value;
    private final double[] gradient;
    private final double[] diagonal;

    private SecondOrderJet(double value,double[] gradient,double[] diagonal) {
        this.value=value; this.gradient=gradient; this.diagonal=diagonal;
    }
    public static SecondOrderJet constant(double value,int dimensions) {
        return new SecondOrderJet(value,new double[dimensions],new double[dimensions]);
    }
    public static SecondOrderJet variable(double value,int dimensions,int axis) {
        double[] gradient=new double[dimensions]; gradient[axis]=1.0;
        return new SecondOrderJet(value,gradient,new double[dimensions]);
    }
    public double value() { return value; }
    public double gradient(int axis) { return gradient[axis]; }
    public double laplacian() { return Arrays.stream(diagonal).sum(); }
    public double laplacian(int axes) {
        if(axes<0||axes>diagonal.length)throw new IllegalArgumentException("invalid Laplacian axis count");
        double sum=0;for(int i=0;i<axes;i++)sum+=diagonal[i];return sum;
    }
    public SecondOrderJet add(SecondOrderJet other) {
        return combine(other,1.0);
    }
    public SecondOrderJet add(double scalar) { return add(constant(scalar,gradient.length)); }
    public SecondOrderJet subtract(SecondOrderJet other) { return combine(other,-1.0); }
    private SecondOrderJet combine(SecondOrderJet other,double sign) {
        requireSameDimensions(other); double[] g=new double[gradient.length],d=new double[gradient.length];
        for(int i=0;i<g.length;i++) { g[i]=gradient[i]+sign*other.gradient[i]; d[i]=diagonal[i]+sign*other.diagonal[i]; }
        return new SecondOrderJet(value+sign*other.value,g,d);
    }
    public SecondOrderJet multiply(SecondOrderJet other) {
        requireSameDimensions(other); double[] g=new double[gradient.length],d=new double[gradient.length];
        for(int i=0;i<g.length;i++) {
            g[i]=gradient[i]*other.value+value*other.gradient[i];
            d[i]=diagonal[i]*other.value+2.0*gradient[i]*other.gradient[i]+value*other.diagonal[i];
        }
        return new SecondOrderJet(value*other.value,g,d);
    }
    public SecondOrderJet multiply(double scalar) {
        double[] g=gradient.clone(),d=diagonal.clone();
        for(int i=0;i<g.length;i++) { g[i]*=scalar; d[i]*=scalar; }
        return new SecondOrderJet(value*scalar,g,d);
    }
    public SecondOrderJet reciprocal() { return unary(1.0/value,-1.0/(value*value),2.0/(value*value*value)); }
    public SecondOrderJet divide(SecondOrderJet other) { return multiply(other.reciprocal()); }
    public SecondOrderJet exp() { double result=Math.exp(value); return unary(result,result,result); }
    public SecondOrderJet sqrt() {
        double result=Math.sqrt(value); return unary(result,0.5/result,-0.25/(value*result));
    }
    public SecondOrderJet tanh() {
        double result=Math.tanh(value),first=1.0-result*result;
        return unary(result,first,-2.0*result*first);
    }
    private SecondOrderJet unary(double result,double first,double second) {
        double[] g=new double[gradient.length],d=new double[gradient.length];
        for(int i=0;i<g.length;i++) { g[i]=first*gradient[i]; d[i]=second*gradient[i]*gradient[i]+first*diagonal[i]; }
        return new SecondOrderJet(result,g,d);
    }
    private void requireSameDimensions(SecondOrderJet other) {
        if(gradient.length!=other.gradient.length) throw new IllegalArgumentException("jet dimensions disagree");
    }
}
