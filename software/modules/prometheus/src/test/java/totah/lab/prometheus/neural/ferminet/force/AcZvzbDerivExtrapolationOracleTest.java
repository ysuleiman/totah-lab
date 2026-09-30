package totah.lab.prometheus.neural.ferminet.force;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * Falsification oracles for the private Pathak-Wagner epsilon^3 extrapolation
 * helpers of {@link AcZvzbDerivFermiNetForceEstimator} (linearFit at lines
 * 472-485, interceptCoefficients at lines 457-470), reached by reflection
 * without changing visibility (precedent: FermiNetForceStatisticsAuditTest).
 *
 * <p>Panel fixture: epsilons {0.9, 0.7, 0.5, 0.3} bohr descending, x_i =
 * epsilon_i^3, y built from asymmetric nonzero a = 1.375, b = -2.625.
 */
final class AcZvzbDerivExtrapolationOracleTest {

    private static final double[] EPSILON = {0.9, 0.7, 0.5, 0.3};
    private static final double[] X = cube(EPSILON);
    private static final double A = 1.375;
    private static final double B = -2.625;
    private static final double TOLERANCE = 1e-12;

    /** ORACLE-PW-01: an exact a + b*x panel must be recovered exactly. */
    @Test
    void exactLinearPanelRecoversInterceptAndSlopeToMachinePrecision() {
        double[] y = linearPanel(X, A, B, 0.0);
        Object fit = linearFit(X, y, 0, X.length);
        assertThat(intercept(fit)).isCloseTo(A, within(TOLERANCE));
        assertThat(slope(fit)).isCloseTo(B, within(TOLERANCE));
    }

    /**
     * ORACLE-PW-02: interceptCoefficients are the mathematical intercept
     * estimator at x = 0: they reproduce a on the exact panel, sum to 1
     * (constant-preserving) and annihilate x (linear-term-free).
     */
    @Test
    void interceptCoefficientsReproduceInterceptSumToOneAndAnnihilateX() {
        double[] y = linearPanel(X, A, B, 0.0);
        double[] c = interceptCoefficients(X, 0, X.length);
        assertThat(c).hasSize(X.length);
        double applied = 0.0, sum = 0.0, moment = 0.0;
        for (int i = 0; i < c.length; i++) {
            applied += c[i] * y[i];
            sum += c[i];
            moment += c[i] * X[i];
        }
        assertThat(applied).isCloseTo(A, within(TOLERANCE));
        assertThat(sum).isCloseTo(1.0, within(TOLERANCE));
        assertThat(moment).isCloseTo(0.0, within(TOLERANCE));
    }

    /**
     * ORACLE-PW-03: on a non-polynomial panel y = a + b*x + d*x^2 the fit
     * intercept must deviate from a in exactly the direction and magnitude of
     * an independent hand-computed 2x2 normal-equation solve (no call into the
     * code under test for the expectation).
     */
    @Test
    void quadraticPanelInterceptMatchesIndependentNormalEquationSolve() {
        double d = 0.5;
        double[] y = linearPanel(X, A, B, d);

        // Independent oracle: ordinary-least-squares intercept from the normal
        // equations, solved by Cramer's rule on sums computed directly here.
        int n = X.length;
        double sx = 0.0, sxx = 0.0, sy = 0.0, sxy = 0.0;
        for (int i = 0; i < n; i++) {
            sx += X[i];
            sxx += X[i] * X[i];
            sy += y[i];
            sxy += X[i] * y[i];
        }
        double expectedIntercept =
                (sxx * sy - sx * sxy) / (n * sxx - sx * sx);

        // The quadratic contamination is resolvable, not numerical noise.
        assertThat(Math.abs(expectedIntercept - A)).isGreaterThan(1e-3);

        Object fit = linearFit(X, y, 0, X.length);
        assertThat(intercept(fit)).isCloseTo(expectedIntercept, within(TOLERANCE));
        assertThat(Math.signum(intercept(fit) - A))
                .isEqualTo(Math.signum(expectedIntercept - A));
    }

