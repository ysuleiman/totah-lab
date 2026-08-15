package totah.lab.prometheus.evidence;

/** The kind of calculation that produced an evidence artifact. */
public enum CalculationType {
    OPTIMIZATION,
    SINGLE_POINT,
    HESSIAN,
    ESP,
    RESP,
    INTERACTION_ENERGY,
    COUNTERPOISE_INTERACTION,
    TORSION_SCAN,
    CONSTRAINED_SCAN,
    FORCE_EVALUATION,
    CLASSICAL_FIXED_GEOMETRY_ENERGY,
    ENERGY_DECOMPOSITION
}
