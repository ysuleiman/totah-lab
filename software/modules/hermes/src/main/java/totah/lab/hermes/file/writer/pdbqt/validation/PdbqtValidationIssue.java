package totah.lab.hermes.file.writer.pdbqt.validation;

import java.util.Objects;

public record PdbqtValidationIssue(
        PdbqtValidationSeverity severity,
        PdbqtValidationCode code,
        String message,
        String location) {
    public PdbqtValidationIssue {
        Objects.requireNonNull(severity,"severity"); Objects.requireNonNull(code,"code");
        Objects.requireNonNull(message,"message"); location=location==null?"":location;
    }
}
