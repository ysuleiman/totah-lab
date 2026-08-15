package totah.lab.prometheus.identity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Force-field typing of a canonical atom map: every canonical atom carries a
 * force-field type string within a named force-field family.
 *
 * <p>Type strings are plain strings and duplicates are allowed: two atoms may share
 * a generic type (e.g. GAFF "c6") yet still be distinguished by canonical index, so
 * they can later receive distinct molecule-specific parameters. Use
 * {@link #atomsByType()} to group canonical indices by type.
 */
public final class ForceFieldAtomMap {

    private final CanonicalAtomMap canonical;
    private final String forceFieldFamily;
    private final Map<Integer, String> atomTypeByCanonicalIndex;

    public ForceFieldAtomMap(
            CanonicalAtomMap canonical,
            String forceFieldFamily,
            Map<Integer, String> atomTypeByCanonicalIndex) {

        this.canonical = Objects.requireNonNull(canonical, "canonical");
        Objects.requireNonNull(forceFieldFamily, "forceFieldFamily");
        if (forceFieldFamily.isBlank()) {
            throw new IllegalArgumentException("forceFieldFamily must be non-blank");
        }
        this.forceFieldFamily = forceFieldFamily;
        Objects.requireNonNull(atomTypeByCanonicalIndex, "atomTypeByCanonicalIndex");
        Map<Integer, String> types = new LinkedHashMap<>();
        for (CanonicalAtomId atom : canonical.atoms()) {
            String type = atomTypeByCanonicalIndex.get(atom.canonicalIndex());
            if (type == null) {
                throw new IllegalArgumentException(
                        "missing force-field type for canonical index " + atom.canonicalIndex());
            }
            if (type.isBlank()) {
                throw new IllegalArgumentException(
                        "blank force-field type for canonical index " + atom.canonicalIndex());
            }
            types.put(atom.canonicalIndex(), type);
        }
        for (Integer index : atomTypeByCanonicalIndex.keySet()) {
            if (canonical.byIndex(index).isEmpty()) {
                throw new IllegalArgumentException(
                        "force-field type given for unknown canonical index " + index);
            }
        }
        this.atomTypeByCanonicalIndex = Map.copyOf(types);
    }

    public String typeOf(int canonicalIndex) {
        String type = atomTypeByCanonicalIndex.get(canonicalIndex);
        if (type == null) {
            throw new IllegalArgumentException("unknown canonical index: " + canonicalIndex);
        }
        return type;
    }

    /**
     * Groups canonical indices (ascending) by force-field type. One generic type
     * may map to several canonical atoms that can later receive distinct
     * molecule-specific parameters.
     */
    public Map<String, List<Integer>> atomsByType() {
        Map<String, List<Integer>> grouped = new LinkedHashMap<>();
        for (CanonicalAtomId atom : canonical.atoms()) {
            grouped.computeIfAbsent(atomTypeByCanonicalIndex.get(atom.canonicalIndex()),
                    k -> new ArrayList<>()).add(atom.canonicalIndex());
        }
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        grouped.forEach((type, indices) -> result.put(type, List.copyOf(indices)));
        return Map.copyOf(result);
    }

    public CanonicalAtomMap canonical() {
        return canonical;
    }

    public String forceFieldFamily() {
        return forceFieldFamily;
    }

    public Map<Integer, String> atomTypeByCanonicalIndex() {
        return atomTypeByCanonicalIndex;
    }
}
