package totah.lab.prometheus.candidate;

/**
 * Kind of a derived force-field parameter.
 *
 * <p>Kinds beyond harmonic bonded terms and fixed charges (off-center charges,
 * polarizability) exist so that failed model classes — e.g. an off-center-charge
 * model that did not survive validation — remain representable as failed
 * candidates instead of being silently dropped.
 */
public enum ParameterKind {
    BOND_STRETCH,
    ANGLE_BEND,
    TORSION,
    IMPROPER,
    PARTIAL_CHARGE,
    LJ_SIGMA,
    LJ_EPSILON,
    OFF_CENTER_CHARGE,
    POLARIZABILITY
}
