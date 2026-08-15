package totah.lab.prometheus.recovery;

/** Evidence-backed disposition of a previously missing metadata or result field. */
public enum RecoveryClassification {
    RECOVERABLE_FROM_RAW_ARTIFACT,
    RECOVERABLE_FROM_SOFTWARE_ENVIRONMENT_ARTIFACT,
    DERIVABLE,
    GENUINELY_UNRECOVERABLE
}
