package totah.lab.prometheus.completeness;

/** Result of the fail-closed scientific persistence contract. */
public enum ScientificResultCompleteness {
    REPRODUCIBLE_COMPLETE,
    INCOMPLETE_MISSING_MODEL_STATE,
    INCOMPLETE_MISSING_DERIVATIVES,
    INCOMPLETE_MISSING_COMPONENT_DECOMPOSITION,
    INCOMPLETE_MISSING_OPTIMIZER_STATE,
    INCOMPLETE_MISSING_PROVENANCE
}
