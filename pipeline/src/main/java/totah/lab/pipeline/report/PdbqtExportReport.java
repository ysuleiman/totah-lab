package totah.lab.pipeline.report;

public record PdbqtExportReport(
        int residueCount,
        int atomCount,
        int flexResidueCount,
        String receptorPath,
        String flexPath) {
}
