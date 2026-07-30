package totah.lab.report.analysis;

import totah.lab.pocket.Pocket;
import totah.lab.pocket.ResidueRef;
import totah.lab.protein.Structure;

import java.util.Objects;

final class PocketAnalysisSupport {

    private PocketAnalysisSupport() {
    }

    static Pocket resolvedCopy(Pocket pocket, Structure structure) {
        Objects.requireNonNull(pocket, "pocket");
        Objects.requireNonNull(structure, "structure");
        return new Pocket(
                pocket.getId(),
                pocket.getName(),
                pocket.getCenter(),
                pocket.getScore(),
                pocket.getResidueRefs(),
                pocket.getSource(),
                pocket.getAttributes(),
                reference -> resolve(reference, structure)
        );
    }

    private static totah.lab.protein.Residue resolve(
            ResidueRef reference,
            Structure structure
    ) {
        return structure.getResidue(
                reference.chain(),
                reference.number()
        );
    }
}
