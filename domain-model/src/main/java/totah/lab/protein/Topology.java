package totah.lab.protein;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Topology {

    private final List<Edge> edges;
    private final List<List<Integer>> adjacency;

    public Topology(int atomCount, List<Edge> edges) {
        this.edges = List.copyOf(edges);
        List<List<Integer>> neighbors = new ArrayList<>(atomCount);
        for (int i = 0; i < atomCount; i++) {
            neighbors.add(new ArrayList<>());
        }
        for (Edge edge : edges) {
            neighbors.get(edge.indexA()).add(edge.indexB());
            neighbors.get(edge.indexB()).add(edge.indexA());
        }
        this.adjacency = neighbors.stream()
                .map(List::copyOf)
                .toList();
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public int getBondCount() {
        return edges.size();
    }

    public int getAtomCount() {
        return adjacency.size();
    }

    public List<Integer> getNeighbors(int index) {
        if (index < 0 || index >= adjacency.size()) {
            return Collections.emptyList();
        }
        return adjacency.get(index);
    }

    public record Edge(int indexA, int indexB, double length) {
    }
}
