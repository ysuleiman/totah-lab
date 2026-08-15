package totah.lab.prometheus.planning;

/** Scientific disposition of one strategy evidence requirement. */
public enum EvidenceReuseDecision {
    REUSE_EXISTING,
    DERIVE_FROM_EXISTING,
    GENERATE_NEW,
    RESERVE_AS_HOLDOUT,
    INCOMPATIBLE_EXISTING,
    BLOCKED_BY_INFRASTRUCTURE,
    INSUFFICIENT_METADATA
}
