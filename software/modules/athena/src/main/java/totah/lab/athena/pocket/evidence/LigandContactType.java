package totah.lab.athena.pocket.evidence;

/**
 * Contact strength of an annotated ligand-contact residue, reusing
 * the notions of the BioHub pocket evidence: {@link #DIRECT} for a
 * residue within the direct-contact cutoff of the ligand,
 * {@link #SHELL} for a residue within the ligand shell but beyond the
 * direct-contact cutoff.
 */
public enum LigandContactType {
    DIRECT,
    SHELL
}
