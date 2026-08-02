package totah.lab.hephaestus.receptor;

import totah.lab.gaia.molecule.Protein;

import java.util.Objects;

public record ReceptorPreparationRequest(
        Protein protein,
        ReceptorPreparationOptions options) {

    public ReceptorPreparationRequest {
        Objects.requireNonNull(protein, "protein");
        options = options == null
                ? ReceptorPreparationOptions.defaults()
                : options;
    }

    public ReceptorPreparationRequest(Protein protein) {
        this(protein, ReceptorPreparationOptions.defaults());
    }
}
