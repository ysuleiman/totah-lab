package totah.lab.ligand.torsion;

import totah.lab.chemistry.ChemicalBond;
import totah.lab.chemistry.MolecularGraph;
import totah.lab.docking.torsion.TorsionBranch;
import totah.lab.docking.torsion.TorsionTree;
import totah.lab.protein.ElementResolver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builds a deterministic AutoDock ligand torsion tree from classified bonds.
 */
public final class LigandTorsionTreeBuilder {

    private final RotatableBondClassifier bondClassifier;

    public LigandTorsionTreeBuilder() {
        this(new RotatableBondClassifier());
    }

    LigandTorsionTreeBuilder(RotatableBondClassifier bondClassifier) {
        this.bondClassifier = Objects.requireNonNull(
                bondClassifier, "bondClassifier is null");
    }

    public LigandTorsionTreeResult build(MolecularGraph graph) {
        return build(graph, Set.of());
    }

    public LigandTorsionTreeResult build(
            MolecularGraph graph,
            Set<Integer> explicitlyRigidBondIndices) {
        Objects.requireNonNull(graph, "graph is null");
        if (graph.atoms().isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot build a torsion tree for an empty ligand graph");
        }
        validateConnected(graph);
        LigandRotatableBondReport report =
                bondClassifier.classify(graph, explicitlyRigidBondIndices);
        Set<Integer> rotatable = Set.copyOf(report.rotatableBondIndices());
        Fragmentation fragmentation = fragment(graph, rotatable);
        int rootFragment = chooseRootFragment(graph, fragmentation.fragments());

        TorsionTree tree = new TorsionTree();
        for (int atomIndex : fragmentation.fragments().get(rootFragment)) {
            tree.addRootAtom(atomIndex);
        }
        boolean[] visitedFragments = new boolean[fragmentation.fragments().size()];
        visitedFragments[rootFragment] = true;
        for (FragmentEdge edge : fragmentation.adjacency().get(rootFragment)) {
            tree.addRootBranch(buildBranch(
                    graph, fragmentation, edge, rootFragment, visitedFragments));
        }

        validateTree(graph, tree, report.rotatableBondCount());
        return new LigandTorsionTreeResult(
                tree,
                report,
                fragmentation.fragments().get(rootFragment),
                report.rotatableBondCount());
    }

    private TorsionBranch buildBranch(
            MolecularGraph graph,
            Fragmentation fragmentation,
            FragmentEdge edge,
            int parentFragment,
            boolean[] visitedFragments) {
        int childFragment = edge.otherFragment(parentFragment);
        if (visitedFragments[childFragment]) {
            throw new IllegalStateException(
                    "Rotatable fragment graph contains a cycle");
        }
        visitedFragments[childFragment] = true;
        ChemicalBond bond = graph.bonds().get(edge.bondIndex());
        int parentAtom = fragmentation.atomFragments()[bond.atomIndexA()] == parentFragment
                ? bond.atomIndexA() : bond.atomIndexB();
        int childAtom = parentAtom == bond.atomIndexA()
                ? bond.atomIndexB() : bond.atomIndexA();
        TorsionBranch branch = new TorsionBranch(
                parentAtom,
                childAtom,
                fragmentation.fragments().get(childFragment));
        for (FragmentEdge childEdge : fragmentation.adjacency().get(childFragment)) {
            int nextFragment = childEdge.otherFragment(childFragment);
            if (!visitedFragments[nextFragment]) {
                branch.getChildren().add(buildBranch(
                        graph, fragmentation, childEdge, childFragment, visitedFragments));
            }
        }
        return branch;
    }

    private Fragmentation fragment(
            MolecularGraph graph,
            Set<Integer> rotatableBondIndices) {
        List<List<GraphEdge>> atomAdjacency = atomAdjacency(graph);
        int[] atomFragments = new int[graph.atoms().size()];
        java.util.Arrays.fill(atomFragments, -1);
        List<List<Integer>> fragments = new ArrayList<>();
        for (int start = 0; start < graph.atoms().size(); start++) {
            if (atomFragments[start] >= 0) {
                continue;
            }
            int fragmentIndex = fragments.size();
            List<Integer> atoms = new ArrayList<>();
            Deque<Integer> queue = new ArrayDeque<>();
            atomFragments[start] = fragmentIndex;
            queue.add(start);
            while (!queue.isEmpty()) {
                int atom = queue.removeFirst();
                atoms.add(atom);
                for (GraphEdge edge : atomAdjacency.get(atom)) {
                    if (rotatableBondIndices.contains(edge.bondIndex())
                            || atomFragments[edge.atomIndex()] >= 0) {
                        continue;
                    }
                    atomFragments[edge.atomIndex()] = fragmentIndex;
                    queue.addLast(edge.atomIndex());
                }
            }
            atoms.sort(Integer::compareTo);
            fragments.add(List.copyOf(atoms));
        }

        List<List<FragmentEdge>> fragmentAdjacency = new ArrayList<>(fragments.size());
        for (int index = 0; index < fragments.size(); index++) {
            fragmentAdjacency.add(new ArrayList<>());
        }
        for (int bondIndex : rotatableBondIndices.stream().sorted().toList()) {
            ChemicalBond bond = graph.bonds().get(bondIndex);
            int first = atomFragments[bond.atomIndexA()];
            int second = atomFragments[bond.atomIndexB()];
            if (first == second) {
                throw new IllegalStateException(
                        "Rotatable bond did not separate rigid fragments: " + bondIndex);
            }
            FragmentEdge edge = new FragmentEdge(first, second, bondIndex);
            fragmentAdjacency.get(first).add(edge);
            fragmentAdjacency.get(second).add(edge);
        }
        fragmentAdjacency.forEach(edges ->
                edges.sort(Comparator.comparingInt(FragmentEdge::bondIndex)));
        return new Fragmentation(
                List.copyOf(fragments),
                fragmentAdjacency.stream().map(List::copyOf).toList(),
                atomFragments);
    }

