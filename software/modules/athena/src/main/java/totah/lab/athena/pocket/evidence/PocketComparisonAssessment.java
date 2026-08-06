package totah.lab.athena.pocket.evidence;

/**
 * Method-neutral verdict of a pocket comparison, derived from the
 * preserved evidence by {@link PocketAssessmentRules}.
 *
 * <ul>
 *     <li>{@link #STRONG_FUNCTIONAL_MATCH}: acceptable geometry, high
 *     residue substitution/chemistry agreement, high sequence
 *     consistency (when sequence evidence exists) and high
 *     functional-contact conservation (when ligand evidence
 *     exists).</li>
 *     <li>{@link #PROBABLE_FUNCTIONAL_MATCH}: broadly consistent
 *     evidence with at least one dimension only moderate or
 *     incomplete (for example missing functional annotation).</li>
 *     <li>{@link #GEOMETRIC_MATCH_ONLY}: acceptable geometry but poor
 *     residue and functional agreement.</li>
 *     <li>{@link #CONFLICTING_EVIDENCE}: material disagreement between
 *     evidence dimensions (for example strong geometry with weak
 *     chemistry, or weak geometry with strong residue
 *     evidence).</li>
 *     <li>{@link #INSUFFICIENT_EVIDENCE}: the selected alignment is
 *     unavailable or too few residue correspondences exist to judge
 *     the pair.</li>
 *     <li>{@link #REJECTED}: poor geometry AND poor residue
 *     evidence.</li>
 * </ul>
 */
public enum PocketComparisonAssessment {
    STRONG_FUNCTIONAL_MATCH,
    PROBABLE_FUNCTIONAL_MATCH,
    GEOMETRIC_MATCH_ONLY,
    CONFLICTING_EVIDENCE,
    INSUFFICIENT_EVIDENCE,
    REJECTED
}
