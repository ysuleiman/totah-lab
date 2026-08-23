package totah.lab.prometheus.neural.ferminet.force;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Adversarial acceptance tests C8-C10 of docs/TSL_RSH_ADVERSARIAL_TEST_SUITE.md
 * for the failed-sample statistics of {@link AcZvzbFermiNetForceEstimator} and
 * {@link AcZvzbDerivFermiNetForceEstimator}.
 *
 * <p>Seam note: the full {@code estimate(FermiNetForceEvaluationContext, ...)}
 * path is not hand-buildable in unit-test scope. {@link
 * FermiNetForceEvaluationContext}'s compact constructor recomputes the
 * parameter checksum from a real {@code FermiNetV1State} (a ~4800-line frozen
 * network state whose parameters cannot be fabricated by hand), and {@code
 * estimate} additionally requires on-disk correlated-FD reference and
 * SWCT/AC_ZV comparison artifacts with matching provenance. The confirmed
 * defect class, however, lives entirely in the sample-statistics machinery
 * ({@code ComponentStatistics.compute}, {@code tails}, and the sample-matrix
 * allocation), which the patched classes expose through the package-private
 * testing seam {@code summarizeSamplesForTesting(double[] values, int[]
 * chains, int walkers)} and {@code invalidSampleMatrix(int, int)}. These tests
 * exercise exactly that machinery through those seams; they do not test
 * private helpers and do not substitute a different oracle. The oracle is the
 * suite's: a failed sample is ABSENT from statistics (never a primitive 0.0),
 * the mean over the remaining samples is exact, tail diagnostics never
 * over-run, and results are invariant to the failed sample's position.
 *
 * <p>The simpler sample-array-based estimators in
 * {@code totah.lab.prometheus.variational.force}
 * ({@code AssarafCaffarelZvzbForceEstimator},
 * {@code AnalyticDifferentialSwctForceEstimator}) were checked as a candidate
 * second mapping: they are streaming weighted accumulators whose only
 * missing-sample channel is a rejection counter — they expose no per-sample
 * array with mean-over-remaining semantics, so the C8/C9/C10 oracles have no
 * second executable mapping there.
 */
class AdversarialSampleStatisticsAcceptanceTest {

    private static final int SAMPLES = 8;
    private static final int WALKERS = 2;
    /** Hand-set constant force; 0.375 = 3/8 is exactly representable. */
    private static final double FORCE = 0.375;

    /**
     * TEST_ID: C8 — a failed sample must not become 0.0. One NaN sample at
     * position k=5, constant force F != 0 for the rest. Expected: returned
     * sample count n-1, mean exactly F (7 * 0.375 = 2.625 and 2.625/7 are
     * exact in binary floating point), variance exactly 0, tails over n-1
     * entries with min=median=max=F and no AIOOBE, and the classification
     * honestly reports the missing sample (IMPLEMENTATION_FAILURE) instead of
     * silently flipping to operational.
     */
    @Test void c8_failedSampleIsAbsentFromStatisticsNeverZero() {
        AcZvzbFermiNetForceEstimator.SampleSummary summary =
                summarize(samplesWithFailureAt(5));
        assertThat(summary.finiteCount()).isEqualTo(SAMPLES - 1);
        assertThat(summary.mean()).isEqualTo(FORCE);
        assertThat(summary.variance()).isEqualTo(0.0);
        assertThat(summary.tails().minimum()).isEqualTo(FORCE);
        assertThat(summary.tails().median()).isEqualTo(FORCE);
        assertThat(summary.tails().maximum()).isEqualTo(FORCE);
        assertThat(summary.tails().beyondFiveSigma()).isZero();
        assertThat(summary.tails().beyondTenSigma()).isZero();
        assertThat(summary.classification()).isEqualTo(
                AcZvzbFermiNetForceEstimator.IMPLEMENTATION_FAILURE);

        AcZvzbFermiNetForceEstimator.SampleSummary clean = summarize(allFinite());
        assertThat(clean.finiteCount()).isEqualTo(SAMPLES);
        assertThat(clean.mean()).isEqualTo(FORCE);
        assertThat(clean.classification()).isEqualTo(
                AcZvzbFermiNetForceEstimator.NUMERICALLY_OPERATIONAL);
    }

    /**
     * TEST_ID: C8 (allocation lock) — the estimator's invalid-sample matrix
     * must be NaN-filled, never primitive 0.0, in both patched estimators,
     * and rows must be independent arrays (no shared-row aliasing).
     */
    @Test void c8_invalidSampleMatrixIsNanFilledNotPrimitiveZero() {
        double[][] acZvzb = AcZvzbFermiNetForceEstimator.invalidSampleMatrix(3, 4);
        double[][] acZvzbDeriv = AcZvzbDerivFermiNetForceEstimator.invalidSampleMatrix(3, 4);
        for (double[][] matrix : new double[][][] {acZvzb, acZvzbDeriv}) {
            assertThat(matrix).hasDimensions(3, 4);
            for (double[] row : matrix) {
                for (double value : row) {
                    assertThat(value).isNaN();
                }
            }
            matrix[0][0] = 0.0;
            assertThat(matrix[1][0]).as("rows must not alias").isNaN();
        }
    }

