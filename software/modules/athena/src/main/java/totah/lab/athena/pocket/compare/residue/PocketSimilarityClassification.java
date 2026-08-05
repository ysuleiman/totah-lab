package totah.lab.athena.pocket.compare.residue;

/**
 * Chemistry-gated classification of a pocket similarity candidate.
 *
 * <p>{@link #SHAPE_ONLY_NEIGHBOR} denotes a candidate whose geometry
 * aligns but whose residue chemistry does not pass the chemistry gate;
 * {@link #REJECTED} denotes a candidate that passes the chemistry gate
 * but whose combined final similarity is below the moderate
 * threshold.</p>
 */
public enum PocketSimilarityClassification {
    STRONG_SIMILARITY,
    MODERATE_SIMILARITY,
    SHAPE_ONLY_NEIGHBOR,
    REJECTED
}
