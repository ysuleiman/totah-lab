package totah.lab.prometheus.store;

/** Durable terminal state of one attempted generated calculation. */
public enum GeneratedEvidenceStatus {
    ACCEPTED,
    REJECTED,
    FAILED
}
