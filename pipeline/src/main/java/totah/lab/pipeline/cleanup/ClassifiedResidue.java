package totah.lab.pipeline.cleanup;

import totah.lab.protein.Residue;

import java.util.Objects;

public record ClassifiedResidue(
        Residue residue,
        ResidueRole role,
        ResidueDisposition disposition,
        String reason
) {
    public ClassifiedResidue {
        Objects.requireNonNull(residue);
        Objects.requireNonNull(role);
        Objects.requireNonNull(disposition);
        Objects.requireNonNull(reason);
    }
}
