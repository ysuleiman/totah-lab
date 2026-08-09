package totah.lab.hermes.file.pdbqt.validation;

import java.util.List;

public record PdbqtValidationReport(List<PdbqtValidationIssue> issues) {
    public PdbqtValidationReport { issues=issues==null?List.of():List.copyOf(issues); }
    public boolean valid(){return !hasErrors();}
    public boolean hasErrors(){return issues.stream().anyMatch(i->i.severity()==PdbqtValidationSeverity.ERROR);}
    public boolean hasWarnings(){return issues.stream().anyMatch(i->i.severity()==PdbqtValidationSeverity.WARNING);}
}
