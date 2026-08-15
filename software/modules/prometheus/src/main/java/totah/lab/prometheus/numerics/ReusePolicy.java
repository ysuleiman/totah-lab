package totah.lab.prometheus.numerics;

/** Retention policy for one numerical intermediate within a state-scoped evaluation. */
public enum ReusePolicy {
    MANDATORY_REUSE,
    CACHE_IF_BENEFICIAL,
    RECOMPUTE_IF_CHEAPER
}
