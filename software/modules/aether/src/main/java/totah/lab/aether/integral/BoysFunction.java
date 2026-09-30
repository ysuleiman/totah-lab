package totah.lab.aether.integral;

/** Boys orders 0..8, sufficient for s/p/d quartets. F0 retains its frozen implementation. */
public final class BoysFunction {
    private BoysFunction() {}
    public static double value(int order, double t) {
        if (order < 0 || order > 8 || !Double.isFinite(t) || t < 0) throw new IllegalArgumentException("Boys domain: n=0..8, finite T>=0");
        if (order == 0) return BoysF0.value(t);
        if (t >= 16) {
            double value = BoysF0.value(t), exponential = StrictMath.exp(-t);
            for (int n=0; n<order; n++) value = ((2*n+1)*value-exponential)/(2*t);
            return GaussianContraction.requireFinite(value);
        }
        // Kummer positive-term series: no cancellation or division by T near zero.
        double term = 1.0/(2*order+1), sum = term;
        for (int k=1; k<=256; k++) {
            term *= t/(order+k+0.5);
            sum += term;
            if (term <= sum*1e-16) return GaussianContraction.requireFinite(StrictMath.exp(-t)*sum);
        }
        throw new ArithmeticException("NUMERICAL_FAILURE: Boys series did not converge");
    }
}
