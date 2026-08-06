package totah.lab.athena.pocket.evidence;

import java.util.Objects;

/**
 * Config-owned thresholds and rules that turn a preserved
 * {@link PocketComparisonEvidence} bundle into a
 * {@link PocketComparisonAssessment}. The rules never combine the
 * evidence dimensions into a score; they compare each preserved
 * dimension against its own threshold.
 *
 * <p>Rule order (first match wins):</p>
 *
 * <ol>
 *     <li>{@code INSUFFICIENT_EVIDENCE}: the selected alignment
 *     hypothesis is unavailable, or fewer than
 *     {@code minimumCorrespondenceCount} residue pairs matched.</li>
 *     <li>{@code REJECTED}: poor geometry (below
 *     {@code geometryAcceptableSimilarity}) AND poor residue
 *     chemistry (below {@code poorChemistrySimilarity}).</li>
 *     <li>{@code CONFLICTING_EVIDENCE}: material disagreement —
 *     strong geometry (at or above {@code strongGeometrySimilarity})
 *     with poor chemistry, or unacceptable geometry with high
 *     chemistry (at or above {@code highChemistrySimilarity}).</li>
 *     <li>{@code STRONG_FUNCTIONAL_MATCH}: acceptable geometry, high
 *     chemistry, high substitution similarity, high sequence
 *     consistency (when sequence evidence exists) and high contact
 *     conservation (when ligand evidence exists).</li>
 *     <li>{@code PROBABLE_FUNCTIONAL_MATCH}: acceptable geometry,
 *     not-poor chemistry, and at least moderate sequence consistency
 *     and contact conservation where that evidence exists —
 *     incomplete annotation is allowed.</li>
 *     <li>{@code GEOMETRIC_MATCH_ONLY}: acceptable geometry but poor
 *     residue agreement.</li>
 *     <li>Otherwise {@code CONFLICTING_EVIDENCE} (unacceptable
 *     geometry with moderate residue evidence).</li>
 * </ol>
 *
 * <p>Contact conservation is the fraction of annotated query contact
 * residues whose correspondence is chemically acceptable (identical,
 * conservative or chemistry-compatible).</p>
 *
 * <p>All defaults are UNCALIBRATED and deliberately conservative:
 * they encode the current best guess (tuned so the METTL7A/METTL7B
 * pocket pair lands on {@code STRONG_FUNCTIONAL_MATCH}) and are
 * expected to move once calibrated against known binders.</p>
 *
 * @param geometryAcceptableSimilarity geometry similarity below
 *        which geometry counts as poor (default {@code 0.25})
 * @param strongGeometrySimilarity geometry similarity at or above
 *        which geometry counts as strong for conflict detection
 *        (default {@code 0.60})
 * @param poorChemistrySimilarity residue chemistry similarity below
 *        which residue evidence counts as poor (default {@code 0.40})
 * @param highChemistrySimilarity residue chemistry similarity at or
 *        above which residue evidence counts as high (default
 *        {@code 0.60})
 * @param highSubstitutionSimilarity mean BLOSUM62 substitution
 *        similarity required for {@code STRONG_FUNCTIONAL_MATCH}
 *        (default {@code 0.60})
 * @param highSequenceConsistentFraction sequence-consistent fraction
 *        required for {@code STRONG_FUNCTIONAL_MATCH} when sequence
 *        evidence exists (default {@code 0.80})
 * @param moderateSequenceConsistentFraction sequence-consistent
 *        fraction required for {@code PROBABLE_FUNCTIONAL_MATCH}
 *        (default {@code 0.50})
 * @param highContactConservation contact conservation required for
 *        {@code STRONG_FUNCTIONAL_MATCH} when ligand evidence exists
 *        (default {@code 0.70})
 * @param moderateContactConservation contact conservation required
 *        for {@code PROBABLE_FUNCTIONAL_MATCH} (default {@code 0.50})
 * @param minimumCorrespondenceCount matched residue pairs below
 *        which the evidence is insufficient (default {@code 3})
 */
