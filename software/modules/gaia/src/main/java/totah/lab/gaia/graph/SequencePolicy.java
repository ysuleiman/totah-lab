package totah.lab.gaia.graph;

/** Policy controlling which sequence-adjacency facts a graph exposes. */
public enum SequencePolicy {
    /** Use only recognized polymer-linkage bonds from the structure. */
    EXPLICIT_BONDS_ONLY,
    /**
     * Use explicit linkages and infer absent links from consecutive chain
     * order. This is opt-in because Gaia does not yet carry chain-level
     * polymer membership or chain-break metadata; callers must know that the
     * supplied chain order represents polymer order.
     */
    EXPLICIT_OR_CHAIN_ORDER,
    /** Do not expose sequence adjacency. */
    NONE
}
