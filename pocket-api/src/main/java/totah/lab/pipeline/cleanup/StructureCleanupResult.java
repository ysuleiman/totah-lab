package totah.lab.pipeline.cleanup;

import java.util.List;
import java.util.Objects;

public record StructureCleanupResult(
        List<ClassifiedResidue> receptorResidues,
        List<ClassifiedResidue> extractedLigands,
        List<ClassifiedResidue> removedWaters,
        List<ClassifiedResidue> removedMetals,
        List<ClassifiedResidue> keptSpecialResidues
) {
    public StructureCleanupResult {
        receptorResidues = List.copyOf(Objects.requireNonNull(
                receptorResidues, "receptorResidues is null"));
        extractedLigands = List.copyOf(Objects.requireNonNull(
                extractedLigands, "extractedLigands is null"));
        removedWaters = List.copyOf(Objects.requireNonNull(
                removedWaters, "removedWaters is null"));
        removedMetals = List.copyOf(Objects.requireNonNull(
                removedMetals, "removedMetals is null"));
        keptSpecialResidues = List.copyOf(Objects.requireNonNull(
                keptSpecialResidues, "keptSpecialResidues is null"));
    }
}
