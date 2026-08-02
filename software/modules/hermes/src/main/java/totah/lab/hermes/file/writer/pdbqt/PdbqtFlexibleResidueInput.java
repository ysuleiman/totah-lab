package totah.lab.hermes.file.writer.pdbqt;

import java.util.List;
import java.util.Objects;

public record PdbqtFlexibleResidueInput(
        String residueName,
        String chainId,
        int residueNumber,
        Character insertionCode,
        int anchorAtomIndex,
        List<PdbqtFragmentInput> fragments,
        List<PdbqtRotatableBondInput> rotatableBonds) {
    public PdbqtFlexibleResidueInput {
        Objects.requireNonNull(residueName, "residueName"); Objects.requireNonNull(chainId, "chainId");
        fragments = List.copyOf(fragments); rotatableBonds = List.copyOf(rotatableBonds);
    }
}
