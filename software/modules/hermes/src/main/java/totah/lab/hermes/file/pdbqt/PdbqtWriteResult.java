package totah.lab.hermes.file.pdbqt;

import java.nio.file.Path;

public record PdbqtWriteResult(
        Path rigidOutput,
        Path flexibleOutput,
        int rigidAtomCount,
        int flexibleAtomCount,
        int flexibleResidueCount,
        int torsionCount) {
}
