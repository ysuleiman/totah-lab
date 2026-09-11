package totah.lab.athena.recognition;

/** Frozen cross-ensemble basin vocabulary. */
public enum RecognitionBasinState {
    PRESERVED_BASIN,
    SUBSTITUTED_BASIN,
    REROUTED_BASIN,
    FRAGMENTED,
    LOST_BASIN,
    MULTIPLE_ALTERNATIVE_BASINS,
    UNOBSERVED_BASIN,
    UNAVAILABLE,
    INSUFFICIENT_EVIDENCE
}
