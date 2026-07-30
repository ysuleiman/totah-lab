package totah.lab.ligand.hydrogen;

import totah.lab.chemistry.MolecularGraph;

import java.util.List;
import java.util.Objects;

public record LigandHydrogenationResult(
        MolecularGraph graph,
        List<String> generatedHydrogenNames,
        LigandValenceValidationReport valenceReport) {

    public LigandHydrogenationResult {
        Objects.requireNonNull(graph, "graph is null");
        generatedHydrogenNames = List.copyOf(Objects.requireNonNull(
                generatedHydrogenNames, "generatedHydrogenNames is null"));
        Objects.requireNonNull(valenceReport, "valenceReport is null");
    }
}
