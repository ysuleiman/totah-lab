package totah.lab.ligand;

import totah.lab.chemistry.MolecularGraph;

import java.util.Map;
import java.util.Objects;

public record LigandAd4TypingResult(
        MolecularGraph graph,
        Map<String, Integer> typeCounts) {

    public LigandAd4TypingResult {
        Objects.requireNonNull(graph, "graph is null");
        typeCounts = Map.copyOf(Objects.requireNonNull(typeCounts, "typeCounts is null"));
    }

    public int atomCount() {
        return graph.atoms().size();
    }
}
