package totah.lab.hephaestus.ligand.topology;

import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.ChemicalBond;
import totah.lab.gaia.structure.Atom;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Perceives aromatic atoms in Kekulé-encoded bond tables (alternating
 * single/double bonds, no aromatic bond flags). SDF writers such as
 * RDKit Kekuléize on write, so aromaticity cannot rely on bond-type-4
 * flags alone.
 *
 * <p>Method: the smallest 5/6-membered cycle through each bond is
 * collected (single/double bonds only) and evaluated independently.
 * A cycle is aromatic when every atom is conjugated — it participates
 * in an in-cycle double bond, or is a lone-pair donor (N/O/S with
 * only single in-cycle bonds), or is a fusion atom shared with
 * another candidate cycle and double-bonded somewhere — and the
 * pi-electron count satisfies Hückel: 2 per in-cycle double bond and
 * 2 per donor, plus 1 per shared conjugated atom when the ring's own
 * count falls short of the sextet. This reproduces RDKit's Kekulé
 * behavior on fused systems (fusion bonds are single, so an aromatic
 * ring fused to an aliphatic one, as in tetralin, keeps its
 * aromaticity while the aliphatic ring is not marked).</p>
 *
 * <p>Deliberate divergence from full RDKit perception: rings larger
 * than 6 (tropylium, azulene) and exocyclic aromaticity are not
 * perceived.</p>
 */
public final class KekuleAromaticity {

    private KekuleAromaticity() {
    }

    public static boolean[] perceive(
            int atomCount,
            List<ChemicalBond> bonds,
            List<Atom> atoms
    ) {
        Objects.requireNonNull(bonds, "bonds");
        Objects.requireNonNull(atoms, "atoms");

        List<List<int[]>> adjacency = new ArrayList<>();
        for (int index = 0; index < atomCount; index++) {
            adjacency.add(new ArrayList<>());
        }
        for (int bondIndex = 0; bondIndex < bonds.size(); bondIndex++) {
            ChemicalBond bond = bonds.get(bondIndex);
            adjacency.get(bond.atomIndexA())
                    .add(new int[]{bond.atomIndexB(), bondIndex});
            adjacency.get(bond.atomIndexB())
                    .add(new int[]{bond.atomIndexA(), bondIndex});
        }

        // Unique smallest 5/6-cycles with single/double bonds only.
        Map<Set<Integer>, Set<Integer>> cycles = new LinkedHashMap<>();
        for (int bondIndex = 0; bondIndex < bonds.size(); bondIndex++) {
            ChemicalBond bond = bonds.get(bondIndex);
            List<int[]> path = shortestPath(
                    adjacency, bond.atomIndexA(), bond.atomIndexB(),
                    bondIndex);
            if (path == null) {
                continue;
            }
            int cycleLength = path.size() + 1;
            if (cycleLength < 5 || cycleLength > 6) {
                continue;
            }

            Set<Integer> cycleAtoms = new LinkedHashSet<>();
            Set<Integer> cycleBonds = new LinkedHashSet<>();
            cycleAtoms.add(bond.atomIndexA());
            for (int[] step : path) {
                cycleAtoms.add(step[0]);
                cycleBonds.add(step[1]);
            }
            cycleBonds.add(bondIndex);

            boolean simple = cycleBonds.stream()
                    .map(bonds::get)
                    .map(ChemicalBond::order)
                    .allMatch(order -> order == BondOrder.SINGLE
                            || order == BondOrder.DOUBLE);
            if (simple) {
                cycles.putIfAbsent(
                        Set.copyOf(cycleAtoms), cycleBonds);
            }
        }

        // Atoms appearing in more than one candidate cycle.
        Map<Integer, Integer> cycleMembership = new LinkedHashMap<>();
        for (Set<Integer> cycleAtoms : cycles.keySet()) {
            for (int atom : cycleAtoms) {
                cycleMembership.merge(atom, 1, Integer::sum);
            }
        }
        // Atoms double-bonded anywhere, and per-atom sigma-bond
        // counts (for the sp3 gate).
        Set<Integer> doubleBonded = new LinkedHashSet<>();
        Map<Integer, Integer> sigmaBondCount = new LinkedHashMap<>();
        for (int index = 0; index < atomCount; index++) {
            sigmaBondCount.put(index, 0);
        }
        for (ChemicalBond bond : bonds) {
            if (bond.order() == BondOrder.DOUBLE) {
                doubleBonded.add(bond.atomIndexA());
                doubleBonded.add(bond.atomIndexB());
            }
            if (bond.order() == BondOrder.SINGLE) {
                sigmaBondCount.merge(bond.atomIndexA(), 1, Integer::sum);
                sigmaBondCount.merge(bond.atomIndexB(), 1, Integer::sum);
            }
        }

        boolean[] aromatic = new boolean[atomCount];
        for (Map.Entry<Set<Integer>, Set<Integer>> cycle
                : cycles.entrySet()) {
            if (isAromaticCycle(cycle.getKey(), cycle.getValue(),
                    bonds, atoms, cycleMembership,
                    doubleBonded, sigmaBondCount)) {
                for (int atom : cycle.getKey()) {
                    aromatic[atom] = true;
                }
            }
        }
        return aromatic;
    }

