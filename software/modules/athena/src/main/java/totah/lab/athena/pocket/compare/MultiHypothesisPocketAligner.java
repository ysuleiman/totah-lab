package totah.lab.athena.pocket.compare;

import totah.lab.athena.pocket.compare.residue.PocketResiduePoint;
import totah.lab.athena.pocket.compare.residue.PocketResiduePointTransformer;
import totah.lab.athena.pocket.compare.residue.ResidueChemistryAssessment;
import totah.lab.athena.pocket.compare.residue.ResidueChemistryScorer;
import totah.lab.athena.pocket.compare.residue.ResidueCorrespondence;
import totah.lab.athena.pocket.compare.residue.ResidueCorrespondenceCalculator;
import totah.lab.athena.pocket.compare.residue.ResidueMatch;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.athena.sequence.AlignedResiduePair;
import totah.lab.athena.sequence.SequenceAlignment;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Aligns a candidate pocket onto a query pocket by evaluating multiple
 * alignment hypotheses and selecting the best one.
 *
 * <p>Hypothesis A is always the production PCA+ICP alignment
 * ({@link CompositePocketAligner}). Hypothesis B exists only when the
 * protein sequence alignment provides a usable seed: identity at or
 * above {@link #MINIMUM_SEQUENCE_IDENTITY} and at least
 * {@link #MINIMUM_SEED_PAIRS} aligned residue pairs whose residue
 * numbers have residue points in BOTH pockets. The seed is a rigid
 * Kabsch fit over those pairs, optionally ICP-refined; the refinement
 * is kept only when it strictly lowers the mean bidirectional
 * distance, so ICP can never pull the alignment out of a good seeded
 * frame.</p>
 *
 * <p>Selection is lexicographic:</p>
 *
 * <ol>
 *     <li>A hypothesis whose geometry is unacceptable (symmetric
 *     coverage below {@link #MINIMUM_SYMMETRIC_COVERAGE} or mean
 *     bidirectional distance above
 *     {@link #MAXIMUM_BIDIRECTIONAL_DISTANCE_ANGSTROMS}) is rejected;
 *     sequence evidence never rescues a geometrically rejected
 *     seed.</li>
 *     <li>If the geometric similarities differ by more than
 *     {@link #GEOMETRY_TOLERANCE}, the better geometry wins.</li>
 *     <li>Otherwise the higher sequence-consistent correspondence
 *     fraction wins.</li>
 *     <li>Then the higher chemistry similarity.</li>
 *     <li>Then the lower mean bidirectional distance.</li>
 *     <li>A perfect tie keeps hypothesis A (production behavior).</li>
 * </ol>
 *
 * <p>If every hypothesis is geometrically rejected, hypothesis A is
 * selected to preserve the production fallback. When no usable seed
 * exists the result is exactly hypothesis A, byte-identical to running
 * {@code PocketComparator} with {@code CompositePocketAligner}.</p>
 *
 * <p>All thresholds are calibration-pending constants: they encode the
 * current best guess and are expected to move once calibrated against
 * known binders.</p>
 */
public final class MultiHypothesisPocketAligner {

    /**
     * Minimum sequence identity for a sequence seed to be considered.
     * Calibration-pending.
     */
    public static final double MINIMUM_SEQUENCE_IDENTITY = 0.25;

    /**
     * Minimum number of aligned residue pairs present in both pockets
     * required to seed a Kabsch fit. Calibration-pending.
     */
    public static final int MINIMUM_SEED_PAIRS = 3;

    /**
     * Preferred number of seed pairs; below this the seed is trusted
     * less. Currently informational only. Calibration-pending.
     */
    public static final int PREFERRED_SEED_PAIRS = 6;

    /**
     * Geometric-similarity difference within which two hypotheses are
     * considered geometrically equivalent. Calibration-pending.
     */
    public static final double GEOMETRY_TOLERANCE = 0.03;

    /**
     * Minimum symmetric (geometric-mean) point coverage for a
     * hypothesis to be geometrically acceptable. Calibration-pending.
     */
    public static final double MINIMUM_SYMMETRIC_COVERAGE = 0.30;

    /**
     * Maximum mean bidirectional nearest-neighbor distance (angstroms)
     * for a hypothesis to be geometrically acceptable.
     * Calibration-pending.
     */
    public static final double MAXIMUM_BIDIRECTIONAL_DISTANCE_ANGSTROMS =
            4.0;

    private static final double COLLINEARITY_TOLERANCE = 1.0e-9;

    private final PocketComparator comparator = new PocketComparator(
            new CompositePocketAligner(),
            PocketComparisonOptions.defaults()
    );
    private final RigidPointAligner seedAligner =
            new KabschRigidPointAligner();
    private final PocketAligner refinementAligner = new IcpPocketAligner();
    private final ResidueCorrespondenceCalculator correspondenceCalculator =
            new ResidueCorrespondenceCalculator();
    private final PocketResiduePointTransformer residueTransformer =
            new PocketResiduePointTransformer();
    private final ResidueChemistryScorer chemistryScorer =
            new ResidueChemistryScorer();

    /**
     * Aligns {@code candidate} onto {@code query}. A {@code null} or
     * empty {@code sequenceAlignment} means "no sequence evidence" and
     * yields the PCA+ICP-only result.
     */
    public PocketAlignmentResult align(
            PocketPointCloud query,
            PocketPointCloud candidate,
            List<PocketResiduePoint> queryResidues,
            List<PocketResiduePoint> candidateResidues,
            SequenceAlignment sequenceAlignment
    ) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(queryResidues, "queryResidues");
        Objects.requireNonNull(candidateResidues, "candidateResidues");

        PocketAlignment pcaAlignment = comparator.align(query, candidate);

        SeededAlignmentEvaluation pcaEvaluation = evaluate(
                pcaAlignment,
                AlignmentInitialization.PCA_ICP,
                0,
                queryResidues,
                candidateResidues,
                sequenceAlignment
        );

        List<AlignedResiduePair> seedPairs = usableSeedPairs(
                queryResidues,
                candidateResidues,
                sequenceAlignment
        );

        boolean seedAvailable = sequenceAlignment != null
                && sequenceAlignment.identity() >= MINIMUM_SEQUENCE_IDENTITY
                && seedPairs.size() >= MINIMUM_SEED_PAIRS;

        if (!seedAvailable) {
            return result(
                    pcaEvaluation,
                    List.of(pcaEvaluation),
                    false,
                    false
            );
        }

        List<Point3D> seedCandidatePoints = new ArrayList<>();
        List<Point3D> seedQueryPoints = new ArrayList<>();

        Map<Integer, PocketResiduePoint> queryByNumber =
                indexByResidueNumber(queryResidues);
        Map<Integer, PocketResiduePoint> candidateByNumber =
                indexByResidueNumber(candidateResidues);

        for (AlignedResiduePair pair : seedPairs) {
            seedCandidatePoints.add(
                    candidateByNumber
                            .get(pair.candidateResidueNumber())
                            .position()
            );
            seedQueryPoints.add(
                    queryByNumber
                            .get(pair.queryResidueNumber())
                            .position()
            );
        }

        final RigidTransform seedTransform;

        try {
            requireNonCollinear(seedCandidatePoints);
            requireNonCollinear(seedQueryPoints);
            seedTransform = seedAligner.align(
                    seedCandidatePoints,
                    seedQueryPoints
            );
        } catch (RuntimeException exception) {
            // Degenerate seed: keep the production PCA+ICP hypothesis.
            return result(
                    pcaEvaluation,
                    List.of(pcaEvaluation),
                    true,
                    true
            );
        }

        PocketAlignment seededAlignment = seededHypothesis(
                query,
                candidate,
                seedTransform
        );

        PocketAlignment seedHypothesisAlignment = seededAlignment;
        AlignmentInitialization seedInitialization =
                AlignmentInitialization.SEQUENCE_SEEDED_KABSCH;

        try {
            PocketAlignment refinedAlignment =
                    refinementAligner.align(
                            query,
                            seededAlignment.alignedCandidate()
                    );

            RigidTransform refinedTransform =
                    seedTransform.andThen(refinedAlignment.transform());

            PocketAlignment icpAlignment = new PocketAlignment(
                    query,
                    new PocketPointCloud(
                            refinedTransform.apply(candidate.points()),
                            candidate.basis()
                    ),
                    refinedTransform,
                    refinedAlignment.rmsd(),
                    refinedAlignment.iterations(),
                    refinedAlignment.converged()
            );

            double seededBidirectional = comparator
                    .compareAligned(seededAlignment)
                    .meanBidirectionalDistance();
            double icpBidirectional = comparator
                    .compareAligned(icpAlignment)
                    .meanBidirectionalDistance();

            if (icpBidirectional < seededBidirectional) {
                seedHypothesisAlignment = icpAlignment;
                seedInitialization =
                        AlignmentInitialization.SEQUENCE_SEEDED_KABSCH_ICP;
            }
        } catch (RuntimeException exception) {
            // ICP refinement failed: keep the pure seeded hypothesis.
        }

        SeededAlignmentEvaluation seedEvaluation = evaluate(
                seedHypothesisAlignment,
                seedInitialization,
                seedPairs.size(),
                queryResidues,
                candidateResidues,
                sequenceAlignment
        );

        SeededAlignmentEvaluation selected = select(
                pcaEvaluation,
                seedEvaluation
        );

        return result(
                selected,
                List.of(pcaEvaluation, seedEvaluation),
                true,
                false
        );
    }

    /**
     * Aligned pairs whose query AND candidate residue numbers both have
     * pocket residue points (matched by residue number).
     */
    private static List<AlignedResiduePair> usableSeedPairs(
            List<PocketResiduePoint> queryResidues,
            List<PocketResiduePoint> candidateResidues,
            SequenceAlignment sequenceAlignment
    ) {
        if (sequenceAlignment == null) {
            return List.of();
        }

        Map<Integer, PocketResiduePoint> queryByNumber =
                indexByResidueNumber(queryResidues);
        Map<Integer, PocketResiduePoint> candidateByNumber =
                indexByResidueNumber(candidateResidues);

        List<AlignedResiduePair> usable = new ArrayList<>();

        for (AlignedResiduePair pair : sequenceAlignment.pairs()) {
            if (queryByNumber.containsKey(pair.queryResidueNumber())
                    && candidateByNumber.containsKey(
                            pair.candidateResidueNumber())) {
                usable.add(pair);
            }
        }

        return usable;
    }

    private static Map<Integer, PocketResiduePoint> indexByResidueNumber(
            List<PocketResiduePoint> residues
    ) {
        Map<Integer, PocketResiduePoint> index = new HashMap<>();

        for (PocketResiduePoint residue : residues) {
            index.putIfAbsent(
                    residue.reference().residueNumber(),
                    residue
            );
        }

        return index;
    }

    private PocketAlignment seededHypothesis(
            PocketPointCloud query,
            PocketPointCloud candidate,
            RigidTransform seedTransform
    ) {
        PocketPointCloud alignedCandidate = new PocketPointCloud(
                seedTransform.apply(candidate.points()),
                candidate.basis()
        );

        return new PocketAlignment(
                query,
                alignedCandidate,
                seedTransform,
                nearestNeighborRmsd(
                        query.points(),
                        alignedCandidate.points()
                ),
                0,
                true
        );
    }

    private SeededAlignmentEvaluation evaluate(
            PocketAlignment alignment,
            AlignmentInitialization initialization,
            int seedPairCount,
            List<PocketResiduePoint> queryResidues,
            List<PocketResiduePoint> candidateResidues,
            SequenceAlignment sequenceAlignment
    ) {
        PocketComparison comparison =
                comparator.compareAligned(alignment);

        List<PocketResiduePoint> alignedCandidateResidues =
                residueTransformer.transform(
                        candidateResidues,
                        alignment.transform()
                );

        ResidueCorrespondence correspondence =
                correspondenceCalculator.calculate(
                        queryResidues,
                        alignedCandidateResidues
                );

        Set<Long> alignedPairs = alignedPairKeys(sequenceAlignment);

        int consistentCount = 0;

        for (ResidueMatch match : correspondence.matches()) {
            if (alignedPairs.contains(pairKey(
                    match.query().reference().residueNumber(),
                    match.candidate().reference().residueNumber()
            ))) {
                consistentCount++;
            }
        }

        int matchCount = correspondence.matches().size();

        double consistentFraction = matchCount == 0
                ? 0.0
                : (double) consistentCount / matchCount;

        double symmetricCoverage = Math.sqrt(
                comparison.queryCoverage()
                        * comparison.candidateCoverage()
        );

        boolean geometryAcceptable =
                symmetricCoverage >= MINIMUM_SYMMETRIC_COVERAGE
                        && comparison.meanBidirectionalDistance()
                                <= MAXIMUM_BIDIRECTIONAL_DISTANCE_ANGSTROMS;

        return new SeededAlignmentEvaluation(
                alignment,
                initialization,
                comparison,
                correspondence,
                seedPairCount,
                consistentCount,
                consistentFraction,
                geometryAcceptable
        );
    }

    private SeededAlignmentEvaluation select(
            SeededAlignmentEvaluation pca,
            SeededAlignmentEvaluation seeded
    ) {
        if (pca.geometryAcceptable() && !seeded.geometryAcceptable()) {
            return pca;
        }

        if (seeded.geometryAcceptable() && !pca.geometryAcceptable()) {
            return seeded;
        }

        if (!pca.geometryAcceptable() && !seeded.geometryAcceptable()) {
            // Both rejected: preserve the production fallback.
            return pca;
        }

        double geometryDifference = Math.abs(
                pca.comparison().geometrySimilarity()
                        - seeded.comparison().geometrySimilarity()
        );

        if (geometryDifference > GEOMETRY_TOLERANCE) {
            return pca.comparison().geometrySimilarity()
                    >= seeded.comparison().geometrySimilarity()
                    ? pca
                    : seeded;
        }

        if (pca.sequenceConsistentCorrespondenceFraction()
                != seeded.sequenceConsistentCorrespondenceFraction()) {
            return pca.sequenceConsistentCorrespondenceFraction()
                    > seeded.sequenceConsistentCorrespondenceFraction()
                    ? pca
                    : seeded;
        }

        double pcaChemistry = chemistrySimilarity(pca.correspondence());
        double seededChemistry = chemistrySimilarity(seeded.correspondence());

        if (pcaChemistry != seededChemistry) {
            return pcaChemistry > seededChemistry ? pca : seeded;
        }

        if (pca.comparison().meanBidirectionalDistance()
                != seeded.comparison().meanBidirectionalDistance()) {
            return pca.comparison().meanBidirectionalDistance()
                    < seeded.comparison().meanBidirectionalDistance()
                    ? pca
                    : seeded;
        }

        // Perfect tie: keep hypothesis A (production behavior).
        return pca;
    }

    private double chemistrySimilarity(ResidueCorrespondence correspondence) {
        ResidueChemistryAssessment assessment =
                chemistryScorer.assess(correspondence, Set.of());

        return assessment.chemistrySimilarity();
    }

    private static PocketAlignmentResult result(
            SeededAlignmentEvaluation selected,
            List<SeededAlignmentEvaluation> hypotheses,
            boolean sequenceSeedAvailable,
            boolean sequenceSeedDegenerate
    ) {
        return new PocketAlignmentResult(
                selected.alignment(),
                selected.initialization(),
                selected.seedPairCount(),
                selected.sequenceConsistentCorrespondenceCount(),
                selected.sequenceConsistentCorrespondenceFraction(),
                sequenceSeedAvailable,
                sequenceSeedDegenerate,
                selected.comparison(),
                selected.correspondence(),
                hypotheses
        );
    }

    private static Set<Long> alignedPairKeys(
            SequenceAlignment sequenceAlignment
    ) {
        if (sequenceAlignment == null) {
            return Set.of();
        }

        Set<Long> keys = new HashSet<>();

        for (AlignedResiduePair pair : sequenceAlignment.pairs()) {
            keys.add(pairKey(
                    pair.queryResidueNumber(),
                    pair.candidateResidueNumber()
            ));
        }

        return keys;
    }

    private static long pairKey(int queryResidueNumber, int candidateResidueNumber) {
        return ((long) queryResidueNumber << 32)
                ^ (candidateResidueNumber & 0xffffffffL);
    }

    /**
     * Rejects seed point sets that do not span a plane: with all points
     * on one line the Kabsch rotation around that line is
     * underdetermined, so the seed cannot define a frame.
     */
    private static void requireNonCollinear(List<Point3D> points) {
        Point3D first = points.getFirst();
        Point3D farthest = null;
        double maximumDistanceSquared = 0.0;

        for (Point3D point : points) {
            double distanceSquared = first.distanceSquared(point);

            if (distanceSquared > maximumDistanceSquared) {
                maximumDistanceSquared = distanceSquared;
                farthest = point;
            }
        }

        if (farthest == null
                || maximumDistanceSquared <= COLLINEARITY_TOLERANCE) {
            throw new IllegalArgumentException(
                    "Seed points are geometrically degenerate"
            );
        }

        double axisX = farthest.x() - first.x();
        double axisY = farthest.y() - first.y();
        double axisZ = farthest.z() - first.z();

        for (Point3D point : points) {
            double dx = point.x() - first.x();
            double dy = point.y() - first.y();
            double dz = point.z() - first.z();

            // |p x axis|^2 = |p|^2 |axis|^2 - (p . axis)^2
            double crossX = dy * axisZ - dz * axisY;
            double crossY = dz * axisX - dx * axisZ;
            double crossZ = dx * axisY - dy * axisX;

            double crossNormSquared = crossX * crossX
                    + crossY * crossY
                    + crossZ * crossZ;

            double tolerance = COLLINEARITY_TOLERANCE
                    * maximumDistanceSquared
                    * (1.0 + maximumDistanceSquared);

            if (crossNormSquared > tolerance) {
                return;
            }
        }

        throw new IllegalArgumentException(
                "Seed points are collinear; the seeded frame"
                        + " is underdetermined"
        );
    }

    private static double nearestNeighborRmsd(
            List<Point3D> queryPoints,
            List<Point3D> candidatePoints
    ) {
        double total = 0.0;

        for (Point3D candidatePoint : candidatePoints) {
            double minimumSquared = Double.POSITIVE_INFINITY;

            for (Point3D queryPoint : queryPoints) {
                minimumSquared = Math.min(
                        minimumSquared,
                        candidatePoint.distanceSquared(queryPoint)
                );
            }

            total += minimumSquared;
        }

        return Math.sqrt(total / candidatePoints.size());
    }
}
