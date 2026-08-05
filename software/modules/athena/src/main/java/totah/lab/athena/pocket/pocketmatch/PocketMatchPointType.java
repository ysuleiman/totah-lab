package totah.lab.athena.pocket.pocketmatch;

/**
 * Representative point types computed per pocket residue for the
 * PocketMatch representation.
 *
 * <p>Part of Athena's independent, clean-room implementation of the
 * PocketMatch representation described by Yeturu &amp; Chandra (2008);
 * see the package documentation for the full citation and provenance.</p>
 */
public enum PocketMatchPointType {

    /**
     * The alpha carbon. Required: a residue without a usable CA cannot
     * contribute any representative point.
     */
    CA,

    /**
     * The beta carbon. Glycine, and residues whose CB atom is absent,
     * fall back to the alpha carbon position.
     */
    CB,

    /**
     * Centroid of the side-chain heavy atoms. Glycine, and residues
     * without side-chain heavy atoms, fall back to the alpha carbon
     * position.
     */
    SIDE_CHAIN_CENTROID
}