    private int chooseRootFragment(
            MolecularGraph graph,
            List<List<Integer>> fragments) {
        return java.util.stream.IntStream.range(0, fragments.size())
                .boxed()
                .max(Comparator
                        .comparingInt((Integer index) ->
                                heavyAtomCount(graph, fragments.get(index)))
                        .thenComparingInt(index -> fragments.get(index).size())
                        .thenComparingInt(index -> -fragments.get(index).getFirst()))
                .orElseThrow();
    }

    private int heavyAtomCount(MolecularGraph graph, List<Integer> fragment) {
        return (int) fragment.stream()
                .filter(index -> !"H".equals(ElementResolver.resolveSymbol(
                        graph.atoms().get(index), false)))
                .count();
    }

    private void validateConnected(MolecularGraph graph) {
        List<List<GraphEdge>> adjacency = atomAdjacency(graph);
        boolean[] visited = new boolean[graph.atoms().size()];
        Deque<Integer> queue = new ArrayDeque<>();
        visited[0] = true;
        queue.add(0);
        int count = 0;
        while (!queue.isEmpty()) {
            int atom = queue.removeFirst();
            count++;
            for (GraphEdge edge : adjacency.get(atom)) {
                if (!visited[edge.atomIndex()]) {
                    visited[edge.atomIndex()] = true;
                    queue.addLast(edge.atomIndex());
                }
            }
        }
        if (count != graph.atoms().size()) {
            throw new IllegalArgumentException(
                    "Ligand graph must be connected to build one torsion tree");
        }
    }

    private void validateTree(
            MolecularGraph graph,
            TorsionTree tree,
            int expectedBranchCount) {
        boolean[] covered = new boolean[graph.atoms().size()];
        Set<Integer> availableParents = new HashSet<>();
        for (int atom : tree.getRootAtoms()) {
            cover(atom, covered);
            availableParents.add(atom);
        }
        int branchCount = 0;
        Deque<TorsionBranch> branches = new ArrayDeque<>(tree.getRootBranches());
        while (!branches.isEmpty()) {
            TorsionBranch branch = branches.removeFirst();
            branchCount++;
            if (!availableParents.contains(branch.getParentIdx())
                    || !branch.getMovingAtoms().contains(branch.getChildIdx())) {
                throw new IllegalStateException(
                        "Invalid ligand torsion branch endpoints");
            }
            for (int atom : branch.getMovingAtoms()) {
                cover(atom, covered);
                availableParents.add(atom);
            }
            branches.addAll(branch.getChildren());
        }
        if (branchCount != expectedBranchCount) {
            throw new IllegalStateException(
                    "Torsion branch count does not match rotatable bonds");
        }
        for (int atom = 0; atom < covered.length; atom++) {
            if (!covered[atom]) {
                throw new IllegalStateException(
                        "Ligand atom is absent from torsion tree: " + atom);
            }
        }
    }

    private void cover(int atom, boolean[] covered) {
        if (atom < 0 || atom >= covered.length || covered[atom]) {
            throw new IllegalStateException(
                    "Invalid or duplicate ligand atom in torsion tree: " + atom);
        }
        covered[atom] = true;
    }

    private List<List<GraphEdge>> atomAdjacency(MolecularGraph graph) {
        List<List<GraphEdge>> adjacency = new ArrayList<>(graph.atoms().size());
        for (int index = 0; index < graph.atoms().size(); index++) {
            adjacency.add(new ArrayList<>());
        }
        for (int bondIndex = 0; bondIndex < graph.bonds().size(); bondIndex++) {
            ChemicalBond bond = graph.bonds().get(bondIndex);
            adjacency.get(bond.atomIndexA()).add(
                    new GraphEdge(bond.atomIndexB(), bondIndex));
            adjacency.get(bond.atomIndexB()).add(
                    new GraphEdge(bond.atomIndexA(), bondIndex));
        }
        return adjacency;
    }

    private record GraphEdge(int atomIndex, int bondIndex) {
    }

    private record FragmentEdge(int firstFragment, int secondFragment, int bondIndex) {
        private int otherFragment(int fragment) {
            if (fragment == firstFragment) {
                return secondFragment;
            }
            if (fragment == secondFragment) {
                return firstFragment;
            }
            throw new IllegalArgumentException("Fragment is not an edge endpoint");
        }
    }

    private record Fragmentation(
            List<List<Integer>> fragments,
            List<List<FragmentEdge>> adjacency,
            int[] atomFragments) {
    }
}
