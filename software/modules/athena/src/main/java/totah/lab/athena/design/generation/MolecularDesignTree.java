package totah.lab.athena.design.generation;

import totah.lab.athena.design.backend.GraphEditReceipt;
import totah.lab.athena.design.backend.MolecularGraph;

import java.util.List;
import java.util.Map;

/** Immutable generated-molecule graph with complete parent-child provenance. */
public record MolecularDesignTree(String rootNodeId, List<Node> nodes, List<Edge> edges,
                                  Map<String, String> canonicalKeyToNodeId) {
    public MolecularDesignTree {
        nodes = List.copyOf(nodes); edges = List.copyOf(edges);
        canonicalKeyToNodeId = Map.copyOf(canonicalKeyToNodeId);
    }
    public record Node(String nodeId, String canonicalKey, MolecularGraph graph, int depth,
                       boolean expanded) { }
    public record Edge(String parentNodeId, String childNodeId, GraphEditReceipt editReceipt) { }
}
