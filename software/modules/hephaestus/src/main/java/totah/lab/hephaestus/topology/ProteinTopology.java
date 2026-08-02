package totah.lab.hephaestus.topology;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProteinTopology implements TopologyModel {
    private final int atomCount;
    private final List<Edge> edges;
    private final List<List<Integer>> adjacency;

    public ProteinTopology(int atomCount, List<Edge> edges) {
        if (atomCount < 0) {
            throw new IllegalArgumentException("atomCount must not be negative.");
        }
        this.atomCount = atomCount;
        this.edges = List.copyOf(edges);
        List<List<Integer>> neighbors = new ArrayList<>(atomCount);
        for (int index = 0; index < atomCount; index++) {
            neighbors.add(new ArrayList<>());
        }
        for (Edge edge : this.edges) {
            neighbors.get(edge.indexA()).add(edge.indexB());
            neighbors.get(edge.indexB()).add(edge.indexA());
        }
        this.adjacency = neighbors.stream().map(List::copyOf).toList();
    }

    @Override
    public String name() {
        return "amber-protein-topology";
    }

    public int atomCount() { return atomCount; }
    public int bondCount() { return edges.size(); }
    public List<Edge> edges() { return edges; }

    public List<Integer> neighbors(int atomIndex) {
        if (atomIndex < 0 || atomIndex >= atomCount) {
            return Collections.emptyList();
        }
        return adjacency.get(atomIndex);
    }
}
