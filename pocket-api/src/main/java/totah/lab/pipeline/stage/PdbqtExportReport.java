package totah.lab.pipeline.stage;

public record PdbqtExportReport(
        int residueCount,
        int atomCount,
        int flexResidueCount,
        String receptorPath,
        String flexPath) {
}
