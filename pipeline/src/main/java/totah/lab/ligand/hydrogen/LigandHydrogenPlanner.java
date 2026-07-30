package totah.lab.ligand.hydrogen;

import totah.lab.ligand.ccd.CcdLigandGraphResult;

import java.util.Objects;

public final class LigandHydrogenPlanner {

    private final LigandValenceValidator valenceValidator;

    public LigandHydrogenPlanner() {
        this(new LigandValenceValidator());
    }

    LigandHydrogenPlanner(LigandValenceValidator valenceValidator) {
        this.valenceValidator = Objects.requireNonNull(
                valenceValidator, "valenceValidator is null");
    }

    public LigandHydrogenPlan plan(CcdLigandGraphResult graphResult) {
        Objects.requireNonNull(graphResult, "graphResult is null");
        LigandValenceValidationReport report = valenceValidator.validate(
                graphResult.graph(), graphResult.missingHydrogens());
        if (!report.valid()) {
            throw new LigandValenceException(report);
        }
        return new LigandHydrogenPlan(graphResult.missingHydrogens(), report);
    }
}
