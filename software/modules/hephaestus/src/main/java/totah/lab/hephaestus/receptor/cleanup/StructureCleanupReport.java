package totah.lab.hephaestus.receptor.cleanup;

import java.util.List;

public record StructureCleanupReport(
        int inputResidues,
        int outputResidues,
        List<String> removedWaters,
        List<String> removedMetals,
        List<String> keptSpecialResidues) {

    public StructureCleanupReport {
        removedWaters = List.copyOf(removedWaters);
        removedMetals = List.copyOf(removedMetals);
        keptSpecialResidues = List.copyOf(keptSpecialResidues);
    }
}
