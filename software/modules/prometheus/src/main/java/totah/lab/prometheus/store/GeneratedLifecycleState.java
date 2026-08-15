package totah.lab.prometheus.store;

/** Auditable state progression for one generated-evidence attempt. */
public enum GeneratedLifecycleState {
    MISSING,
    AUTHORIZED,
    RUNNING,
    COMPLETED,
    VALIDATED,
    REGISTERED,
    REUSABLE,
    REJECTED,
    FAILED
}
