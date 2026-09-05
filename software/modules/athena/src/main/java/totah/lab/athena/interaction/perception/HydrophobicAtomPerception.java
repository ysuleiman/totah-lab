package totah.lab.athena.interaction.perception;

import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.ConnectivityProvenance;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Perceives hydrophobic atoms following the PLIP rule: a carbon atom is
 * hydrophobic iff all its bonded neighbors are carbons or hydrogens.
 *
 * <p>When the structure connectivity is
 * {@link ConnectivityProvenance#EXPLICIT} or
 * {@link ConnectivityProvenance#INFERRED} the bond graph is used and the
 * result carries {@link PerceptionProvenance#BOND_GRAPH}. When connectivity
 * is {@link ConnectivityProvenance#PARTIAL} or
 * {@link ConnectivityProvenance#ABSENT} the bond graph cannot be trusted, so
 * perception degrades to AutoDock4 typing: an atom is hydrophobic iff its
 * AutoDock type is {@code "C"} or {@code "A"}, and the result carries
 * {@link PerceptionProvenance#AD4_FALLBACK}. Atoms without a usable AutoDock
 * type in degraded mode are left unclassified — they are never guessed.</p>
 *
 * <p>Under the pure PLIP rule a carbon with no bonds at all is trivially
 * hydrophobic (all zero neighbors are C or H); callers working with
 * EXPLICIT connectivity are expected to have complete bonding.</p>
 */
public final class HydrophobicAtomPerception {

    private static final Set<String> HYDROPHOBIC_AD4_TYPES = Set.of("C", "A");

    /**
     * Perceives the hydrophobic atoms of a structure.
     *
     * @param structure structure to inspect
     * @return perceived hydrophobic atoms with provenance
     */
    public HydrophobicAtoms perceive(Structure structure) {
        Objects.requireNonNull(structure, "structure");

        ConnectivityProvenance connectivity =
                structure.getConnectivityMetadata().provenance();

        return switch (connectivity) {
            case EXPLICIT, INFERRED -> perceiveFromBonds(structure);
            case PARTIAL, ABSENT -> perceiveFromAd4Types(
                    structure, connectivity);
        };
    }

    private HydrophobicAtoms perceiveFromBonds(Structure structure) {
        Map<AtomReference, Atom> atomsByReference = indexAtoms(structure);
        Map<AtomReference, Set<AtomReference>> neighbors =
                neighbors(structure);

        List<Atom> hydrophobic = new ArrayList<>();
        for (Map.Entry<AtomReference, Atom> entry
                : atomsByReference.entrySet()) {
            Atom atom = entry.getValue();
            if (atom.getElement() != Element.C) {
                continue;
            }
            boolean allCarbonOrHydrogen = true;
            for (AtomReference neighborReference
                    : neighbors.getOrDefault(entry.getKey(), Set.of())) {
                Element neighborElement =
                        atomsByReference.get(neighborReference).getElement();
                if (neighborElement != Element.C
                        && neighborElement != Element.H) {
                    allCarbonOrHydrogen = false;
                    break;
                }
            }
            if (allCarbonOrHydrogen) {
                hydrophobic.add(atom);
            }
        }

        return new HydrophobicAtoms(
                hydrophobic,
                PerceptionProvenance.BOND_GRAPH,
                "PLIP carbon-neighborhood rule over "
                        + structure.getConnectivityMetadata().provenance()
                        + " connectivity");
    }

    private HydrophobicAtoms perceiveFromAd4Types(
            Structure structure,
            ConnectivityProvenance connectivity) {

        List<Atom> hydrophobic = new ArrayList<>();
        for (Atom atom : indexAtoms(structure).values()) {
            String autoDockType = atom.getAutoDockType();
            if (autoDockType == null || autoDockType.isBlank()) {
                continue; // unclassified in degraded mode; never guessed
            }
            if (HYDROPHOBIC_AD4_TYPES.contains(autoDockType.trim())) {
                hydrophobic.add(atom);
            }
        }

        return new HydrophobicAtoms(
                hydrophobic,
                PerceptionProvenance.AD4_FALLBACK,
                "connectivity " + connectivity
                        + "; degraded to AutoDock4 C/A typing");
    }

    private static Map<AtomReference, Atom> indexAtoms(Structure structure) {
        // Insertion order = structure traversal order; kept for deterministic output.
        Map<AtomReference, Atom> index = new LinkedHashMap<>();
        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                char insertionCode = residue.getInsertionCode() == null
                        ? ' '
                        : residue.getInsertionCode();
                for (Atom atom : residue.getAtoms()) {
                    index.put(
                            new AtomReference(
                                    chain.id(),
                                    residue.getNumber(),
                                    insertionCode,
                                    atom.getName()),
                            atom);
                }
            }
        }
        return index;
    }

    private static Map<AtomReference, Set<AtomReference>> neighbors(
            Structure structure) {

        Map<AtomReference, Set<AtomReference>> neighbors = new HashMap<>();
        for (Bond bond : structure.bonds()) {
            neighbors.computeIfAbsent(bond.atom1(), key -> new HashSet<>())
                    .add(bond.atom2());
            neighbors.computeIfAbsent(bond.atom2(), key -> new HashSet<>())
                    .add(bond.atom1());
        }
        return neighbors;
    }
}