    /**
     * TEST_ID: C9 — mask and array are a single source of truth. The seam
     * derives the validity mask from the numeric array itself
     * ({@code Double.isFinite} per entry), so the incoherent (array, mask)
     * pairs of the confirmed defect cannot be constructed through it. The
     * executable oracle asserts the four combinations on the single channel:
     * finite value present / counted, NaN absent / excluded, +Infinity absent
     * / excluded, and — the pre-fix defect signature — a literal 0.0 in the
     * array is DATA, counted and shifting the mean to exactly 0.328125
     * (7 * 0.375 / 8): a phantom zero can never masquerade as a missing
     * sample, and a missing sample can never masquerade as zero.
     */
    @Test void c9_singleValidityChannel_arrayIsTheOnlyTruth() {
        double[] withPhantomZero = samplesWithFailureAt(5);
        withPhantomZero[5] = 0.0;
        AcZvzbFermiNetForceEstimator.SampleSummary phantom = summarize(withPhantomZero);
        assertThat(phantom.finiteCount()).isEqualTo(SAMPLES);
        assertThat(phantom.mean()).isEqualTo(0.328125);

        double[] withInfinity = samplesWithFailureAt(5);
        withInfinity[5] = Double.POSITIVE_INFINITY;
        AcZvzbFermiNetForceEstimator.SampleSummary infinite = summarize(withInfinity);
        assertThat(infinite.finiteCount()).isEqualTo(SAMPLES - 1);
        assertThat(infinite.mean()).isEqualTo(FORCE);

        AcZvzbFermiNetForceEstimator.SampleSummary nan = summarize(samplesWithFailureAt(5));
        assertThat(nan.finiteCount()).isEqualTo(SAMPLES - 1);
        assertThat(nan.mean()).isEqualTo(FORCE);
    }

    /**
     * TEST_ID: C10 — sample position carries no information. The C8 fixture
     * failing at sample 0, n/2, and n-1 must produce identical count, mean,
     * variance (bit-compared), identical tails, and no
     * ArrayIndexOutOfBoundsException at the boundaries — first/last are where
     * off-by-one allocation bugs live. With walkers=2 the failed sample
     * unbalances chain counts, so the chain standard error is honestly NaN in
     * every run rather than silently dropping the bad chain.
     */
    @Test void c10_statisticsInvariantToFailedSamplePosition() {
        AcZvzbFermiNetForceEstimator.SampleSummary first = summarize(samplesWithFailureAt(0));
        AcZvzbFermiNetForceEstimator.SampleSummary middle =
                summarize(samplesWithFailureAt(SAMPLES / 2));
        AcZvzbFermiNetForceEstimator.SampleSummary last =
                summarize(samplesWithFailureAt(SAMPLES - 1));
        for (AcZvzbFermiNetForceEstimator.SampleSummary summary :
                new AcZvzbFermiNetForceEstimator.SampleSummary[] {first, middle, last}) {
            assertThat(summary.finiteCount()).isEqualTo(SAMPLES - 1);
            assertThat(Double.doubleToRawLongBits(summary.mean()))
                    .isEqualTo(Double.doubleToRawLongBits(FORCE));
            assertThat(Double.doubleToRawLongBits(summary.variance()))
                    .isEqualTo(Double.doubleToRawLongBits(0.0));
            assertThat(summary.chainStandardError()).isNaN();
            assertThat(summary.tails().minimum()).isEqualTo(FORCE);
            assertThat(summary.tails().median()).isEqualTo(FORCE);
            assertThat(summary.tails().maximum()).isEqualTo(FORCE);
            assertThat(summary.classification()).isEqualTo(
                    AcZvzbFermiNetForceEstimator.IMPLEMENTATION_FAILURE);
        }
        // Sanity that the oracle is not vacuous: a nonconstant survivor set
        // yields a strictly positive variance through the same machinery.
        double[] varied = samplesWithFailureAt(3);
        varied[0] = FORCE + 0.125;
        assertThat(summarize(varied).variance()).isGreaterThan(0.0);
    }

    private static AcZvzbFermiNetForceEstimator.SampleSummary summarize(double[] values) {
        int[] chains = new int[values.length];
        for (int sample = 0; sample < values.length; sample++) chains[sample] = sample % WALKERS;
        return AcZvzbFermiNetForceEstimator.summarizeSamplesForTesting(values, chains, WALKERS);
    }

    private static double[] samplesWithFailureAt(int failedSample) {
        double[] values = allFinite();
        values[failedSample] = Double.NaN;
        return values;
    }

    private static double[] allFinite() {
        double[] values = new double[SAMPLES];
        java.util.Arrays.fill(values, FORCE);
        return values;
    }
}
