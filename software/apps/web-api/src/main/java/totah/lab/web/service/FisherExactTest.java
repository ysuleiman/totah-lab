package totah.lab.web.service;

/**
 * One-sided Fisher exact test (enrichment tail) over a 2x2 table:
 * flagged vs not flagged, hit list vs background. Computed as the
 * hypergeometric upper tail in log space.
 */
final class FisherExactTest {

    private FisherExactTest() {
    }

    static double enrichmentPValue(
            int flaggedInHits,
            int totalHits,
            int flaggedInBackground,
            int totalBackground
    ) {
        if (flaggedInHits < 0 || totalHits < 0
                || flaggedInBackground < 0 || totalBackground < 0) {
            throw new IllegalArgumentException(
                    "Counts must not be negative"
            );
        }
        if (flaggedInHits > totalHits
                || flaggedInBackground > totalBackground) {
            throw new IllegalArgumentException(
                    "Flagged counts must not exceed totals"
            );
        }

        int maximum = Math.min(flaggedInBackground, totalHits);

        double pValue = 0.0;

        for (int flagged = flaggedInHits;
             flagged <= maximum;
             flagged++) {

            pValue += Math.exp(
                    logChoose(flaggedInBackground, flagged)
                            + logChoose(
                                    totalBackground - flaggedInBackground,
                                    totalHits - flagged
                            )
                            - logChoose(totalBackground, totalHits)
            );
        }

        return Math.min(1.0, pValue);
    }

    private static double logChoose(int n, int k) {
        if (k < 0 || k > n) {
            return Double.NEGATIVE_INFINITY;
        }

        return logFactorial(n)
                - logFactorial(k)
                - logFactorial(n - k);
    }

    private static double logFactorial(int n) {
        double result = 0.0;

        for (int factor = 2; factor <= n; factor++) {
            result += Math.log(factor);
        }

        return result;
    }
}
