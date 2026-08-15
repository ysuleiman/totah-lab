package totah.lab.prometheus.evidence;

/** Whether an evidence record is usable, and if not, why. */
public enum EvidenceAcceptanceState {
    PENDING,
    ACCEPTED,
    FAILED_NUMERICALLY,
    GEOMETRY_INVALID,
    PROTOCOL_INCOMPLETE,
    CHECKSUM_INVALID,
    ATOM_MAP_INVALID,
    EXCLUDED_BY_PROTOCOL
}
