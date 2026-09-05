package totah.lab.mettl7.triage;

/** Scientific strength/category; categories are preserved and never averaged. */
public enum EvidenceClass {
    DIRECT_EXPERIMENTAL,
    INDIRECT_EXPERIMENTAL,
    COMPUTATIONAL,
    STRUCTURAL_INFERENCE,
    ANALOGY_ONLY,
    SPECULATIVE
}
