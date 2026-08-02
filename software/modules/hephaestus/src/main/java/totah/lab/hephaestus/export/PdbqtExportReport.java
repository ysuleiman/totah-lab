package totah.lab.hephaestus.export;

import java.nio.file.Path;
import java.util.Objects;

public record PdbqtExportReport(
        int chainCount,
        int residueCount,
        int atomCount,
        Path receptorPath,
        Path flexiblePath,
        int rigidAtomCount,
        int flexibleAtomCount,
        int torsionCount) {

    public PdbqtExportReport {
        Objects.requireNonNull(receptorPath, "receptorPath");
    }
}
