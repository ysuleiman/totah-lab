package totah.lab.ligand;

import totah.lab.chemistry.MolecularGraph;

import java.util.Objects;

public record LigandChargeAssignmentResult(
        MolecularGraph graph,
        String source,
        int totalFormalCharge,
        double totalPartialCharge) {

    public LigandChargeAssignmentResult {
        Objects.requireNonNull(graph, "graph is null");
        Objects.requireNonNull(source, "source is null");
    }
}
