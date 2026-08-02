package totah.lab.hephaestus.ligand.flexibility;

import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.ChemicalBond;
import totah.lab.gaia.structure.Atom;
import totah.lab.hephaestus.ligand.topology.LigandTopology;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LigandFlexibilityModelBuilder {
    public LigandFlexibilityModel build(List<Atom> atoms, LigandTopology topology) {
        if (atoms.isEmpty() || atoms.size() != topology.atomCount()) {
            throw new IllegalArgumentException("Atoms must match a nonempty topology.");
        }
        List<List<Edge>> adjacency = adjacency(atoms.size(), topology.bonds());
        ensureConnected(adjacency);
        Set<Integer> rotatable = new HashSet<>();
        int[] heavyDegree = heavyDegrees(atoms, topology.bonds());
        for (int index = 0; index < topology.bonds().size(); index++) {
            ChemicalBond bond = topology.bonds().get(index);
            if (bond.order() == BondOrder.SINGLE && !bond.aromatic()
                    && atoms.get(bond.atomIndexA()).isHeavyAtom()
                    && atoms.get(bond.atomIndexB()).isHeavyAtom()
                    && heavyDegree[bond.atomIndexA()] > 1 && heavyDegree[bond.atomIndexB()] > 1
                    && !inRing(adjacency, bond.atomIndexA(), bond.atomIndexB(), index)
                    && !amideLike(atoms, topology.bonds(), bond)) {
                rotatable.add(index);
            }
        }
        Fragmentation fragmentation = fragment(atoms.size(), adjacency, topology.bonds(), rotatable);
        int root = chooseRoot(atoms, fragmentation.fragments());
        List<LigandFragment> ordered = new ArrayList<>();
        boolean[] visited = new boolean[fragmentation.fragments().size()];
        visit(root, null, null, null, fragmentation, topology.bonds(), visited, ordered);
        return new LigandFlexibilityModel(atoms.size(), id(root), ordered);
    }

    private void visit(int fragment, String parentId, Integer parentAtom, Integer childAtom,
                       Fragmentation value, List<ChemicalBond> bonds,
                       boolean[] visited, List<LigandFragment> result) {
        if (visited[fragment]) throw new IllegalStateException("Fragment graph is cyclic.");
        visited[fragment] = true;
        result.add(new LigandFragment(id(fragment), value.fragments().get(fragment),
                parentId, parentAtom, childAtom));
        for (FragmentEdge edge : value.fragmentAdjacency().get(fragment)) {
            int child = edge.other(fragment);
            if (visited[child]) continue;
            ChemicalBond bond = bonds.get(edge.bondIndex());
            int from = value.atomFragments()[bond.atomIndexA()] == fragment
                    ? bond.atomIndexA() : bond.atomIndexB();
            int to = from == bond.atomIndexA() ? bond.atomIndexB() : bond.atomIndexA();
            visit(child, id(fragment), from, to, value, bonds, visited, result);
        }
    }

    private Fragmentation fragment(int atomCount, List<List<Edge>> adjacency,
                                   List<ChemicalBond> bonds, Set<Integer> rotatable) {
        int[] atomFragments = new int[atomCount];
        Arrays.fill(atomFragments, -1);
        List<List<Integer>> fragments = new ArrayList<>();
        for (int start = 0; start < atomCount; start++) {
            if (atomFragments[start] >= 0) continue;
            int fragment = fragments.size();
            List<Integer> members = new ArrayList<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            atomFragments[start] = fragment;
            queue.add(start);
            while (!queue.isEmpty()) {
                int atom = queue.removeFirst();
                members.add(atom);
                for (Edge edge : adjacency.get(atom)) {
                    if (!rotatable.contains(edge.bondIndex())
                            && atomFragments[edge.atomIndex()] < 0) {
                        atomFragments[edge.atomIndex()] = fragment;
                        queue.add(edge.atomIndex());
                    }
                }
            }
            members.sort(Integer::compareTo);
            fragments.add(List.copyOf(members));
        }
        List<List<FragmentEdge>> fragmentAdjacency = new ArrayList<>();
        for (int index = 0; index < fragments.size(); index++) fragmentAdjacency.add(new ArrayList<>());
        for (int bondIndex : rotatable.stream().sorted().toList()) {
            ChemicalBond bond = bonds.get(bondIndex);
            int first = atomFragments[bond.atomIndexA()];
            int second = atomFragments[bond.atomIndexB()];
            FragmentEdge edge = new FragmentEdge(first, second, bondIndex);
            fragmentAdjacency.get(first).add(edge);
            fragmentAdjacency.get(second).add(edge);
        }
        fragmentAdjacency.forEach(list -> list.sort(Comparator.comparingInt(FragmentEdge::bondIndex)));
        return new Fragmentation(List.copyOf(fragments),
                fragmentAdjacency.stream().map(List::copyOf).toList(), atomFragments);
    }

    private boolean inRing(List<List<Edge>> adjacency, int start, int target, int excludedBond) {
        boolean[] visited = new boolean[adjacency.size()];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        visited[start] = true;
        queue.add(start);
        while (!queue.isEmpty()) {
            int atom = queue.removeFirst();
            for (Edge edge : adjacency.get(atom)) {
                if (edge.bondIndex() == excludedBond) continue;
                if (edge.atomIndex() == target) return true;
                if (!visited[edge.atomIndex()]) {
                    visited[edge.atomIndex()] = true;
                    queue.add(edge.atomIndex());
                }
            }
        }
        return false;
    }

    private boolean amideLike(List<Atom> atoms, List<ChemicalBond> bonds, ChemicalBond bond) {
        int carbon;
        String first = atoms.get(bond.atomIndexA()).getElement().symbol();
        String second = atoms.get(bond.atomIndexB()).getElement().symbol();
        if ("C".equals(first) && "N".equals(second)) carbon = bond.atomIndexA();
        else if ("N".equals(first) && "C".equals(second)) carbon = bond.atomIndexB();
        else return false;
        return bonds.stream().anyMatch(candidate -> candidate.order() == BondOrder.DOUBLE
                && ((candidate.atomIndexA() == carbon && Set.of("O", "S").contains(
                atoms.get(candidate.atomIndexB()).getElement().symbol()))
                || (candidate.atomIndexB() == carbon && Set.of("O", "S").contains(
                atoms.get(candidate.atomIndexA()).getElement().symbol()))));
    }

    private int[] heavyDegrees(List<Atom> atoms, List<ChemicalBond> bonds) {
        int[] result = new int[atoms.size()];
        for (ChemicalBond bond : bonds) {
            if (atoms.get(bond.atomIndexB()).isHeavyAtom()) result[bond.atomIndexA()]++;
            if (atoms.get(bond.atomIndexA()).isHeavyAtom()) result[bond.atomIndexB()]++;
        }
        return result;
    }

    private int chooseRoot(List<Atom> atoms, List<List<Integer>> fragments) {
        return java.util.stream.IntStream.range(0, fragments.size()).boxed().max(
                Comparator.comparingInt((Integer index) -> (int) fragments.get(index).stream()
                                .filter(atom -> atoms.get(atom).isHeavyAtom()).count())
                        .thenComparingInt(index -> fragments.get(index).size())
                        .thenComparingInt(index -> -fragments.get(index).getFirst())).orElseThrow();
    }

    private void ensureConnected(List<List<Edge>> adjacency) {
        boolean[] visited = new boolean[adjacency.size()];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        visited[0] = true;
        queue.add(0);
        int count = 0;
        while (!queue.isEmpty()) {
            int atom = queue.removeFirst(); count++;
            for (Edge edge : adjacency.get(atom)) if (!visited[edge.atomIndex()]) {
                visited[edge.atomIndex()] = true; queue.add(edge.atomIndex());
            }
        }
        if (count != adjacency.size()) throw new IllegalArgumentException("Ligand graph is disconnected.");
    }

    private List<List<Edge>> adjacency(int count, List<ChemicalBond> bonds) {
        List<List<Edge>> result = new ArrayList<>();
        for (int index = 0; index < count; index++) result.add(new ArrayList<>());
        for (int index = 0; index < bonds.size(); index++) {
            ChemicalBond bond = bonds.get(index);
            result.get(bond.atomIndexA()).add(new Edge(bond.atomIndexB(), index));
            result.get(bond.atomIndexB()).add(new Edge(bond.atomIndexA(), index));
        }
        return result;
    }

    private String id(int index) { return "fragment-" + index; }
    private record Edge(int atomIndex, int bondIndex) {}
    private record FragmentEdge(int first, int second, int bondIndex) {
        int other(int value) { return value == first ? second : first; }
    }
    private record Fragmentation(List<List<Integer>> fragments,
                                 List<List<FragmentEdge>> fragmentAdjacency,
                                 int[] atomFragments) {}
}
