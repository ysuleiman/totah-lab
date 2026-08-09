package totah.lab.hermes.file.pdbqt;

import java.util.List;

public record PdbqtFile(
        List<PdbqtModel> models
) {
    public PdbqtFile {
        models = List.copyOf(models);
    }

    public PdbqtModel firstModel() {
        if (models.isEmpty()) {
            throw new IllegalStateException(
                    "PDBQT contains no models"
            );
        }

        return models.getFirst();
    }
}
