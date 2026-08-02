package totah.lab.gaia.classification;


public enum PolymerType {

    /**
     * Protein / polypeptide.
     */
    PROTEIN,

    /**
     * DNA polymer.
     */
    DNA,

    /**
     * RNA polymer.
     */
    RNA,

    /**
     * Hybrid nucleic acid (DNA/RNA).
     */
    NUCLEIC_ACID,

    /**
     * Polysaccharide.
     */
    CARBOHYDRATE,

    /**
     * Peptide-like polymer that is not a standard protein.
     */
    PEPTIDE,

    /**
     * Other polymer not covered above.
     */
    OTHER,

    /**
     * Not part of a polymer (ligands, cofactors, ions, waters, etc.).
     */
    NONE,

    /**
     * Polymer membership could not be determined.
     */
    UNKNOWN
}
