package totah.lab.athena.interaction;

import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.ConnectivityProvenance;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Identity-keyed bonded-neighbor lookup over a structure's bond graph.
 */
final class BondNeighbors {

    private BondNeighbors() {
    }

    /**
     * Returns {@code true} when the structure's connectivity is usable
     * for bond-graph perception: {@link ConnectivityProvenance#EXPLICIT}
     * or {@link ConnectivityProvenance#INFERRED} with a non-empty bond
     * list.
     */
    static boolean usable(Structure structure) {
        Objects.requireNonNull(structure, "structure");
        return switch (structure.getConnectivityMetadata().provenance()) {
            case EXPLICIT, INFERRED -> !structure.getBonds().isEmpty();
            case PARTIAL, ABSENT -> false;
        };
    }

    /**
     * Maps every atom of the structure to its bonded neighbors, keyed by
     * atom object identity. Atoms without bonds map to an empty list.
     */
    static Map<Atom, List<Atom>> of(Structure structure) {
        Objects.requireNonNull(structure, "structure");

        Map<AtomReference, Atom> atomsByReference = new HashMap<>();
        Map<Atom, List<Atom>> neighbors = new IdentityHashMap<>();
        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                char insertionCode = residue.getInsertionCode() == null
                        ? ' ' : residue.getInsertionCode();
                for (Atom atom : residue.getAtoms()) {
                    atomsByReference.put(new AtomReference(
                            chain.id(), residue.getNumber(),
                            insertionCode, atom.getName()), atom);
                    neighbors.put(atom, new ArrayList<>());
                }
            }
        }
        for (Bond bond : structure.getBonds()) {
            Atom first = atomsByReference.get(bond.atom1());
            Atom second = atomsByReference.get(bond.atom2());
            if (first == null || second == null) {
                continue;
            }
            neighbors.get(first).add(second);
            neighbors.get(second).add(first);
        }
        return neighbors;
    }
}