    /*
     * Per-cycle Huckel counting: an in-cycle double bond contributes
     * 2 electrons; a donor (N/O/S with only single cycle bonds)
     * contributes 2 (lone pair); a fusion atom double-bonded in a
     * neighbouring ring contributes 1; a bare sp2 atom (three or
     * fewer sigma bonds, e.g. the ring carbon of a tetrazole or a
     * carbocation) contributes 0 but stays conjugated; an exocyclic
     * double-bond atom (carbonyl carbon) contributes 0 to the ring.
     * An atom with four sigma bonds (sp3) has no p orbital and kills
     * the cycle. Aromatic iff the total satisfies 4n+2 (>= 6).
     */
    private static boolean isAromaticCycle(
            Set<Integer> cycleAtoms,
            Set<Integer> cycleBonds,
            List<ChemicalBond> bonds,
            List<Atom> atoms,
            Map<Integer, Integer> cycleMembership,
            Set<Integer> doubleBondedAnywhere,
            Map<Integer, Integer> sigmaBondCount
    ) {
        int electrons = 0;
        int sharedConjugated = 0;

        Map<Integer, Integer> singleCycleBonds = new LinkedHashMap<>();
        Map<Integer, Boolean> inCycleDouble = new LinkedHashMap<>();
        for (int atom : cycleAtoms) {
            singleCycleBonds.put(atom, 0);
            inCycleDouble.put(atom, false);
        }
        for (int bondIndex : cycleBonds) {
            ChemicalBond bond = bonds.get(bondIndex);
            if (bond.order() == BondOrder.DOUBLE) {
                electrons += 2;
                inCycleDouble.put(bond.atomIndexA(), true);
                inCycleDouble.put(bond.atomIndexB(), true);
            } else {
                singleCycleBonds.merge(bond.atomIndexA(), 1,
                        Integer::sum);
                singleCycleBonds.merge(bond.atomIndexB(), 1,
                        Integer::sum);
            }
        }

        for (int atom : cycleAtoms) {
            if (inCycleDouble.get(atom)) {
                continue;
            }
            String element = atoms.get(atom).getElement().symbol();
            boolean donorElement = element.equals("N")
                    || element.equals("O") || element.equals("S");
            if (donorElement && singleCycleBonds.get(atom) == 2) {
                electrons += 2;
                continue;
            }
            // sp3 atoms have no p orbital: the ring is not aromatic.
            if (sigmaBondCount.get(atom) >= 4
                    && !doubleBondedAnywhere.contains(atom)) {
                return false;
            }
            // Fusion atoms conjugated through a neighbouring ring
            // contribute one electron; bare sp2 atoms and exocyclic
            // double-bond atoms contribute zero.
            if (cycleMembership.get(atom) > 1
                    && doubleBondedAnywhere.contains(atom)) {
                sharedConjugated++;
            }
        }

        // The ring's own pi electrons come first; fusion atoms only
        // top the count up when the ring is short of the sextet (a
        // ring that already has one — pyridone, pyrrole — must not
        // overshoot past 4n+2).
        if (electrons < 6) {
            electrons += sharedConjugated;
        }
        return electrons >= 6 && (electrons - 2) % 4 == 0;
    }

    /**
     * Shortest path between a bond's endpoints with that bond
     * removed, as a list of [atomIndex, bondIndex] steps (BFS).
     */
    private static List<int[]> shortestPath(
            List<List<int[]>> adjacency,
            int start,
            int target,
            int excludedBond
    ) {
        int[] previousAtom = new int[adjacency.size()];
        int[] previousBond = new int[adjacency.size()];
        java.util.Arrays.fill(previousAtom, -1);
        boolean[] visited = new boolean[adjacency.size()];

        ArrayDeque<Integer> queue = new ArrayDeque<>();
        visited[start] = true;
        queue.add(start);

        while (!queue.isEmpty()) {
            int atom = queue.removeFirst();
            if (atom == target) {
                List<int[]> path = new ArrayList<>();
                int current = target;
                while (current != start) {
                    path.add(0, new int[]{
                            current, previousBond[current]});
                    current = previousAtom[current];
                }
                return path;
            }
            for (int[] edge : adjacency.get(atom)) {
                if (edge[1] == excludedBond || visited[edge[0]]) {
                    continue;
                }
                visited[edge[0]] = true;
                previousAtom[edge[0]] = atom;
                previousBond[edge[0]] = edge[1];
                queue.add(edge[0]);
            }
        }
        return null;
    }
}
