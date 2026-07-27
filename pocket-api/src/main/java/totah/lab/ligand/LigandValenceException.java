package totah.lab.ligand;

public class LigandValenceException extends IllegalArgumentException {

    private final LigandValenceValidationReport report;

    public LigandValenceException(LigandValenceValidationReport report) {
        super("Ligand valence validation failed: " + report.violations());
        this.report = report;
    }

    public LigandValenceValidationReport getReport() {
        return report;
    }
}
