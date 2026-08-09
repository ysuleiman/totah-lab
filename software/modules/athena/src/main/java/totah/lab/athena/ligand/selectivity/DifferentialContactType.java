package totah.lab.athena.ligand.selectivity;

/**
 * Differential contact classification of one aligned position between
 * two receptors. Correspondence always comes from the sequence
 * alignment, never from raw residue numbers.
 */
public enum DifferentialContactType {

    /** Identical residue, ligand contact on both sides. */
    CONSERVED_CONTACT,

    /** Ligand contact only on the A side. */
    A_ONLY_CONTACT,

    /** Ligand contact only on the B side. */
    B_ONLY_CONTACT,

    /** Ligand contact on both sides, but the residues differ. */
    CONTACT_BOTH_DIFFERENT_RESIDUE,

    /** No ligand contact on either side, but the residues differ. */
    NONCONTACT_DIFFERENCE,

    /** A ligand-contact residue that the sequence alignment did not
     *  map onto the other receptor (gap); only one side is present. */
    UNMAPPED
}
