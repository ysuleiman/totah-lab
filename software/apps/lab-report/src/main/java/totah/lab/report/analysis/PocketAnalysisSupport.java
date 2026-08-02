package totah.lab.report.analysis;

import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.pocket.Pocket;
import totah.lab.pocket.ResidueRef;

import java.util.List;
import java.util.Objects;

final class PocketAnalysisSupport {

    private PocketAnalysisSupport() {
    }

    static List<ResolvedResidue> resolve(
            Pocket pocket,
            Structure structure) {
        Objects.requireNonNull(pocket, "pocket");
        Objects.requireNonNull(structure, "structure");
        return pocket.getResidueRefs().stream()
                .map(reference -> resolve(reference, structure))
                .toList();
    }

    private static ResolvedResidue resolve(
            ResidueRef reference,
            Structure structure) {
        Residue residue = structure.findResidue(
                        reference.chain(), reference.number())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Pocket residue not found: " + reference));
        return new ResolvedResidue(reference.chain(), residue);
    }

    record ResolvedResidue(String chainId, Residue residue) {
    }
}
