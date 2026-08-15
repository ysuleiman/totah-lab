package totah.lab.prometheus.diagnosis;

/**
 * Classification of a functional-form diagnosis.
 *
 * <p>Diagnosis precedes fitting: Prometheus must be able to say "another value
 * for the same parameter type cannot solve this" ({@link #HARMONIC_FORM_INSUFFICIENT}
 * and friends) as a first-class outcome, distinct from a simple parameter-value
 * failure that refitting could fix.
 */
public enum FunctionalFormClassification {
    /** The parameter value is wrong; a different value of the same type may fix it. */
    PARAMETER_VALUE_FAILURE,
    /** The parameter value does not transfer from its source system to this molecule. */
    PARAMETER_TRANSFERABILITY_FAILURE,
    /** No harmonic force constant can reproduce the observed behavior. */
    HARMONIC_FORM_INSUFFICIENT,
    /** No torsion Fourier series of the current form can reproduce the observed behavior. */
    TORSION_FORM_INSUFFICIENT,
    /** The nonbonded functional form (e.g. fixed-charge LJ 12-6) cannot reproduce the evidence. */
    NONBONDED_FORM_INSUFFICIENT,
    /** The failure only appears when coordinates move together; no single-term fix exists. */
    COUPLED_COORDINATE_BEHAVIOR,
    /** The available evidence contradicts itself and cannot support any single model. */
    INCOMPATIBLE_EVIDENCE,
    /** There is not enough evidence to diagnose anything. */
    INSUFFICIENT_EVIDENCE,
    /** The current model is acceptable; no refit required. */
    MODEL_ACCEPTABLE,
    /** The model is rejected outright. */
    MODEL_REJECTED
}
