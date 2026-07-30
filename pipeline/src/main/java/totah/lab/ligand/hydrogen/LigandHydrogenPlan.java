package totah.lab.ligand.hydrogen;

import totah.lab.ligand.ccd.MissingLigandHydrogen;

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
