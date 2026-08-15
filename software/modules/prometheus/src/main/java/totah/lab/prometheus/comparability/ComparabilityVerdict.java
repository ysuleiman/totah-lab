package totah.lab.prometheus.comparability;

/** Verdict of a pairwise protocol-comparability check. */
public enum ComparabilityVerdict {
    COMPARABLE,
    COMPARABLE_AFTER_REFERENCE_SHIFT,
    SAME_GEOMETRY_DIFFERENT_METHOD,
    INCOMPATIBLE_ENERGY_TARGET,
    INCOMPATIBLE_PROTOCOL,
    INCOMPLETE_METADATA
}
