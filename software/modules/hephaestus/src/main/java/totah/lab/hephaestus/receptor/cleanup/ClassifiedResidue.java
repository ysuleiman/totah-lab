package totah.lab.hephaestus.receptor.cleanup;


import totah.lab.gaia.structure.Residue;

import java.util.Objects;

public record ClassifiedResidue(
        String chainId,
        Residue residue,
        ResidueRole role,
        ResidueDisposition disposition,
        String reason) {

    public ClassifiedResidue {
        Objects.requireNonNull(chainId, "chainId");
        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(disposition, "disposition");

        reason = reason == null ? "" : reason.trim();
    }
}
