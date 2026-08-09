package totah.lab.web.docking;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a multi-model vina output PDBQT into per-model line blocks
 * (MODEL..ENDMDL). Vina writes one model per pose table row.
 */
final class PdbqtModelSplitter {

    private PdbqtModelSplitter() {
    }

    static List<List<String>> split(Path multiModelPdbqt)
            throws IOException {
        List<List<String>> models = new ArrayList<>();
        List<String> current = null;
        for (String line : Files.readAllLines(multiModelPdbqt)) {
            if (line.startsWith("MODEL")) {
                current = new ArrayList<>();
            } else if (line.startsWith("ENDMDL")) {
                if (current != null) {
                    models.add(current);
                    current = null;
                }
            } else if (current != null) {
                current.add(line);
            }
        }
        if (models.isEmpty()) {
            // No MODEL records: treat the whole file as one model.
            models.add(Files.readAllLines(multiModelPdbqt));
        }
        return List.copyOf(models);
    }
}
