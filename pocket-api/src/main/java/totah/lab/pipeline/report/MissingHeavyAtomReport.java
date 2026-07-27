package totah.lab.pipeline.report;

import java.util.List;

public record MissingHeavyAtomReport(
        int missingCount,
        boolean pocketCenterAvailable,
        double pocketProximityCutoff,
        List<Entry> missingAtoms) {

    public MissingHeavyAtomReport {
        missingAtoms = List.copyOf(missingAtoms);
    }

    public record Entry(
            String residueKey,
            String residueLabel,
            String templateName,
            String atomName,
            Double residueDistanceToPocketCenter,
            boolean nearPocket) {
    }
}
