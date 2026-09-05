package totah.lab.athena.interaction.perception;

/**
 * How a perception result was derived. Every perception result carries one
 * of these flags so downstream consumers can tell high-confidence
 * bond-graph perception from degraded fallbacks; nothing is guessed
 * silently.
 */
public enum PerceptionProvenance {

    /** Derived from the residue-name template (standard amino acids). */
    PROTEIN_TEMPLATE,

    /** Derived from the structure bond graph (EXPLICIT/INFERRED connectivity). */
    BOND_GRAPH,

    /**
     * Degraded: bond connectivity was absent or partial, so AutoDock4 atom
     * typing was used instead of the bond graph.
     */
    AD4_FALLBACK,

    /**
     * Degraded: bond connectivity was absent or partial, so the per-residue
     * partial-charge sum was used as a single pseudo-group.
     */
    CHARGE_SUM_FALLBACK;

    /**
     * Returns {@code true} when this provenance marks a degraded fallback
     * rather than full bond-graph or template perception.
     */
    public boolean isDegraded() {
        return this == AD4_FALLBACK || this == CHARGE_SUM_FALLBACK;
    }
}
