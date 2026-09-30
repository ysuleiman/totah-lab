package totah.lab.aether.integral;

/** Only F0(t) = integral_0^1 exp(-t u^2) du; no higher-order Boys machinery. */
public final class BoysF0 {
    private BoysF0() {}

    public static double value(double t) {
        if (!Double.isFinite(t) || t < 0) {
            throw new IllegalArgumentException("Boys argument must be finite and nonnegative");
        }
        if (t == 0) return 1;
        // erfc(sqrt(36)) < 2.2e-17: omitted relative correction is below binary64 epsilon.
        if (t >= 36) return (StrictMath.sqrt(StrictMath.PI) / 2) / StrictMath.sqrt(t);
        double sum = 1;
        double term = 1;
        double correction = 0;
        for (int k = 1; k <= 256; k++) {
            double contribution;
            if (t <= 0.5) {
                term *= -t / k;
                contribution = term / (2 * k + 1);
            } else {
                // F0(t) = exp(-t) sum_k (2t)^k / (2k+1)!!; no cancellation.
                term *= (2 * t) / (2 * k + 1);
                contribution = term;
            }
            double adjusted = contribution - correction;
            double updated = sum + adjusted;
            correction = (updated - sum) - adjusted;
            sum = updated;
            if (StrictMath.abs(contribution) <= 1e-16 * StrictMath.abs(sum)) {
                return t <= 0.5 ? sum : StrictMath.exp(-t) * sum;
            }
        }
        throw new ArithmeticException("NUMERICAL_FAILURE: Boys F0 series failed to converge");
    }
}