public record PocketAssessmentRules(
        double geometryAcceptableSimilarity,
        double strongGeometrySimilarity,
        double poorChemistrySimilarity,
        double highChemistrySimilarity,
        double highSubstitutionSimilarity,
        double highSequenceConsistentFraction,
        double moderateSequenceConsistentFraction,
        double highContactConservation,
        double moderateContactConservation,
        int minimumCorrespondenceCount
) {

    public PocketAssessmentRules {
        requireFraction(
                geometryAcceptableSimilarity,
                "geometryAcceptableSimilarity"
        );
        requireFraction(
                strongGeometrySimilarity,
                "strongGeometrySimilarity"
        );
        requireFraction(
                poorChemistrySimilarity,
                "poorChemistrySimilarity"
        );
        requireFraction(
                highChemistrySimilarity,
                "highChemistrySimilarity"
        );
        requireFraction(
                highSubstitutionSimilarity,
                "highSubstitutionSimilarity"
        );
        requireFraction(
                highSequenceConsistentFraction,
                "highSequenceConsistentFraction"
        );
        requireFraction(
                moderateSequenceConsistentFraction,
                "moderateSequenceConsistentFraction"
        );
        requireFraction(
                highContactConservation,
                "highContactConservation"
        );
        requireFraction(
                moderateContactConservation,
                "moderateContactConservation"
        );

        if (minimumCorrespondenceCount < 1) {
            throw new IllegalArgumentException(
                    "minimumCorrespondenceCount must be positive"
            );
        }
    }

    /**
     * Uncalibrated, conservative defaults (see class javadoc).
     */
    public static PocketAssessmentRules defaults() {
        return new PocketAssessmentRules(
                0.25,
                0.60,
                0.40,
                0.60,
                0.60,
                0.80,
                0.50,
                0.70,
                0.50,
                3
        );
    }

    public PocketComparisonAssessment assess(
            PocketComparisonEvidence evidence
    ) {
        Objects.requireNonNull(evidence, "evidence");

        AlignmentHypothesisEvidence selected =
                evidence.alignment().selectedHypothesis();
        PocketResidueEvidence residues = evidence.residues();

        if (!selected.available()
                || residues.matchedResidueCount()
                        < minimumCorrespondenceCount) {
            return PocketComparisonAssessment.INSUFFICIENT_EVIDENCE;
        }

        double geometry = selected.geometrySimilarity();
        double chemistry = residues.chemistrySimilarity();

        boolean geometryAcceptable =
                geometry >= geometryAcceptableSimilarity;
        boolean chemistryPoor = chemistry < poorChemistrySimilarity;
        boolean chemistryHigh = chemistry >= highChemistrySimilarity;

        if (!geometryAcceptable && chemistryPoor) {
            return PocketComparisonAssessment.REJECTED;
        }

        boolean geometryStrong = geometry >= strongGeometrySimilarity;

        if ((geometryStrong && chemistryPoor)
                || (!geometryAcceptable && chemistryHigh)) {
            return PocketComparisonAssessment.CONFLICTING_EVIDENCE;
        }

        boolean sequenceEvidenceExists = sequenceEvidenceExists(
                evidence
        );
        boolean ligandEvidenceExists = ligandEvidenceExists(evidence);

        boolean sequenceHigh = !sequenceEvidenceExists
                || residues.sequenceConsistentFraction()
                        >= highSequenceConsistentFraction;
        boolean sequenceModerate = !sequenceEvidenceExists
                || residues.sequenceConsistentFraction()
                        >= moderateSequenceConsistentFraction;

        double contactConservation = contactConservation(evidence);

        boolean contactsHigh = !ligandEvidenceExists
                || contactConservation >= highContactConservation;
        boolean contactsModerate = !ligandEvidenceExists
                || contactConservation >= moderateContactConservation;

        if (geometryAcceptable
                && chemistryHigh
                && residues.substitutionSimilarity()
                        >= highSubstitutionSimilarity
                && sequenceHigh
                && contactsHigh) {
            return PocketComparisonAssessment.STRONG_FUNCTIONAL_MATCH;
        }

        if (geometryAcceptable
                && !chemistryPoor
                && sequenceModerate
                && contactsModerate) {
            return PocketComparisonAssessment.PROBABLE_FUNCTIONAL_MATCH;
        }

        if (geometryAcceptable) {
            return PocketComparisonAssessment.GEOMETRIC_MATCH_ONLY;
        }

        return PocketComparisonAssessment.CONFLICTING_EVIDENCE;
    }

    /**
     * Sequence evidence counts as existing when a sequence-seeded
     * hypothesis was evaluated or any hypothesis reported
     * sequence-consistent pairs.
     */
    private static boolean sequenceEvidenceExists(
            PocketComparisonEvidence evidence
    ) {
        return evidence.alignment().sequenceSeeded().available()
                || evidence.alignment().pcaIcp()
                        .sequenceConsistentPairCount() > 0
                || evidence.residues().sequenceConsistentPairCount() > 0;
    }

    private static boolean ligandEvidenceExists(
            PocketComparisonEvidence evidence
    ) {
        return evidence.functional().ligandContacts()
                .filter(contacts ->
                        contacts.queryContactResidueCount() > 0)
                .isPresent();
    }

    private static double contactConservation(
            PocketComparisonEvidence evidence
    ) {
        return evidence.functional().ligandContacts()
                .map(contacts -> {
                    if (contacts.queryContactResidueCount() == 0) {
                        return 0.0;
                    }

                    int conserved = contacts.identicalContactCount()
                            + contacts.conservativeContactCount()
                            + contacts.chemistryCompatibleContactCount();

                    return (double) conserved
                            / contacts.queryContactResidueCount();
                })
                .orElse(0.0);
    }

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    name + " must be within [0, 1]"
            );
        }
    }
}
