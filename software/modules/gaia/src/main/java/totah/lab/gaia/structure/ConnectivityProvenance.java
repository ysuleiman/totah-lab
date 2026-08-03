package totah.lab.gaia.structure;

/**
 * Source confidence for structure-owned connectivity.
 * EXPLICIT means all connectivity exposed by the source parser was mapped;
 * PARTIAL means some exposed connectivity could not be mapped; ABSENT means
 * the parser exposed none; INFERRED is reserved for a separate inference step.
 */
public enum ConnectivityProvenance {
    EXPLICIT,
    PARTIAL,
    ABSENT,
    INFERRED
}
