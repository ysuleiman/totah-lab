package totah.lab.gaia.graph;

/** Source of the topology assertion represented by a sequence edge. */
public enum SequenceEdgeProvenance {
    EXPLICIT_BOND,
    PARTIAL_CONNECTIVITY_BOND,
    INFERRED_CONNECTIVITY_BOND,
    UNVERIFIED_BOND,
    CHAIN_ORDER_INFERRED
}
