package totah.lab.hephaestus.topology;

import java.util.List;

public record MissingHeavyAtomReport(
        int missingCount,
        List<Entry> missingAtoms) {

    public MissingHeavyAtomReport {
        missingAtoms = List.copyOf(missingAtoms);
    }

    public record Entry(
            String residueKey,
            String residueLabel,
            String templateName,
            String atomName) {
    }
}
