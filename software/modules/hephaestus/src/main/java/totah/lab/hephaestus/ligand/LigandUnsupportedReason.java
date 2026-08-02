package totah.lab.hephaestus.ligand;

/** Stable public reasons why ordinary free-ligand preparation cannot proceed. */
public enum LigandUnsupportedReason {
    INCOMPLETE_CCD,
    MISSING_HEAVY_ATOMS,
    EXTRA_HEAVY_ATOMS,
    MULTI_COMPONENT,
    COVALENTLY_ATTACHED,
    DISCONNECTED_GRAPH,
    UNSUPPORTED_ELEMENT_FOR_CHARGE,
    UNSUPPORTED_AD4_TYPE,
    INVALID_VALENCE,
    UNUSABLE_HYDROGEN_REFERENCE_GEOMETRY
}
