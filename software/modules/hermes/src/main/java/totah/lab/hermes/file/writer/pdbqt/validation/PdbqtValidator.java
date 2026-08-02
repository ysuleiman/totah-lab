package totah.lab.hermes.file.writer.pdbqt.validation;

import totah.lab.hermes.file.validation.FileValidator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PdbqtValidator implements FileValidator<PdbqtValidationReport> {
    private final PdbqtStructureValidator structureValidator=new PdbqtStructureValidator();
    private final PdbqtFlexibilityValidator flexibilityValidator=new PdbqtFlexibilityValidator();

    @Override public PdbqtValidationReport validate(Path path)throws IOException{return validatePdbqt(path);}
    public PdbqtValidationReport validatePdbqt(Path pdbqtFile)throws IOException{
        return structureValidator.validate(read(pdbqtFile),false);
    }
    public PdbqtValidationReport validateFlexiblePdbqt(Path rigidFile,Path flexibleFile)throws IOException{
        List<String>rigid=read(rigidFile),flexible=read(flexibleFile);List<PdbqtValidationIssue>issues=new ArrayList<>();
        issues.addAll(structureValidator.validate(rigid,false).issues());
        issues.addAll(structureValidator.validate(flexible,true).issues());
        issues.addAll(flexibilityValidator.validate(flexible).issues());
        Set<String>overlap=new HashSet<>(structureValidator.atomKeys(rigid));overlap.retainAll(structureValidator.atomKeys(flexible));
        for(String atom:overlap)issues.add(new PdbqtValidationIssue(PdbqtValidationSeverity.ERROR,
                PdbqtValidationCode.RIGID_FLEXIBLE_OVERLAP,"Atom occurs in rigid and flexible files.",atom));
        return new PdbqtValidationReport(issues);
    }
    private List<String>read(Path path)throws IOException{return Files.readAllLines(path.toAbsolutePath().normalize(),StandardCharsets.UTF_8);}
}
