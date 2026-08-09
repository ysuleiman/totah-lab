package totah.lab.athena.ligand.selectivity;

/**
 * Classification of a mutant pose relative to two wild-type reference
 * poses (for example WT METTL7A and WT METTL7B), from centroid shifts
 * and contact-set similarities only. Docking confidence never takes
 * part.
 */
public enum PoseSimilarityClassification {

    /** Closer to the A reference on both centroid shift and contact
     *  similarity. */
    MORE_A_LIKE,

    /** Closer to the B reference on both centroid shift and contact
     *  similarity. */
    MORE_B_LIKE,

    /** Mixed evidence: one metric favors each reference, or one metric
     *  ties while the other decides. */
    INTERMEDIATE,

    /** Both centroid shifts exceed the large-pose-change threshold;
     *  the mutant pose matches neither reference. */
    DIFFERENT_FROM_BOTH,

    /** Both metrics are inside their tie bands; the evidence does not
     *  distinguish the references. */
    AMBIGUOUS
}
