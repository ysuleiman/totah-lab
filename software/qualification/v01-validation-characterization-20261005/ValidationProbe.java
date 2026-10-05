package totah.lab.daedalus.system;
import java.nio.file.*;
import java.util.*;
import totah.lab.athena.system.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.BACKEND;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.system;
/** Read-only characterization; no change to historical validation semantics. */
public class ValidationProbe {
 public static void main(String[] args)throws Exception {
  var results=new TreeMap<String,Object>();
  for(var name:List.of("methylamine","methylammonium","acetamide","pyridine","pyridinium")) {
   var f=ChemicalRoleAcceptanceTest.chemical(name);var s=system(List.of(f.graph()),true,false);
   var before=SystemStateView.bytes(s.snapshot());
   var record=new TreeMap<String,Object>();
   record.put("systemValidation",new SystemGraphValidation(BACKEND,BACKEND,BACKEND).validate(s));
   for(var operation:List.of("sanitize","stereo")) {
    try {
     if(operation.equals("sanitize"))BACKEND.sanitize(f.graph(),new totah.lab.athena.design.backend.MolecularSanitizer.SanitizationPolicy(Set.of(),true));
     else BACKEND.validate(f.graph());
     record.put(operation,List.of("PASS"));
    } catch(Exception e) {
     var causes=new ArrayList<String>();for(Throwable t=e;t!=null;t=t.getCause())causes.add(t.getClass().getName()+": "+t.getMessage());
     record.put(operation,causes);
    }
   }
   results.put(name,record);
   if(!Arrays.equals(before,SystemStateView.bytes(s.snapshot())))throw new AssertionError("state mutated");
  }
  Files.write(Path.of(args[0]),SystemStateView.bytes(results));
 }
}
