package totah.lab.prometheus.candidate;

/**
 * Evidentiary standing of a parameter candidate.
 *
 * <p>Failed candidates are preserved evidence: they are never deleted and never
 * silently promoted. Production status is only ever reached through an explicit
 * accepting {@link ModelDecision}.
 */
public enum EvidenceClass {
    EVIDENCE,
    FAILED_CANDIDATE,
    VALIDATED_DIAGNOSTIC,
    PRODUCTION_MODEL
}
