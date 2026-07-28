package totah.lab.ligand.ccd;

import java.util.List;

public record LigandGraphValidationReport(
        String componentId,
        int depositedAtomCount,
        int mappedAtomCount,
        List<String> missingHeavyAtoms,
        List<String> extraHeavyAtoms,
        List<String> missingHydrogens) {

    public LigandGraphValidationReport {
        missingHeavyAtoms = List.copyOf(missingHeavyAtoms);
        extraHeavyAtoms = List.copyOf(extraHeavyAtoms);
        missingHydrogens = List.copyOf(missingHydrogens);
    }

    public boolean valid() {
        return missingHeavyAtoms.isEmpty() && extraHeavyAtoms.isEmpty();
    }
}
