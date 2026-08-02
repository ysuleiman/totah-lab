package totah.lab.hermes.file.writer.pdbqt.validation;

import java.util.Objects;

public final class PdbqtValidationException extends IllegalStateException {
    private final PdbqtValidationReport report;
    public PdbqtValidationException(PdbqtValidationReport report){
        super("PDBQT validation failed with "+Objects.requireNonNull(report,"report").issues().size()+" issue(s).");this.report=report;
    }
    public PdbqtValidationReport report(){return report;}
}
