package totah.lab.prometheus.inventory;

/** Explicitly absent provenance metadata; no gap is inferred from scientific values. */
public enum ProvenanceGapType {
    SOURCE_PATH_MISSING,
    SOURCE_CHECKSUM_MISSING,
    PROTOCOL_METHOD_MISSING,
    PROTOCOL_SOFTWARE_MISSING,
    SOFTWARE_VERSION_MISSING,
    TOPOLOGY_REFERENCE_MISSING,
    DERIVED_EVIDENCE_NOT_IN_INVENTORY
}
