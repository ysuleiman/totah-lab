package totah.lab.ligand;

import totah.lab.chemistry.MolecularGraph;

import java.util.List;
import java.util.Objects;

public record CcdLigandGraphResult(
        MolecularGraph graph,
        LigandGraphValidationReport validationReport,
        List<MissingLigandHydrogen> missingHydrogens,
        List<CcdAtomCoordinates> depositedCcdCoordinates) {

    public CcdLigandGraphResult {
        Objects.requireNonNull(graph, "graph is null");
        Objects.requireNonNull(validationReport, "validationReport is null");
        missingHydrogens = List.copyOf(
                Objects.requireNonNull(missingHydrogens, "missingHydrogens is null"));
        depositedCcdCoordinates = List.copyOf(Objects.requireNonNull(
                depositedCcdCoordinates, "depositedCcdCoordinates is null"));
    }
}
