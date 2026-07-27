package totah.lab.ligand;

import java.util.List;
import java.util.Objects;

public record LigandHydrogenPlan(
        List<MissingLigandHydrogen> hydrogens,
        LigandValenceValidationReport valenceReport) {

    public LigandHydrogenPlan {
        hydrogens = List.copyOf(Objects.requireNonNull(hydrogens, "hydrogens is null"));
        Objects.requireNonNull(valenceReport, "valenceReport is null");
    }
}
