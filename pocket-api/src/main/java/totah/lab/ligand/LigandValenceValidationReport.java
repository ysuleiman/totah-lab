package totah.lab.ligand;

import java.util.List;

public record LigandValenceValidationReport(
        List<AtomValence> atoms,
        List<String> violations) {

    public LigandValenceValidationReport {
        atoms = List.copyOf(atoms);
        violations = List.copyOf(violations);
    }

    public boolean valid() {
        return violations.isEmpty();
    }

    public record AtomValence(
            int atomIndex,
            String atomName,
            String element,
            int formalCharge,
            double depositedBondOrderSum,
            int plannedHydrogenCount,
            double completedBondOrderSum,
            double maximumSupportedValence) {
    }
}
