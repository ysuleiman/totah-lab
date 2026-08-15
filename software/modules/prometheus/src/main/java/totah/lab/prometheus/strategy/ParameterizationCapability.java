package totah.lab.prometheus.strategy;

/** Scientific parameter families or workflow roles a strategy may support. */
public enum ParameterizationCapability {
    BASELINE_ASSIGNMENT,
    ATOMIC_CHARGES,
    BONDS,
    ANGLES,
    PROPER_TORSIONS,
    IMPROPERS,
    VAN_DER_WAALS,
    FORCE_MATCHING,
    WHOLE_MOLECULE_PARAMETERIZATION
}
