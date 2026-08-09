package totah.lab.athena.ligand.selectivity;

/**
 * Coarse class of a residue substitution at one aligned position,
 * derived from the raw {@link SubstitutionChemistry} features.
 */
public enum SubstitutionClass {

    /** Same residue on both sides. */
    IDENTICAL,

    /** Different residues sharing their physicochemical profile
     *  (no charge/aromatic gain-loss, no polar-hydrophobic swap, no
     *  proline/glycine special case). */
    CONSERVATIVE,

    /** A chemistry change that is neither conservative nor a
     *  charge/aromatic gain-loss (for example a polar-hydrophobic swap
     *  or a proline/glycine special case). */
    MODERATE,

    /** Charge gain/loss or aromatic gain/loss. */
    RADICAL
}