    /**
     * ORACLE-PW-04: subrange semantics used by the drop-largest /
     * drop-smallest sensitivity path (extrapolation(), lines 449-450):
     * linearFit(x,y,1,n) must equal a full fit of the panel with the first
     * (largest-epsilon) point removed, and linearFit(x,y,0,n-1) likewise for
     * the last (smallest-epsilon) point.
     */
    @Test
    void subrangeFitEqualsExplicitlyDroppedPointPanels() {
        double[] y = linearPanel(X, A, B, 0.5);

        double[] xDropFirst = {X[1], X[2], X[3]};
        double[] yDropFirst = {y[1], y[2], y[3]};
        Object viaSubrange = linearFit(X, y, 1, X.length);
        Object viaDropped = linearFit(xDropFirst, yDropFirst, 0, xDropFirst.length);
        assertThat(intercept(viaSubrange))
                .isCloseTo(intercept(viaDropped), within(TOLERANCE));
        assertThat(slope(viaSubrange))
                .isCloseTo(slope(viaDropped), within(TOLERANCE));

        double[] xDropLast = {X[0], X[1], X[2]};
        double[] yDropLast = {y[0], y[1], y[2]};
        Object viaSubrangeLast = linearFit(X, y, 0, X.length - 1);
        Object viaDroppedLast = linearFit(xDropLast, yDropLast, 0, xDropLast.length);
        assertThat(intercept(viaSubrangeLast))
                .isCloseTo(intercept(viaDroppedLast), within(TOLERANCE));
        assertThat(slope(viaSubrangeLast))
                .isCloseTo(slope(viaDroppedLast), within(TOLERANCE));
    }

    /** ORACLE-PW-05: linearFit guards n < 2. */
    @Test
    void linearFitRejectsFewerThanTwoPoints() {
        double[] y = linearPanel(X, A, B, 0.0);
        assertThatThrownBy(() -> linearFit(X, y, 0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least two fit points");
        assertThatThrownBy(() -> linearFit(X, y, 2, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least two fit points");
    }

    /**
     * ORACLE-PW-06 (observation probe, potential defect): interceptCoefficients
     * has no guard for a degenerate panel where n*sxx - sx^2 = 0 (all-equal x,
     * i.e. all-equal epsilon). It silently returns NaN coefficients, unlike the
     * adjacent linearFit which guards n < 2. Traced consumption in
     * extrapolation() (AcZvzbDerivFermiNetForceEstimator.java:434-454): NaN
     * coefficients produce all-NaN interceptSamples, ComponentStatistics over
     * zero finite samples is NaN, and the recorded PathakWagnerExtrapolation
     * carries NaN standard error and (via linearFit's own degenerate
     * denominator) NaN/Inf intercept - visible, not silently finite, and the
     * estimator classification does not read Pathak-Wagner diagnostics. Hence
     * an input-validation inconsistency, not a silent corruption channel.
     */
    @Test
    void degenerateConstantXPanelYieldsSilentNaNCoefficientsWithoutGuard() {
        double[] degenerate = {0.5, 0.5, 0.5, 0.5};
        double[] c = interceptCoefficients(degenerate, 0, degenerate.length);
        assertThat(c).hasSize(degenerate.length);
        for (double coefficient : c) {
            assertThat(coefficient)
                    .as("degenerate denominator n*sxx - sx^2 = 0 is not guarded")
                    .isNaN();
        }
    }

    private static double[] cube(double[] epsilon) {
        double[] x = new double[epsilon.length];
        for (int i = 0; i < epsilon.length; i++) {
            x[i] = epsilon[i] * epsilon[i] * epsilon[i];
        }
        return x;
    }

    private static double[] linearPanel(double[] x, double a, double b, double d) {
        double[] y = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            y[i] = a + b * x[i] + d * x[i] * x[i];
        }
        return y;
    }

    private static Object linearFit(double[] x, double[] y, int start, int end) {
        try {
            Method method = AcZvzbDerivFermiNetForceEstimator.class
                    .getDeclaredMethod("linearFit", double[].class,
                            double[].class, int.class, int.class);
            method.setAccessible(true);
            return method.invoke(null, x, y, start, end);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(exception);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static double[] interceptCoefficients(double[] x, int start, int end) {
        try {
            Method method = AcZvzbDerivFermiNetForceEstimator.class
                    .getDeclaredMethod("interceptCoefficients", double[].class,
                            int.class, int.class);
            method.setAccessible(true);
            return (double[]) method.invoke(null, x, start, end);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(exception);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static double intercept(Object fit) {
        return accessor(fit, "intercept");
    }

    private static double slope(Object fit) {
        return accessor(fit, "slope");
    }

    private static double accessor(Object fit, String name) {
        try {
            Method method = fit.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            return (double) method.invoke(fit);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
