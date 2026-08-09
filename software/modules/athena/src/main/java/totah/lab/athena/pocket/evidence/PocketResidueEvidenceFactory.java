package totah.lab.athena.pocket.evidence;

import totah.lab.athena.pocket.compare.residue.MatchType;
import totah.lab.athena.pocket.compare.residue.ResidueCorrespondence;
import totah.lab.athena.pocket.compare.residue.ResidueChemistryScorer;
import totah.lab.athena.pocket.compare.residue.ResidueMatch;
import totah.lab.athena.pocket.compare.residue.ResidueReference;
import totah.lab.athena.pocket.compare.residue.ResidueSubstitutionScorer;
import totah.lab.athena.sequence.AlignedResiduePair;
import totah.lab.athena.sequence.SequenceAlignment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Builds {@link PocketResidueEvidence} from the residue
 * correspondence of the SELECTED alignment, the (possibly absent)
 * protein sequence alignment, a BLOSUM62 substitution scorer and the
 * configured key-residue set.
 *
 * <p>Aggregation: {@code substitutionSimilarity} and
 * {@code chemistrySimilarity} are the means of the per-pair
 * substitution and chemistry scores over the matched pairs;
 * {@code identityFraction}, {@code compatibleMatchedFraction} and
 * {@code replacementFraction} are the respective match-type counts
 * over the matched pairs; the coverages are the matched count over
 * the respective pocket residue count. The per-pair chemistry score
 * uses the weights of {@code ResidueChemistryScorer} (identical 1.00,
 * conservative 0.70, chemistry-compatible 0.80, spatial replacement
 * 0.00), so {@code chemistrySimilarity} equals the scorer's
 * chemistry similarity of the same correspondence.</p>
 *
 * <p>Key residues and contact residues are named by query residue
 * name and number, uppercased (for example {@code "LEU145"}) — the
 * same convention as {@code ResidueChemistryScorer}.</p>
 */
public final class PocketResidueEvidenceFactory {

    private final ResidueSubstitutionScorer substitutionScorer;

    public PocketResidueEvidenceFactory(
            ResidueSubstitutionScorer substitutionScorer
    ) {
        this.substitutionScorer = Objects.requireNonNull(
                substitutionScorer,
                "substitutionScorer"
        );
    }

    /**
     * Residue evidence without ligand-contact annotation: the
     * {@code querySamContact} and {@code candidateSamContact} flags
     * of every pair are {@code false}.
     *
     * @param sequenceAlignment the protein sequence alignment, or
     *                          {@code null} when no sequence evidence
     *                          exists
     */
    public PocketResidueEvidence create(
            ResidueCorrespondence correspondence,
            SequenceAlignment sequenceAlignment,
            Set<String> keyResidues
    ) {
        return create(
                correspondence,
                sequenceAlignment,
                keyResidues,
                Set.of(),
                Set.of()
        );
    }

    /**
     * Residue evidence with key-residue and ligand-contact flags.
     */
    public PocketResidueEvidence create(
            ResidueCorrespondence correspondence,
            SequenceAlignment sequenceAlignment,
            Set<String> keyResidues,
            Set<String> queryContactResidues,
            Set<String> candidateContactResidues
    ) {
        Objects.requireNonNull(correspondence, "correspondence");

        Set<String> normalizedKeys = normalizeLabels(keyResidues);
        Set<String> normalizedQueryContacts =
                normalizeLabels(queryContactResidues);
        Set<String> normalizedCandidateContacts =
                normalizeLabels(candidateContactResidues);
        Set<Long> alignedPairs = alignedPairKeys(sequenceAlignment);

        List<ResidueCorrespondenceEvidence> correspondences =
                new ArrayList<>();

        int identicalCount = 0;
        int conservativeCount = 0;
        int chemistryCompatibleCount = 0;
        int incompatibleCount = 0;
        int sequenceConsistentCount = 0;
        double substitutionSum = 0.0;
        double chemistrySum = 0.0;

        for (ResidueMatch match : correspondence.matches()) {
            ResidueCorrespondenceEvidence pair = mapMatch(
                    match,
                    sequenceAlignment,
                    normalizedKeys,
                    normalizedQueryContacts,
                    normalizedCandidateContacts
            );

            correspondences.add(pair);
            substitutionSum += pair.substitutionScore();
            chemistrySum += pair.chemistryScore();

            switch (match.matchType()) {
                case IDENTICAL -> identicalCount++;
                case CONSERVATIVE -> conservativeCount++;
                case CHEMISTRY_COMPATIBLE -> chemistryCompatibleCount++;
                case DIFFERENT -> incompatibleCount++;
                default -> {
                    // UNMATCHED never appears in a match list.
                }
            }

            if (pair.sequenceAlignedPair()) {
                sequenceConsistentCount++;
            }
        }

        int matchedCount = correspondence.matches().size();
        int queryResidueCount =
                matchedCount + correspondence.unmatchedQuery().size();
        int candidateResidueCount =
                matchedCount + correspondence.unmatchedCandidate().size();

        return new PocketResidueEvidence(
                queryResidueCount,
                candidateResidueCount,
                matchedCount,
                correspondence.unmatchedQuery().size(),
                correspondence.unmatchedCandidate().size(),
                identicalCount,
                conservativeCount,
                chemistryCompatibleCount,
                incompatibleCount,
                fraction(identicalCount, matchedCount),
                matchedCount == 0
                        ? 0.0
                        : substitutionSum / matchedCount,
                matchedCount == 0 ? 0.0 : chemistrySum / matchedCount,
                fraction(
                        identicalCount + conservativeCount
                                + chemistryCompatibleCount,
                        matchedCount
                ),
                fraction(incompatibleCount, matchedCount),
                fraction(matchedCount, queryResidueCount),
                fraction(matchedCount, candidateResidueCount),
                sequenceConsistentCount,
                fraction(sequenceConsistentCount, matchedCount),
                correspondences
        );
    }

