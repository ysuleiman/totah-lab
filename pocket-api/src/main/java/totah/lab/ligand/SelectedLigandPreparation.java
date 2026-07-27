package totah.lab.ligand;

import totah.lab.pipeline.cleanup.ClassifiedResidue;

import java.util.Objects;

public record SelectedLigandPreparation(
        ClassifiedResidue selectedLigand,
        LigandPreparationResult preparation
) {
    public SelectedLigandPreparation {
        Objects.requireNonNull(selectedLigand, "selectedLigand is null");
        Objects.requireNonNull(preparation, "preparation is null");
    }
}
