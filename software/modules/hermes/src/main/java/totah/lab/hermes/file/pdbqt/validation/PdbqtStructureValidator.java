package totah.lab.hermes.file.pdbqt.validation;

import totah.lab.hermes.file.pdbqt.PdbqtTypes;
import totah.lab.hermes.file.pdbqt.PdbqtAtom;
import totah.lab.hermes.file.pdbqt.PdbqtFormatException;
import totah.lab.hermes.file.pdbqt.internal.PdbqtAtomParser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PdbqtStructureValidator {
    private static final Set<String> FLEX_RECORDS=Set.of("BEGIN_RES","END_RES","ROOT","ENDROOT","BRANCH","ENDBRANCH","TORSDOF");
    private final PdbqtAtomParser atomParser = new PdbqtAtomParser();

    public PdbqtValidationReport validate(List<String> lines, boolean flexible) {
        List<PdbqtValidationIssue> issues=new ArrayList<>(); Set<Integer> serials=new HashSet<>();
        if(lines==null||lines.isEmpty())issues.add(error(PdbqtValidationCode.FILE_EMPTY,"PDBQT file is empty.","file"));
        int residueBlock=0;
        for(int i=0;lines!=null&&i<lines.size();i++){
            String line=lines.get(i), location="line "+(i+1); if(line.isBlank())continue;
            String record=line.trim().split("\\s+")[0];
            if("BEGIN_RES".equals(record)){residueBlock++;serials.clear();}
            if("ATOM".equals(record)||"HETATM".equals(record))validateAtom(line,i+1,serials,issues,location);
            else if(Set.of("TER","END").contains(record)) { if(flexible)issues.add(error(PdbqtValidationCode.UNSUPPORTED_RECORD,"Record is not supported in flexible PDBQT.",location)); }
            else if(FLEX_RECORDS.contains(record)){if(!flexible)issues.add(error(PdbqtValidationCode.UNSUPPORTED_RECORD,"Flexible record found in rigid PDBQT.",location));}
            else issues.add(error(PdbqtValidationCode.UNSUPPORTED_RECORD,"Unsupported PDBQT record: "+record,location));
        }
        return new PdbqtValidationReport(issues);
    }

    Set<String> atomKeys(List<String> lines){Set<String>keys=new HashSet<>();for(int i=0;i<lines.size();i++){String line=lines.get(i);if(line.startsWith("ATOM")||line.startsWith("HETATM")){try{PdbqtAtom atom=atomParser.parse(line,i+1);keys.add(atom.residueName()+":"+atom.chainId()+":"+atom.residueNumber()+":"+atom.atomName());}catch(PdbqtFormatException ignored){}}}return keys;}

    private void validateAtom(String line,int lineNumber,Set<Integer>serials,List<PdbqtValidationIssue>issues,String location){
        try {
            PdbqtAtom atom=atomParser.parse(line,lineNumber);
            if(!serials.add(atom.serial()))issues.add(error(PdbqtValidationCode.DUPLICATE_SERIAL,"Atom serial is duplicated.",location));
            if(!Double.isFinite(atom.x())||!Double.isFinite(atom.y())||!Double.isFinite(atom.z()))issues.add(error(PdbqtValidationCode.INVALID_COORDINATE,"Coordinates are malformed or non-finite.",location));
            if(!Double.isFinite(atom.partialCharge()))issues.add(error(PdbqtValidationCode.INVALID_CHARGE,"Charge is malformed or non-finite.",location));
            if(!PdbqtTypes.isSupported(atom.autodockType()))issues.add(error(PdbqtValidationCode.MISSING_AD4_TYPE,"AD4 atom type is missing or unsupported.",location));
        } catch (PdbqtFormatException e) {
            issues.add(error(PdbqtValidationCode.MALFORMED_RECORD,e.getMessage(),location));
        }
    }
    private PdbqtValidationIssue error(PdbqtValidationCode code,String message,String location){return new PdbqtValidationIssue(PdbqtValidationSeverity.ERROR,code,message,location);}
}