    /**
     * Per-pair evidence of one matched residue pair.
     *
     * @param sequenceAlignment the protein sequence alignment, or
     *                          {@code null} when no sequence evidence
     *                          exists
     */
    public ResidueCorrespondenceEvidence mapMatch(
            ResidueMatch match,
            SequenceAlignment sequenceAlignment,
            Set<String> keyResidues,
            Set<String> queryContactResidues,
            Set<String> candidateContactResidues
    ) {
        Objects.requireNonNull(match, "match");

        Set<String> normalizedKeys = normalizeLabels(keyResidues);
        Set<String> normalizedQueryContacts =
                normalizeLabels(queryContactResidues);
        Set<String> normalizedCandidateContacts =
                normalizeLabels(candidateContactResidues);
        Set<Long> alignedPairs = alignedPairKeys(sequenceAlignment);
        ResidueReference queryReference = match.query().reference();
        ResidueReference candidateReference =
                match.candidate().reference();

        return new ResidueCorrespondenceEvidence(
                queryReference,
                candidateReference,
                queryReference.residueName(),
                candidateReference.residueName(),
                match.distanceAngstroms(),
                alignedPairs.contains(pairKey(
                        queryReference.residueNumber(),
                        candidateReference.residueNumber()
                )),
                match.identicalResidue(),
                match.matchType() == MatchType.CONSERVATIVE,
                match.query().chemistry(),
                match.candidate().chemistry(),
                match.matchType(),
                chemistryWeight(match.matchType()),
                substitutionScorer.similarity(
                        queryReference.residueName(),
                        candidateReference.residueName()
                ),
                normalizedKeys.contains(label(queryReference)),
                normalizedQueryContacts.contains(label(queryReference)),
                normalizedCandidateContacts.contains(
                        label(candidateReference)
                )
        );
    }

    /**
     * The per-pair chemistry weight of a match type: identical 1.00,
     * conservative 0.70, chemistry-compatible 0.80, anything else
     * 0.00 (the weights of {@code ResidueChemistryScorer}).
     */
    public static double chemistryWeight(MatchType matchType) {
        return ResidueChemistryScorer.chemistryWeight(matchType);
    }

    /**
     * The normalized key/contact label of a residue: residue name and
     * number, uppercased (for example {@code "LEU145"}).
     */
    public static String label(ResidueReference reference) {
        return (reference.residueName().trim()
                + reference.residueNumber())
                .toUpperCase(Locale.ROOT);
    }

    private static Set<String> normalizeLabels(Set<String> labels) {
        Objects.requireNonNull(labels, "labels");

        Set<String> normalized = new HashSet<>();

        for (String label : labels) {
            normalized.add(label.trim().toUpperCase(Locale.ROOT));
        }

        return normalized;
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

    private static long pairKey(
            int queryResidueNumber,
            int candidateResidueNumber
    ) {
        return ((long) queryResidueNumber << 32)
                ^ (candidateResidueNumber & 0xffffffffL);
    }

    private static double fraction(int numerator, int denominator) {
        if (denominator == 0) {
            return 0.0;
        }

        return (double) numerator / denominator;
    }
}
