package totah.lab.athena.pocket.compare.residue;

/**
 * Classification of a matched pair of pocket residues.
 *
 * <p>{@link #CONSERVATIVE} denotes a substitution within a
 * conservative amino-acid set (for example leucine for isoleucine),
 * while {@link #CHEMISTRY_COMPATIBLE} only requires the broad
 * {@link ResidueChemistry} class to agree.</p>
 */
public enum MatchType {
    IDENTICAL,
    CONSERVATIVE,
    CHEMISTRY_COMPATIBLE,
    DIFFERENT,
    UNMATCHED
}
