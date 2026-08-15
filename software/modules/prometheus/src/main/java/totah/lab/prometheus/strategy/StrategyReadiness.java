package totah.lab.prometheus.strategy;

/** Fail-closed state of a strategy proposal or plan assessment. */
public enum StrategyReadiness {
    READY_FOR_AUTHORIZED_EXECUTION,
    EVIDENCE_REQUIRED,
    BLOCKED,
    EXTERNAL_METHOD_NOT_INTEGRATED
}
