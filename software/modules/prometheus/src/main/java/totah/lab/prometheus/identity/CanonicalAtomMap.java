package totah.lab.prometheus.identity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The canonical atom ordering of a molecule: a {@link MoleculeIdentity} plus the
 * atoms in canonical order (ascending {@link CanonicalAtomId#canonicalIndex()}).
 * Canonical indices must be unique, and labels must be unique.
 */
public final class CanonicalAtomMap {

    private final MoleculeIdentity molecule;
    private final List<CanonicalAtomId> atoms;
    private final Map<Integer, CanonicalAtomId> byIndex;
    private final Map<String, CanonicalAtomId> byLabel;

    public CanonicalAtomMap(MoleculeIdentity molecule, List<CanonicalAtomId> atoms) {
        this.molecule = Objects.requireNonNull(molecule, "molecule");
        List<CanonicalAtomId> ordered = new ArrayList<>(Objects.requireNonNull(atoms, "atoms"));
        ordered.sort((a, b) -> Integer.compare(a.canonicalIndex(), b.canonicalIndex()));

        Set<Integer> indices = new HashSet<>();
        Set<String> labels = new HashSet<>();
        for (CanonicalAtomId atom : ordered) {
            Objects.requireNonNull(atom, "atoms must not contain null");
            if (!indices.add(atom.canonicalIndex())) {
                throw new IllegalArgumentException("duplicate canonicalIndex: " + atom.canonicalIndex());
            }
            if (!labels.add(atom.label())) {
                throw new IllegalArgumentException("duplicate label: " + atom.label());
            }
        }
        this.atoms = List.copyOf(ordered);
        this.byIndex = new HashMap<>();
        this.byLabel = new HashMap<>();
        for (CanonicalAtomId atom : this.atoms) {
            byIndex.put(atom.canonicalIndex(), atom);
            byLabel.put(atom.label(), atom);
        }
    }

    public Optional<CanonicalAtomId> byIndex(int canonicalIndex) {
        return Optional.ofNullable(byIndex.get(canonicalIndex));
    }

    public Optional<CanonicalAtomId> byLabel(String label) {
        return Optional.ofNullable(byLabel.get(label));
    }

    public int size() {
        return atoms.size();
    }

    /** Atoms in canonical order (ascending canonical index). */
    public List<CanonicalAtomId> atoms() {
        return atoms;
    }

    public MoleculeIdentity molecule() {
        return molecule;
    }

    /**
     * SHA-256 over the molecule id followed by one line per atom in canonical
     * order: {@code "index|label|element"}, lines joined with {@code '\n'}.
     */
    public String canonicalHash() {
        StringBuilder sb = new StringBuilder();
        sb.append(molecule.moleculeId());
        for (CanonicalAtomId atom : atoms) {
            sb.append('\n')
                    .append(atom.canonicalIndex())
                    .append('|')
                    .append(atom.label())
                    .append('|')
                    .append(atom.elementSymbol());
        }
        return CanonicalHashing.sha256Hex(sb.toString());
    }
}
