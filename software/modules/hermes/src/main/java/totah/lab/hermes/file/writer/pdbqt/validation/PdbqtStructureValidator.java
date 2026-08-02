package totah.lab.hermes.file.writer.pdbqt.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PdbqtStructureValidator {
    private static final Set<String> TYPES=Set.of("C","A","N","NA","O","OA","S","SA","P","HD","H","F","Cl","Br","I","Mg","Mn","Fe","Zn","Ca");
    private static final Set<String> FLEX_RECORDS=Set.of("BEGIN_RES","END_RES","ROOT","ENDROOT","BRANCH","ENDBRANCH","TORSDOF");

    public PdbqtValidationReport validate(List<String> lines, boolean flexible) {
        List<PdbqtValidationIssue> issues=new ArrayList<>(); Set<Integer> serials=new HashSet<>();
        if(lines==null||lines.isEmpty())issues.add(error(PdbqtValidationCode.FILE_EMPTY,"PDBQT file is empty.","file"));
        int residueBlock=0;
        for(int i=0;lines!=null&&i<lines.size();i++){
            String line=lines.get(i), location="line "+(i+1); if(line.isBlank())continue;
            String record=line.trim().split("\\s+")[0];
            if("BEGIN_RES".equals(record)){residueBlock++;serials.clear();}
            if("ATOM".equals(record)||"HETATM".equals(record))validateAtom(line,serials,issues,location);
            else if(Set.of("TER","END").contains(record)) { if(flexible)issues.add(error(PdbqtValidationCode.UNSUPPORTED_RECORD,"Record is not supported in flexible PDBQT.",location)); }
            else if(FLEX_RECORDS.contains(record)){if(!flexible)issues.add(error(PdbqtValidationCode.UNSUPPORTED_RECORD,"Flexible record found in rigid PDBQT.",location));}
            else issues.add(error(PdbqtValidationCode.UNSUPPORTED_RECORD,"Unsupported PDBQT record: "+record,location));
        }
        return new PdbqtValidationReport(issues);
    }

    Set<String> atomKeys(List<String> lines){Set<String>keys=new HashSet<>();for(String line:lines)if(line.startsWith("ATOM")||line.startsWith("HETATM")){String[]p=line.trim().split("\\s+");if(p.length>=6)keys.add(p[3]+":"+p[4]+":"+p[5]+":"+p[2]);}return keys;}

    private void validateAtom(String line,Set<Integer>serials,List<PdbqtValidationIssue>issues,String location){
        String[]p=line.trim().split("\\s+");
        if(p.length<12){issues.add(error(PdbqtValidationCode.MALFORMED_RECORD,"ATOM/HETATM record has too few fields.",location));return;}
        try{int serial=Integer.parseInt(p[1]);if(!serials.add(serial))issues.add(error(PdbqtValidationCode.DUPLICATE_SERIAL,"Atom serial is duplicated.",location));}catch(NumberFormatException e){issues.add(error(PdbqtValidationCode.INVALID_SERIAL,"Atom serial is not numeric.",location));}
        try{int coordinateStart=p.length-7;for(int j=coordinateStart;j<coordinateStart+3;j++)if(!Double.isFinite(Double.parseDouble(p[j])))throw new NumberFormatException();}
        catch(RuntimeException e){issues.add(error(PdbqtValidationCode.INVALID_COORDINATE,"Coordinates are malformed or non-finite.",location));}
        try{if(!Double.isFinite(Double.parseDouble(p[p.length-2])))throw new NumberFormatException();}
        catch(RuntimeException e){issues.add(error(PdbqtValidationCode.INVALID_CHARGE,"Charge is malformed or non-finite.",location));}
        if(!TYPES.contains(p[p.length-1]))issues.add(error(PdbqtValidationCode.MISSING_AD4_TYPE,"AD4 atom type is missing or unsupported.",location));
    }
    private PdbqtValidationIssue error(PdbqtValidationCode code,String message,String location){return new PdbqtValidationIssue(PdbqtValidationSeverity.ERROR,code,message,location);}
}
