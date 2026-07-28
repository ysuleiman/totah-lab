package totah.lab.ligand.ccd;

public class LigandGraphValidationException extends IllegalArgumentException {

    private final LigandGraphValidationReport report;

    public LigandGraphValidationException(LigandGraphValidationReport report) {
        super("Cannot build CCD graph for " + report.componentId()
                + ": missing heavy atoms=" + report.missingHeavyAtoms()
                + ", extra heavy atoms=" + report.extraHeavyAtoms());
        this.report = report;
    }

    public LigandGraphValidationReport getReport() {
        return report;
    }
}
