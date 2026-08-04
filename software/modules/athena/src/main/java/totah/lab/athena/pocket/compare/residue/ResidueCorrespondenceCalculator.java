package totah.lab.athena.pocket.compare.residue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Establishes a deterministic, greedy one-to-one correspondence
 * between the residue points of a query pocket and an aligned
 * candidate pocket.
 *
 * <p>All query/candidate pairs within the maximum distance are
 * enumerated and processed in ascending distance order (ties broken
 * by query reference, then candidate reference). A pair is accepted
 * only while both sides are still unmatched, so each residue matches
 * at most once.</p>
 *
 * <p>Pairs are classified in the following order, which makes
 * {@link MatchType#CONSERVATIVE} reachable for residues that also
 * share a broad chemistry class:</p>
 *
 * <ol>
 *     <li>If either side is cysteine or glycine, only an identical
 *     residue name yields {@link MatchType#IDENTICAL}; any other
 *     pairing is {@link MatchType#DIFFERENT}. Cysteine and glycine
 *     are never treated as conservative or chemistry-compatible with
 *     other residues.</li>
 *     <li>Identical residue name (case-insensitive) yields
 *     {@link MatchType#IDENTICAL}.</li>
 *     <li>Residue names in the same conservative set
 *     ({LEU, ILE, VAL, MET}, {ASP, GLU}, {LYS, ARG}, {SER, THR},
 *     {ASN, GLN}, {PHE, TYR, TRP}) yield
 *     {@link MatchType#CONSERVATIVE}.</li>
 *     <li>Equal {@link ResidueChemistry} classes yield
 *     {@link MatchType#CHEMISTRY_COMPATIBLE}.</li>
 *     <li>Everything else is {@link MatchType#DIFFERENT}.</li>
 * </ol>
 */
public final class ResidueCorrespondenceCalculator {

    /**
     * Default maximum matching distance in angstroms.
     */
    public static final double DEFAULT_MAXIMUM_DISTANCE_ANGSTROMS = 4.0;

    private static final List<Set<String>> CONSERVATIVE_SETS = List.of(
            Set.of("LEU", "ILE", "VAL", "MET"),
            Set.of("ASP", "GLU"),
            Set.of("LYS", "ARG"),
            Set.of("SER", "THR"),
            Set.of("ASN", "GLN"),
            Set.of("PHE", "TYR", "TRP")
    );

    private static final Comparator<ResidueReference> REFERENCE_ORDER =
            Comparator.comparing(ResidueReference::chainId)
                    .thenComparingInt(ResidueReference::residueNumber)
                    .thenComparing(
                            ResidueReference::insertionCode
                    )
                    .thenComparing(ResidueReference::residueName);

    private static final Comparator<CandidatePair> PAIR_ORDER =
            Comparator.comparingDouble(CandidatePair::distance)
                    .thenComparing(
                            pair -> pair.query().reference(),
                            REFERENCE_ORDER
                    )
                    .thenComparing(
                            pair -> pair.candidate().reference(),
                            REFERENCE_ORDER
                    );

    private final double maximumDistanceAngstroms;

    public ResidueCorrespondenceCalculator() {
        this(DEFAULT_MAXIMUM_DISTANCE_ANGSTROMS);
    }

    public ResidueCorrespondenceCalculator(
            double maximumDistanceAngstroms
    ) {
        if (!Double.isFinite(maximumDistanceAngstroms)
                || maximumDistanceAngstroms <= 0.0) {
            throw new IllegalArgumentException(
                    "maximumDistanceAngstroms must be positive and finite"
            );
        }

        this.maximumDistanceAngstroms = maximumDistanceAngstroms;
    }

    public ResidueCorrespondence calculate(
            List<PocketResiduePoint> query,
            List<PocketResiduePoint> alignedCandidate
    ) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(alignedCandidate, "alignedCandidate");

        List<CandidatePair> pairs = new ArrayList<>();

        for (int queryIndex = 0; queryIndex < query.size(); queryIndex++) {
            PocketResiduePoint queryPoint = query.get(queryIndex);

            for (int candidateIndex = 0;
                 candidateIndex < alignedCandidate.size();
                 candidateIndex++) {
                PocketResiduePoint candidatePoint =
                        alignedCandidate.get(candidateIndex);

                double distance = queryPoint.position()
                        .distance(candidatePoint.position());

                if (distance <= maximumDistanceAngstroms) {
                    pairs.add(new CandidatePair(
                            queryIndex,
                            candidateIndex,
                            queryPoint,
                            candidatePoint,
                            distance
                    ));
                }
            }
        }

        pairs.sort(PAIR_ORDER);

        boolean[] matchedQuery = new boolean[query.size()];
        boolean[] matchedCandidate =
                new boolean[alignedCandidate.size()];

        List<ResidueMatch> matches = new ArrayList<>();

        for (CandidatePair pair : pairs) {
            if (matchedQuery[pair.queryIndex()]
                    || matchedCandidate[pair.candidateIndex()]) {
                continue;
            }

            matchedQuery[pair.queryIndex()] = true;
            matchedCandidate[pair.candidateIndex()] = true;

            matches.add(classify(pair));
        }

        List<PocketResiduePoint> unmatchedQuery = new ArrayList<>();

        for (int index = 0; index < query.size(); index++) {
            if (!matchedQuery[index]) {
                unmatchedQuery.add(query.get(index));
            }
        }

        List<PocketResiduePoint> unmatchedCandidate = new ArrayList<>();

        for (int index = 0;
             index < alignedCandidate.size();
             index++) {
            if (!matchedCandidate[index]) {
                unmatchedCandidate.add(alignedCandidate.get(index));
            }
        }

        return summarize(
                matches,
                unmatchedQuery,
                unmatchedCandidate,
                query.size(),
                alignedCandidate.size()
        );
    }

    private static ResidueMatch classify(CandidatePair pair) {
        String queryName = normalize(
                pair.query().reference().residueName()
        );
        String candidateName = normalize(
                pair.candidate().reference().residueName()
        );

        boolean special =
                isSpecial(pair.query().chemistry())
                        || isSpecial(pair.candidate().chemistry());

        if (special) {
            boolean identical = queryName.equals(candidateName);

            return match(
                    pair,
                    identical
                            ? MatchType.IDENTICAL
                            : MatchType.DIFFERENT,
                    identical,
                    identical
            );
        }

        if (queryName.equals(candidateName)) {
            return match(pair, MatchType.IDENTICAL, true, true);
        }

        if (inSameConservativeSet(queryName, candidateName)) {
            return match(pair, MatchType.CONSERVATIVE, false, true);
        }

        if (pair.query().chemistry() == pair.candidate().chemistry()) {
            return match(
                    pair,
                    MatchType.CHEMISTRY_COMPATIBLE,
                    false,
                    true
            );
        }

        return match(pair, MatchType.DIFFERENT, false, false);
    }

    private static ResidueMatch match(
            CandidatePair pair,
            MatchType matchType,
            boolean identicalResidue,
            boolean chemistryCompatible
    ) {
        return new ResidueMatch(
                pair.query(),
                pair.candidate(),
                pair.distance(),
                matchType,
                identicalResidue,
                chemistryCompatible
        );
    }

    private static ResidueCorrespondence summarize(
            List<ResidueMatch> matches,
            List<PocketResiduePoint> unmatchedQuery,
            List<PocketResiduePoint> unmatchedCandidate,
            int querySize,
            int candidateSize
    ) {
        int identicalCount = 0;
        int compatibleCount = 0;
        double distanceSum = 0.0;
        double maximumDistance = 0.0;

        for (ResidueMatch match : matches) {
            if (match.identicalResidue()) {
                identicalCount++;
            }

            if (match.chemistryCompatible()) {
                compatibleCount++;
            }

            distanceSum += match.distanceAngstroms();
            maximumDistance = Math.max(
                    maximumDistance,
                    match.distanceAngstroms()
            );
        }

        int matchCount = matches.size();

        return new ResidueCorrespondence(
                matches,
                unmatchedQuery,
                unmatchedCandidate,
                fraction(matchCount, querySize),
                fraction(matchCount, candidateSize),
                fraction(identicalCount, matchCount),
                fraction(compatibleCount, matchCount),
                matchCount == 0
                        ? 0.0
                        : distanceSum / matchCount,
                matchCount == 0
                        ? 0.0
                        : maximumDistance
        );
    }

    private static double fraction(int numerator, int denominator) {
        if (denominator == 0) {
            return 0.0;
        }

        return (double) numerator / (double) denominator;
    }

    private static boolean isSpecial(ResidueChemistry chemistry) {
        return chemistry == ResidueChemistry.CYSTEINE
                || chemistry == ResidueChemistry.GLYCINE;
    }

    private static boolean inSameConservativeSet(
            String queryName,
            String candidateName
    ) {
        for (Set<String> conservativeSet : CONSERVATIVE_SETS) {
            if (conservativeSet.contains(queryName)
                    && conservativeSet.contains(candidateName)) {
                return true;
            }
        }

        return false;
    }

    private static String normalize(String residueName) {
        return residueName.trim().toUpperCase(Locale.ROOT);
    }

    private record CandidatePair(
            int queryIndex,
            int candidateIndex,
            PocketResiduePoint query,
            PocketResiduePoint candidate,
            double distance
    ) {
    }
}
