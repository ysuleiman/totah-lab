package totah.lab.ligand.torsion;

import totah.lab.chemistry.ChemicalBond;
import totah.lab.chemistry.MolecularGraph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Detects graph edges that belong to at least one cycle.
 */
public final class LigandRingDetector {

    public Set<Integer> detectRingBondIndices(MolecularGraph graph) {
        Objects.requireNonNull(graph, "graph is null");
        List<List<Edge>> adjacency = adjacency(graph);
        Set<Integer> ringBonds = new LinkedHashSet<>();
        for (int bondIndex = 0; bondIndex < graph.bonds().size(); bondIndex++) {
            ChemicalBond bond = graph.bonds().get(bondIndex);
            if (connectedWithoutBond(
                    bond.atomIndexA(), bond.atomIndexB(), bondIndex, adjacency)) {
                ringBonds.add(bondIndex);
            }
        }
        return Set.copyOf(ringBonds);
    }

    private boolean connectedWithoutBond(
            int start,
            int target,
            int excludedBond,
            List<List<Edge>> adjacency) {
        boolean[] visited = new boolean[adjacency.size()];
        Deque<Integer> queue = new ArrayDeque<>();
        visited[start] = true;
        queue.add(start);
        while (!queue.isEmpty()) {
            int atom = queue.removeFirst();
            for (Edge edge : adjacency.get(atom)) {
                if (edge.bondIndex() == excludedBond || visited[edge.atomIndex()]) {
                    continue;
                }
                if (edge.atomIndex() == target) {
                    return true;
                }
                visited[edge.atomIndex()] = true;
                queue.addLast(edge.atomIndex());
            }
        }
        return false;
    }

    private List<List<Edge>> adjacency(MolecularGraph graph) {
        List<List<Edge>> adjacency = new ArrayList<>(graph.atoms().size());
        for (int index = 0; index < graph.atoms().size(); index++) {
            adjacency.add(new ArrayList<>());
        }
        for (int index = 0; index < graph.bonds().size(); index++) {
            ChemicalBond bond = graph.bonds().get(index);
            adjacency.get(bond.atomIndexA()).add(new Edge(bond.atomIndexB(), index));
            adjacency.get(bond.atomIndexB()).add(new Edge(bond.atomIndexA(), index));
        }
        return adjacency;
    }

    private record Edge(int atomIndex, int bondIndex) {
    }
}
