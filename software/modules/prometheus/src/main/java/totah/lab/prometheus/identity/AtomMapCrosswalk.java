package totah.lab.prometheus.identity;

import java.util.Objects;

/**
 * Bridges an evidence artifact's file order and a force-field typing through a
 * shared {@link CanonicalAtomMap}. The compact constructor rejects evidence and
 * force-field maps that do not wrap the same canonical atom map, as determined by
 * {@link CanonicalAtomMap#canonicalHash()} equality.
 */
public record AtomMapCrosswalk(
        EvidenceAtomMap evidenceMap,
        ForceFieldAtomMap forceFieldMap) {

    public AtomMapCrosswalk {
        Objects.requireNonNull(evidenceMap, "evidenceMap");
        Objects.requireNonNull(forceFieldMap, "forceFieldMap");
        if (!evidenceMap.canonical().canonicalHash()
                .equals(forceFieldMap.canonical().canonicalHash())) {
            throw new IllegalArgumentException(
                    "evidenceMap and forceFieldMap wrap different canonical atom maps");
        }
    }

    /**
     * Force-field type of the atom stored at 0-based {@code filePosition} in the
     * evidence artifact, resolved through the canonical index.
     */
    public String forceFieldTypeAt(int filePosition) {
        return forceFieldMap.typeOf(evidenceMap.canonicalIndexAt(filePosition));
    }
}
