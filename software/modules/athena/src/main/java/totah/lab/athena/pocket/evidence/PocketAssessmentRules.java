package totah.lab.athena.pocket.evidence;

import java.util.Locale;
import java.util.Objects;

/**
 * Config-owned thresholds and rules that turn a preserved
 * {@link PocketComparisonEvidence} bundle into a
 * {@link PocketAssessmentVerdict}: the classification plus a
 * human-readable reason naming the deciding dimensions and their
 * values. The rules never combine the
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

    /**
     * Assesses a preserved evidence bundle. The rules read only the
     * alignment, residue and functional dimensions — never the
     * retrieval provenance or a previously stored verdict.
     */
    public PocketAssessmentVerdict assess(
            PocketComparisonEvidence evidence
    ) {
        Objects.requireNonNull(evidence, "evidence");

        return assess(
                evidence.alignment(),
                evidence.residues(),
                evidence.functional()
        );
    }

    /**
     * Applies these comparison rules and wraps the result in the versioned
     * assessment contract. This does not make the comparison rules applicable
     * to a single-pocket {@link PocketEvidence} aggregate.
     */
    public PocketAssessment<PocketComparisonAssessment> assessVersioned(
            PocketComparisonEvidence evidence,
            String rulesetVersion
    ) {
        PocketAssessmentVerdict result = assess(evidence);
        return new PocketAssessment<>(
                result.verdict(),
                java.util.List.of(new AssessmentReason(
                        result.verdict().name(),
                        "POCKET_COMPARISON",
                        result.reason())),
                rulesetVersion);
    }

    /**
     * Assesses the evidence dimensions directly, without a wrapper
     * bundle — the assembly path uses this overload to obtain the
     * verdict BEFORE the bundle exists. Every returned verdict
     * carries a reason naming the deciding dimensions and their
     * values; the rule order is first match wins.
     */
    public PocketAssessmentVerdict assess(
            PocketAlignmentEvidence alignment,
            PocketResidueEvidence residues,
            PocketFunctionalEvidence functional
    ) {
        Objects.requireNonNull(alignment, "alignment");
        Objects.requireNonNull(residues, "residues");
        Objects.requireNonNull(functional, "functional");

        AlignmentHypothesisEvidence selected =
                alignment.selectedHypothesis();

        if (!selected.available()) {
            return new PocketAssessmentVerdict(
                    PocketComparisonAssessment.INSUFFICIENT_EVIDENCE,
                    "INSUFFICIENT_EVIDENCE: the selected alignment"
                            + " hypothesis is unavailable"
            );
        }

        if (residues.matchedResidueCount() < minimumCorrespondenceCount) {
            return new PocketAssessmentVerdict(
                    PocketComparisonAssessment.INSUFFICIENT_EVIDENCE,
                    String.format(
                            Locale.ROOT,
                            "INSUFFICIENT_EVIDENCE: %d matched residue"
                                    + " pairs, below the minimum of %d",
                            residues.matchedResidueCount(),
                            minimumCorrespondenceCount
                    )
            );
        }

        double geometry = selected.geometrySimilarity();
        double chemistry = residues.chemistrySimilarity();

        boolean geometryAcceptable =
                geometry >= geometryAcceptableSimilarity;
        boolean chemistryPoor = chemistry < poorChemistrySimilarity;
        boolean chemistryHigh = chemistry >= highChemistrySimilarity;

        if (!geometryAcceptable && chemistryPoor) {
            return new PocketAssessmentVerdict(
                    PocketComparisonAssessment.REJECTED,
                    String.format(
                            Locale.ROOT,
                            "REJECTED: geometry %.3f below the"
                                    + " acceptable threshold %.2f and"
                                    + " residue chemistry %.3f below the"
                                    + " poor threshold %.2f",
                            geometry,
                            geometryAcceptableSimilarity,
                            chemistry,
                            poorChemistrySimilarity
                    )
            );
        }

        boolean geometryStrong = geometry >= strongGeometrySimilarity;

        if (geometryStrong && chemistryPoor) {
            return new PocketAssessmentVerdict(
                    PocketComparisonAssessment.CONFLICTING_EVIDENCE,
                    String.format(
                            Locale.ROOT,
                            "CONFLICTING_EVIDENCE: strong geometry"
                                    + " %.3f (>= %.2f) conflicts with"
                                    + " poor residue chemistry %.3f"
                                    + " (< %.2f)",
                            geometry,
                            strongGeometrySimilarity,
                            chemistry,
                            poorChemistrySimilarity
                    )
            );
        }

        if (!geometryAcceptable && chemistryHigh) {
            return new PocketAssessmentVerdict(
                    PocketComparisonAssessment.CONFLICTING_EVIDENCE,
                    String.format(
                            Locale.ROOT,
                            "CONFLICTING_EVIDENCE: unacceptable geometry"
                                    + " %.3f (< %.2f) conflicts with"
                                    + " high residue chemistry %.3f"
                                    + " (>= %.2f)",
                            geometry,
                            geometryAcceptableSimilarity,
                            chemistry,
                            highChemistrySimilarity
                    )
            );
        }

        boolean sequenceEvidenceExists = sequenceEvidenceExists(
                alignment,
                residues
        );
        boolean ligandEvidenceExists = ligandEvidenceExists(functional);

        boolean sequenceHigh = !sequenceEvidenceExists
                || residues.sequenceConsistentFraction()
                        >= highSequenceConsistentFraction;
        boolean sequenceModerate = !sequenceEvidenceExists
                || residues.sequenceConsistentFraction()
                        >= moderateSequenceConsistentFraction;

        double contactConservation = contactConservation(functional);

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
            return new PocketAssessmentVerdict(
                    PocketComparisonAssessment.STRONG_FUNCTIONAL_MATCH,
                    String.format(
                            Locale.ROOT,
                            "STRONG_FUNCTIONAL_MATCH: acceptable"
                                    + " geometry %.3f (>= %.2f), high"
                                    + " residue chemistry %.3f (>= %.2f),"
                                    + " substitution similarity %.3f"
                                    + " (>= %.2f), %s, %s",
                            geometry,
                            geometryAcceptableSimilarity,
                            chemistry,
                            highChemistrySimilarity,
                            residues.substitutionSimilarity(),
                            highSubstitutionSimilarity,
                            sequenceDimension(
                                    sequenceEvidenceExists,
                                    residues.sequenceConsistentFraction(),
                                    highSequenceConsistentFraction
                            ),
                            contactDimension(
                                    ligandEvidenceExists,
                                    contactConservation,
                                    highContactConservation
                            )
                    )
            );
        }

        if (geometryAcceptable
                && !chemistryPoor
                && sequenceModerate
                && contactsModerate) {
            return new PocketAssessmentVerdict(
                    PocketComparisonAssessment.PROBABLE_FUNCTIONAL_MATCH,
                    String.format(
                            Locale.ROOT,
                            "PROBABLE_FUNCTIONAL_MATCH: acceptable"
                                    + " geometry %.3f (>= %.2f), residue"
                                    + " chemistry %.3f not poor"
                                    + " (>= %.2f), %s, %s",
                            geometry,
                            geometryAcceptableSimilarity,
                            chemistry,
                            poorChemistrySimilarity,
                            sequenceDimension(
                                    sequenceEvidenceExists,
                                    residues.sequenceConsistentFraction(),
                                    moderateSequenceConsistentFraction
                            ),
                            contactDimension(
                                    ligandEvidenceExists,
                                    contactConservation,
                                    moderateContactConservation
                            )
                    )
            );
        }

        if (geometryAcceptable) {
            return new PocketAssessmentVerdict(
                    PocketComparisonAssessment.GEOMETRIC_MATCH_ONLY,
                    String.format(
                            Locale.ROOT,
                            "GEOMETRIC_MATCH_ONLY: acceptable geometry"
                                    + " %.3f (>= %.2f) but residue and"
                                    + " functional agreement below the"
                                    + " functional-match bars (chemistry"
                                    + " %.3f, %s, %s)",
                            geometry,
                            geometryAcceptableSimilarity,
                            chemistry,
                            sequenceDimension(
                                    sequenceEvidenceExists,
                                    residues.sequenceConsistentFraction(),
                                    moderateSequenceConsistentFraction
                            ),
                            contactDimension(
                                    ligandEvidenceExists,
                                    contactConservation,
                                    moderateContactConservation
                            )
                    )
            );
        }

        return new PocketAssessmentVerdict(
                PocketComparisonAssessment.CONFLICTING_EVIDENCE,
                String.format(
                        Locale.ROOT,
                        "CONFLICTING_EVIDENCE: unacceptable geometry"
                                + " %.3f (< %.2f) with moderate residue"
                                + " chemistry %.3f",
                        geometry,
                        geometryAcceptableSimilarity,
                        chemistry
                )
        );
    }

    /**
     * The sequence-consistency dimension of a reason: its value and
     * the applied threshold when sequence evidence exists, an
     * explicit absence note otherwise.
     */
    private static String sequenceDimension(
            boolean sequenceEvidenceExists,
            double sequenceConsistentFraction,
            double threshold
    ) {
        if (!sequenceEvidenceExists) {
            return "no sequence evidence";
        }

        return String.format(
                Locale.ROOT,
                "sequence consistency %.3f (>= %.2f)",
                sequenceConsistentFraction,
                threshold
        );
    }

    /**
     * The ligand-contact dimension of a reason: its value and the
     * applied threshold when ligand evidence exists, an explicit
     * absence note otherwise.
     */
    private static String contactDimension(
            boolean ligandEvidenceExists,
            double contactConservation,
            double threshold
    ) {
        if (!ligandEvidenceExists) {
            return "no ligand-contact evidence";
        }

        return String.format(
                Locale.ROOT,
                "contact conservation %.3f (>= %.2f)",
                contactConservation,
                threshold
        );
    }

    /**
     * Sequence evidence counts as existing when a sequence-seeded
     * hypothesis was evaluated or any hypothesis reported
     * sequence-consistent pairs.
     */
    private static boolean sequenceEvidenceExists(
            PocketAlignmentEvidence alignment,
            PocketResidueEvidence residues
    ) {
        return alignment.sequenceSeeded().available()
                || alignment.pcaIcp().sequenceConsistentPairCount() > 0
                || residues.sequenceConsistentPairCount() > 0;
    }

    private static boolean ligandEvidenceExists(
            PocketFunctionalEvidence functional
    ) {
        return functional.ligandContacts()
                .filter(contacts ->
                        contacts.queryContactResidueCount() > 0)
                .isPresent();
    }

    private static double contactConservation(
            PocketFunctionalEvidence functional
    ) {
        return functional.ligandContacts()
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
