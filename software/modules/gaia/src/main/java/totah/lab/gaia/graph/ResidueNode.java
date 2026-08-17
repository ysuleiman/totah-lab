package totah.lab.gaia.graph;

import totah.lab.gaia.classification.ResidueClassificationEvidence;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Objects;

/** Immutable graph node referencing its source structure residue. */
public record ResidueNode(
        ResidueId id,
        Residue residue,
        ResidueChemistry chemistry) {

    public ResidueNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(chemistry, "chemistry");
    }

    public List<ResidueClassificationEvidence> classificationEvidence() {
        return residue.getClassificationEvidence();
    }
}
