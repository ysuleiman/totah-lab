package totah.lab.hephaestus.ligand;

import totah.lab.gaia.molecule.Ligand;

import java.util.Objects;

public record LigandPreparationRequest(
        Ligand ligand,
        LigandPreparationOptions options) {

    public LigandPreparationRequest {
        Objects.requireNonNull(ligand, "ligand");
        options = options == null
                ? LigandPreparationOptions.defaults()
                : options;
    }

    public LigandPreparationRequest(Ligand ligand) {
        this(ligand, LigandPreparationOptions.defaults());
    }
}
