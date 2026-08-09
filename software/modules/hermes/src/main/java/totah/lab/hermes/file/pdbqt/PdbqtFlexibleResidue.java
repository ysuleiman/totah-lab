package totah.lab.hermes.file.pdbqt;

import java.util.List;
import java.util.Objects;

public record PdbqtFlexibleResidue(
        String residueName,
        String chainId,
        int residueNumber,
        Character insertionCode,
        int anchorAtomIndex,
        List<PdbqtFragment> fragments,
        List<PdbqtRotatableBond> rotatableBonds) {
    public PdbqtFlexibleResidue {
        Objects.requireNonNull(residueName, "residueName"); Objects.requireNonNull(chainId, "chainId");
        fragments = List.copyOf(fragments); rotatableBonds = List.copyOf(rotatableBonds);
    }
}
