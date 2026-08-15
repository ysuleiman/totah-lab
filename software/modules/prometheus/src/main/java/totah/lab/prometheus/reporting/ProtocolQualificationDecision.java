package totah.lab.prometheus.reporting;

/** Frozen protocol-pilot outcomes. */
public enum ProtocolQualificationDecision {
    PROTOCOL_QUALIFIED_FOR_SCALEUP,
    PROTOCOL_QUALIFIED_WITH_DOCUMENTED_METHOD_SHIFT,
    PROTOCOL_EXECUTION_MISMATCH,
    PROTOCOL_RESULT_READER_MISMATCH,
    PROTOCOL_NOT_COMPARABLE_TO_EXISTING_EVIDENCE
}
