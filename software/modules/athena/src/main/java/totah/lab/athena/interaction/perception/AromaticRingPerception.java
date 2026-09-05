package totah.lab.athena.interaction.perception;

import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.ConnectivityProvenance;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Perceives aromatic rings of size 5 and 6, following PLIP.
 *
 * <p>Protein side: for PHE/TYR/TRP/HIS residues the ring atoms are taken
 * from the standard PDB atom-name template (PHE/TYR: CG,CD1,CD2,CE1,CE2,CZ;
 * HIS: CG,ND1,CD2,CE1,NE2; TRP: 5-ring CG,CD1,NE1,CE2,CD2 and 6-ring
 * CD2,CE2,CE3,CZ2,CZ3,CH2). A template ring is emitted only when all its
 * atoms are present; incomplete rings are skipped rather than guessed.
 * Source: {@link PerceptionProvenance#PROTEIN_TEMPLATE}.</p>
 *
 * <p>Ligand side (any residue not matching a template): minimal cycles of
 * size 5-6 are enumerated over the residue's bond subgraph. Candidate atoms
 * are the endpoints of {@link BondOrder#AROMATIC} bonds; when no aromatic
 * bond orders are available, atoms with AutoDock type {@code "A"} connected
 * by any bond order are used instead (noted on the result). Minimality is
 * approximated by chordlessness: a cycle with a shortcut bond between two
 * non-adjacent ring atoms is discarded. This is sufficient for drug-like
 * fused systems (e.g. naphthalene yields exactly two 6-rings, since the
 * fusion bond is a chord of the 10-membered perimeter) but is not a full
 * SSSR computation; exotic bridged systems may yield non-canonical ring
 * sets. Source: {@link PerceptionProvenance#BOND_GRAPH}.</p>
 *
 * <p>When connectivity is {@link ConnectivityProvenance#PARTIAL} or
 * {@link ConnectivityProvenance#ABSENT} the bond graph cannot be trusted;
 * perception degrades to grouping the residue's AD4 {@code "A"}-typed atoms
 * (at least 3) into a single pseudo-ring whose topology is unknown. Source:
 * {@link PerceptionProvenance#AD4_FALLBACK}.</p>
 *
 * <p>Output order is deterministic: structure traversal order of residues,
 * then rings ordered by their lowest canonical atom reference.</p>
 */
public final class AromaticRingPerception {

    private static final int MIN_RING_SIZE = 5;
    private static final int MAX_RING_SIZE = 6;
    private static final int MIN_FALLBACK_ATOMS = 3;

    private static final Map<String, List<List<String>>> PROTEIN_TEMPLATES =
            Map.of(
                    "PHE", List.of(List.of(
                            "CG", "CD1", "CD2", "CE1", "CE2", "CZ")),
                    "TYR", List.of(List.of(
                            "CG", "CD1", "CD2", "CE1", "CE2", "CZ")),
                    "HIS", List.of(List.of(
                            "CG", "ND1", "CD2", "CE1", "NE2")),
                    "TRP", List.of(
                            List.of("CG", "CD1", "NE1", "CE2", "CD2"),
                            List.of("CD2", "CE2", "CE3", "CZ2", "CZ3", "CH2")));

    /**
     * Perceives the aromatic rings of a structure.
     *
     * @param structure structure to inspect
     * @return perceived rings in deterministic order
     */
    public List<AromaticRing> perceive(Structure structure) {
        Objects.requireNonNull(structure, "structure");

        boolean connectivityUsable = switch (
                structure.getConnectivityMetadata().provenance()) {
            case EXPLICIT, INFERRED -> true;
            case PARTIAL, ABSENT -> false;
        };

        List<AromaticRing> rings = new ArrayList<>();
        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                ResidueId owner = new ResidueId(
                        chain.id(),
                        residue.getNumber(),
                        residue.getInsertionCode());
                List<List<String>> templates =
                        PROTEIN_TEMPLATES.get(residue.getName());
                if (templates != null) {
                    perceiveTemplates(chain.id(), residue, owner,
                            templates, rings);
                } else {
                    perceiveLigandResidue(structure, chain.id(), residue,
                            owner, connectivityUsable, rings);
                }
            }
        }
        return List.copyOf(rings);
    }

    private void perceiveTemplates(
            String chainId,
            Residue residue,
            ResidueId owner,
            List<List<String>> templates,
            List<AromaticRing> rings) {

        int ringIndex = 0;
        for (List<String> template : templates) {
            List<Atom> atoms = new ArrayList<>(template.size());
            for (String atomName : template) {
                var atom = residue.findAtom(atomName);
                if (atom.isEmpty()) {
                    atoms = null;
                    break;
                }
                atoms.add(atom.get());
            }
            if (atoms == null) {
                continue; // incomplete template: skipped, never guessed
            }
            rings.add(new AromaticRing(
                    ringId(residue.getName(), chainId, residue, ringIndex++),
                    owner,
                    atoms,
                    centroid(atoms),
                    PerceptionProvenance.PROTEIN_TEMPLATE,
                    "standard " + residue.getName()
                            + " side-chain ring atom template"));
        }
    }

    private void perceiveLigandResidue(
            Structure structure,
            String chainId,
            Residue residue,
            ResidueId owner,
            boolean connectivityUsable,
            List<AromaticRing> rings) {

        Map<AtomReference, Atom> atomsByReference =
                residueAtomIndex(chainId, residue);
        List<Bond> intraResidueBonds = intraResidueBonds(
                structure, atomsByReference.keySet());

        if (!connectivityUsable || intraResidueBonds.isEmpty()) {
            perceiveAd4Fallback(chainId, residue, owner,
                    structure.getConnectivityMetadata().provenance(),
                    atomsByReference, rings);
            return;
        }

        // Primary candidacy: endpoints of aromatic bonds.
        Map<AtomReference, Set<AtomReference>> adjacency = new TreeMap<>();
        String note = "chordless 5/6-cycles over AROMATIC bond orders";
        for (Bond bond : intraResidueBonds) {
            if (bond.order() == BondOrder.AROMATIC) {
                adjacency.computeIfAbsent(bond.atom1(), key -> new TreeSet<>())
                        .add(bond.atom2());
                adjacency.computeIfAbsent(bond.atom2(), key -> new TreeSet<>())
                        .add(bond.atom1());
            }
        }

        if (adjacency.isEmpty()) {
            // Degraded candidacy: AD4 aromatic-typed atoms over any bond order.
            note = "no AROMATIC bond orders; chordless 5/6-cycles over "
                    + "AD4 \"A\"-typed atoms connected by the bond graph";
            Set<AtomReference> aromaticTyped = new TreeSet<>();
            for (Map.Entry<AtomReference, Atom> entry
                    : atomsByReference.entrySet()) {
                if ("A".equals(entry.getValue().getAutoDockType())) {
                    aromaticTyped.add(entry.getKey());
                }
            }
            for (Bond bond : intraResidueBonds) {
                if (aromaticTyped.contains(bond.atom1())
                        && aromaticTyped.contains(bond.atom2())) {
                    adjacency.computeIfAbsent(
                            bond.atom1(), key -> new TreeSet<>())
                            .add(bond.atom2());
                    adjacency.computeIfAbsent(
                            bond.atom2(), key -> new TreeSet<>())
                            .add(bond.atom1());
                }
            }
        }

        int ringIndex = 0;
        for (List<AtomReference> cycle : chordlessCycles(adjacency)) {
            List<Atom> atoms = new ArrayList<>(cycle.size());
            for (AtomReference reference : cycle) {
                atoms.add(atomsByReference.get(reference));
            }
            rings.add(new AromaticRing(
                    ringId(residue.getName(), chainId, residue, ringIndex++),
                    owner,
                    atoms,
                    centroid(atoms),
                    PerceptionProvenance.BOND_GRAPH,
                    note));
        }
    }

    private void perceiveAd4Fallback(
            String chainId,
            Residue residue,
            ResidueId owner,
            ConnectivityProvenance connectivity,
            Map<AtomReference, Atom> atomsByReference,
            List<AromaticRing> rings) {

        List<Atom> aromaticTyped = new ArrayList<>();
        for (Atom atom : atomsByReference.values()) {
            if ("A".equals(atom.getAutoDockType()) && atom.isHeavyAtom()) {
                aromaticTyped.add(atom);
            }
        }
        if (aromaticTyped.size() < MIN_FALLBACK_ATOMS) {
            return;
        }
        rings.add(new AromaticRing(
                ringId(residue.getName(), chainId, residue, 0),
                owner,
                aromaticTyped,
                centroid(aromaticTyped),
                PerceptionProvenance.AD4_FALLBACK,
                "connectivity " + connectivity
                        + "; ring topology unknown, atoms grouped by "
                        + "AutoDock4 \"A\" type"));
    }

    /**
     * Enumerates chordless simple cycles of size 5-6. Each cycle starts at
     * its lowest canonical atom reference; the two traversal directions of
     * the same cycle are deduplicated by atom set.
     */
    private static List<List<AtomReference>> chordlessCycles(
            Map<AtomReference, Set<AtomReference>> adjacency) {

        List<List<AtomReference>> raw = new ArrayList<>();
        for (AtomReference start : adjacency.keySet()) {
            Deque<AtomReference> path = new ArrayDeque<>();
            path.addLast(start);
            enumerate(start, start, adjacency, path, raw);
        }

        Set<Set<AtomReference>> seen = new HashSet<>();
        List<List<AtomReference>> cycles = new ArrayList<>();
        for (List<AtomReference> cycle : raw) {
            Set<AtomReference> atomSet = new LinkedHashSet<>(cycle);
            if (!seen.add(atomSet) || !isChordless(cycle, adjacency)) {
                continue;
            }
            cycles.add(cycle);
        }
        cycles.sort((first, second) ->
                first.getFirst().compareTo(second.getFirst()));
        return cycles;
    }

    private static void enumerate(
            AtomReference start,
            AtomReference current,
            Map<AtomReference, Set<AtomReference>> adjacency,
            Deque<AtomReference> path,
            List<List<AtomReference>> cycles) {

        if (path.size() > MAX_RING_SIZE) {
            return;
        }
        for (AtomReference next
                : adjacency.getOrDefault(current, Set.of())) {
            if (next.equals(start)) {
                if (path.size() >= MIN_RING_SIZE) {
                    cycles.add(new ArrayList<>(path));
                }
            } else if (next.compareTo(start) > 0 && !path.contains(next)) {
                path.addLast(next);
                enumerate(start, next, adjacency, path, cycles);
                path.removeLast();
            }
        }
    }

    private static boolean isChordless(
            List<AtomReference> cycle,
            Map<AtomReference, Set<AtomReference>> adjacency) {

        int size = cycle.size();
        for (int index = 0; index < size; index++) {
            AtomReference previous = cycle.get((index + size - 1) % size);
            AtomReference next = cycle.get((index + 1) % size);
            for (AtomReference neighbor
                    : adjacency.getOrDefault(cycle.get(index), Set.of())) {
                if (!neighbor.equals(previous) && !neighbor.equals(next)
                        && cycle.contains(neighbor)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<Bond> intraResidueBonds(
            Structure structure,
            Set<AtomReference> residueAtoms) {

        List<Bond> bonds = new ArrayList<>();
        for (Bond bond : structure.bonds()) {
            if (residueAtoms.contains(bond.atom1())
                    && residueAtoms.contains(bond.atom2())) {
                bonds.add(bond);
            }
        }
        return bonds;
    }

    private static Map<AtomReference, Atom> residueAtomIndex(
            String chainId,
            Residue residue) {

        char insertionCode = residue.getInsertionCode() == null
                ? ' '
                : residue.getInsertionCode();
        Map<AtomReference, Atom> index = new LinkedHashMap<>();
        for (Atom atom : residue.getAtoms()) {
            index.put(
                    new AtomReference(
                            chainId,
                            residue.getNumber(),
                            insertionCode,
                            atom.getName()),
                    atom);
        }
        return index;
    }

    private static String ringId(
            String residueName,
            String chainId,
            Residue residue,
            int ringIndex) {

        String insertionCode = residue.getInsertionCode() == null
                ? ""
                : residue.getInsertionCode().toString();
        return residueName + " " + chainId + ":"
                + residue.getNumber() + insertionCode + " ring" + ringIndex;
    }

    private static Point3D centroid(List<Atom> atoms) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (Atom atom : atoms) {
            x += atom.getPosition().x();
            y += atom.getPosition().y();
            z += atom.getPosition().z();
        }
        return new Point3D(
                x / atoms.size(), y / atoms.size(), z / atoms.size());
    }
}
