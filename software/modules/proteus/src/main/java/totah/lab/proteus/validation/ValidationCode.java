package totah.lab.proteus.validation;

/**
 * Validation codes for the proteus mutation engine. These mirror the
 * {@code MUTATION_*} codes still present in hephaestus'
 * {@code totah.lab.hephaestus.validation.ValidationCode} (uncommitted work);
 * hephaestus keeps its copies until that tree is consolidated.
 */
public enum ValidationCode {
    MUTATION_TARGET_MISSING, MUTATION_WILD_TYPE_MISMATCH,
    MUTATION_BACKBONE_INCOMPLETE, MUTATION_ALT_LOC_PRESENT,
    MUTATION_EXPLICIT_COVALENT_BOND, MUTATION_AMBIGUOUS_COVALENT_TOPOLOGY,
    MUTATION_TEMPLATE_MISSING, MUTATION_GEOMETRY_INVALID
}
