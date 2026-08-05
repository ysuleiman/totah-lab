package totah.lab.athena.pocket.similar;

import java.util.Objects;

/**
 * The canonical Stage 1 (retrieval) distance between precomputed pocket
 * shape descriptors.
 *
 * <p>Unlike {@link PocketShapeDistance} (the Stage 2 reranking distance),
 * this distance is deliberately restricted to geometry-only, cheaply
 * persistable components so it can be evaluated in SQL over stored
 * descriptor columns: radius of gyration, major extent, normalized
 * elongation/flatness, and the radial histogram.</p>
 *
 * <p>Two complementary forms are defined:</p>
 * <ul>
 *   <li>{@link #scaleAwareDistance} penalizes size differences through
 *   capped log-ratios of radius of gyration and major extent.</li>
 *   <li>{@link #scaleNormalizedDistance} compares only scale-invariant
 *   components (normalized elongation/flatness and the radial histogram).
 *   It lets a compact query subcavity stay retrievable against a larger
 *   merged fpocket pocket whose segmentation swallowed neighbouring
 *   cavities.</li>
 * </ul>
 *
 * <p>{@link #retrievalDistance} takes the minimum of the scale-aware form
 * and the scale-normalized form plus {@link #PARTIAL_MATCH_PENALTY}, so
 * exact-scale matches stay preferred while scale-mismatched partial
 * matches remain reachable.</p>
 *
 * <p>Component definitions (identical in Java and in the Stage 1 SQL):</p>
 * <ul>
 *   <li>{@code logRatio(a, b) = (a <= 0 || b <= 0) ? 1.0
 *       : min(1, abs(ln(a / b)) / ln(4))} — a symmetric size distance
 *   capped at a 4x ratio.</li>
 *   <li>normalized elongation {@code e = major == 0 ? 0 : middle / major}
 *   and normalized flatness {@code f = major == 0 ? 0 : minor / major},
 *   both in [0, 1], computed from each descriptor's extents (NOT the
 *   unbounded {@code major/middle} and {@code middle/minor} ratios the
 *   factory stores on the descriptor itself).</li>
 *   <li>histogram distance {@code = 0.5 * sum |a.bin[i] - b.bin[i]|}
 *   over the factory-normalized (sum 1) radial histograms, in [0, 1].</li>
 * </ul>
 *
 * <pre>
 * scaleAware      = 0.20 * logRatio(rgA, rgB)
 *                 + 0.20 * logRatio(majorA, majorB)
 *                 + 0.15 * |eA - eB|
 *                 + 0.15 * |fA - fB|
 *                 + 0.30 * histogramDistance
 * scaleNormalized = (0.15 * |eA - eB|
 *                 + 0.15 * |fA - fB|
 *                 + 0.30 * histogramDistance) / 0.60
 * retrieval       = min(scaleAware, scaleNormalized + PARTIAL_MATCH_PENALTY)
 * </pre>
 *
 * <p>{@link #DESCRIPTOR_VERSION} stamps persisted descriptor rows; bump it
 * when the descriptor layout or these weights change so stale rows can be
 * detected and recomputed.</p>
 */
public final class PocketRetrievalDistance {

    public static final int DESCRIPTOR_VERSION = 1;

    /**
     * Constant surcharge on the scale-normalized path so that exact-scale
     * matches (which win through the scale-aware form) stay preferred over
     * scale-mismatched partial matches.
     */
    public static final double PARTIAL_MATCH_PENALTY = 0.05;

    private static final double LOG_RATIO_CAP_DENOMINATOR = Math.log(4.0);

    private static final double SCALE_AWARE_RG_WEIGHT = 0.20;
    private static final double SCALE_AWARE_MAJOR_WEIGHT = 0.20;
    private static final double ELONGATION_WEIGHT = 0.15;
    private static final double FLATNESS_WEIGHT = 0.15;
    private static final double HISTOGRAM_WEIGHT = 0.30;

    private static final double SCALE_INVARIANT_WEIGHT_SUM =
            ELONGATION_WEIGHT + FLATNESS_WEIGHT + HISTOGRAM_WEIGHT;

    private PocketRetrievalDistance() {
    }

    /**
     * Size-penalizing form: log-ratio distances on radius of gyration and
     * major extent plus the scale-invariant components.
     */
    public static double scaleAwareDistance(
            PocketShapeDescriptor first,
            PocketShapeDescriptor second
    ) {
        return SCALE_AWARE_RG_WEIGHT * logRatio(
                radiusOfGyration(first),
                radiusOfGyration(second)
        )
                + SCALE_AWARE_MAJOR_WEIGHT * logRatio(
                first.majorExtent(),
                second.majorExtent()
        )
                + scaleInvariantDistance(first, second);
    }

    /**
     * Size-ignoring form: only the scale-invariant components, rescaled to
     * [0, 1] by their combined weight.
     */
    public static double scaleNormalizedDistance(
            PocketShapeDescriptor first,
            PocketShapeDescriptor second
    ) {
        return scaleInvariantDistance(first, second)
                / SCALE_INVARIANT_WEIGHT_SUM;
    }

    /**
     * The Stage 1 retrieval distance: the better of the exact-scale match
     * and the penalized partial (scale-normalized) match.
     */
    public static double retrievalDistance(
            PocketShapeDescriptor first,
            PocketShapeDescriptor second
    ) {
        return Math.min(
                scaleAwareDistance(first, second),
                scaleNormalizedDistance(first, second)
                        + PARTIAL_MATCH_PENALTY
        );
    }

    private static double scaleInvariantDistance(
            PocketShapeDescriptor first,
            PocketShapeDescriptor second
    ) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");

        return ELONGATION_WEIGHT * Math.abs(
                normalizedElongation(first) - normalizedElongation(second)
        )
                + FLATNESS_WEIGHT * Math.abs(
                normalizedFlatness(first) - normalizedFlatness(second)
        )
                + HISTOGRAM_WEIGHT * histogramDistance(
                first.radialHistogram(),
                second.radialHistogram()
        );
    }

    private static double radiusOfGyration(
            PocketShapeDescriptor descriptor
    ) {
        Objects.requireNonNull(descriptor, "descriptor");
        return descriptor.radiusOfGyration();
    }

    /**
     * Symmetric, capped size distance: 0 for equal sizes, 1 once the
     * ratio reaches 4x (in either direction), 1 for non-positive inputs.
     */
    private static double logRatio(double first, double second) {
        if (first <= 0.0 || second <= 0.0) {
            return 1.0;
        }

        return Math.min(
                1.0,
                Math.abs(Math.log(first / second))
                        / LOG_RATIO_CAP_DENOMINATOR
        );
    }

    private static double normalizedElongation(
            PocketShapeDescriptor descriptor
    ) {
        return descriptor.majorExtent() == 0.0
                ? 0.0
                : descriptor.middleExtent() / descriptor.majorExtent();
    }

    private static double normalizedFlatness(
            PocketShapeDescriptor descriptor
    ) {
        return descriptor.majorExtent() == 0.0
                ? 0.0
                : descriptor.minorExtent() / descriptor.majorExtent();
    }

    private static double histogramDistance(
            double[] first,
            double[] second
    ) {
        if (first.length != second.length) {
            throw new IllegalArgumentException(
                    "Radial histograms must have equal lengths: "
                            + first.length + " vs " + second.length
            );
        }

        double sum = 0.0;
        for (int index = 0; index < first.length; index++) {
            sum += Math.abs(first[index] - second[index]);
        }

        return 0.5 * sum;
    }
}
