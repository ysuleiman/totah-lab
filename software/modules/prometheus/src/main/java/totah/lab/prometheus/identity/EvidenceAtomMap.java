package totah.lab.prometheus.identity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Explicit crosswalk between an artifact's file order and the canonical atom order.
 *
 * <p>{@code evidenceOrder.get(filePosition)} is the canonical index of the atom stored
 * at 0-based {@code filePosition} in the artifact. It must be a permutation of the
 * canonical indices of the wrapped {@link CanonicalAtomMap}.
 *
 * <p>Prometheus never trusts file order; all crosswalks between file positions and
 * canonical atoms are explicit, via this map.
 */
public final class EvidenceAtomMap {

    private final CanonicalAtomMap canonical;
    private final List<Integer> evidenceOrder;
    private final Map<Integer, Integer> filePositionByCanonicalIndex;

    public EvidenceAtomMap(CanonicalAtomMap canonical, List<Integer> evidenceOrder) {
        this.canonical = Objects.requireNonNull(canonical, "canonical");
        Objects.requireNonNull(evidenceOrder, "evidenceOrder");
        if (evidenceOrder.size() != canonical.size()) {
            throw new IllegalArgumentException(
                    "evidenceOrder size " + evidenceOrder.size()
                            + " does not match canonical atom count " + canonical.size());
        }
        Set<Integer> seen = new HashSet<>();
        Map<Integer, Integer> positions = new HashMap<>();
        for (int filePosition = 0; filePosition < evidenceOrder.size(); filePosition++) {
            Integer canonicalIndex = Objects.requireNonNull(
                    evidenceOrder.get(filePosition), "evidenceOrder must not contain null");
            if (canonical.byIndex(canonicalIndex).isEmpty()) {
                throw new IllegalArgumentException(
                        "evidenceOrder contains unknown canonical index: " + canonicalIndex);
            }
            if (!seen.add(canonicalIndex)) {
                throw new IllegalArgumentException(
                        "evidenceOrder is not a permutation: duplicate canonical index " + canonicalIndex);
            }
            positions.put(canonicalIndex, filePosition);
        }
        this.evidenceOrder = List.copyOf(evidenceOrder);
        this.filePositionByCanonicalIndex = Map.copyOf(positions);
    }

    /** Canonical index of the atom stored at 0-based {@code filePosition}. */
    public int canonicalIndexAt(int filePosition) {
        if (filePosition < 0 || filePosition >= evidenceOrder.size()) {
            throw new IndexOutOfBoundsException("filePosition out of range: " + filePosition);
        }
        return evidenceOrder.get(filePosition);
    }

    /** 0-based file position at which the atom with {@code canonicalIndex} is stored. */
    public int filePositionOf(int canonicalIndex) {
        Integer position = filePositionByCanonicalIndex.get(canonicalIndex);
        if (position == null) {
            throw new IllegalArgumentException("unknown canonical index: " + canonicalIndex);
        }
        return position;
    }

    public CanonicalAtomMap canonical() {
        return canonical;
    }

    /** File order as canonical indices; {@code get(filePosition) = canonicalIndex}. */
    public List<Integer> evidenceOrder() {
        return evidenceOrder;
    }

    /**
     * SHA-256 over the canonical hash followed by one line per file position:
     * {@code "filePosition|canonicalIndex"}, lines joined with {@code '\n'}.
     */
    public String crosswalkHash() {
        StringBuilder sb = new StringBuilder();
        sb.append(canonical.canonicalHash());
        for (int filePosition = 0; filePosition < evidenceOrder.size(); filePosition++) {
            sb.append('\n')
                    .append(filePosition)
                    .append('|')
                    .append(evidenceOrder.get(filePosition));
        }
        return CanonicalHashing.sha256Hex(sb.toString());
    }
}
