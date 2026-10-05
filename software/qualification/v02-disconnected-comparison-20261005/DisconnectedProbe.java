package totah.lab.daedalus.system;
import java.util.*;
import java.nio.file.*;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.design.backend.ocl.OclMolecularBackend;
import totah.lab.athena.system.SystemStateView;
public class DisconnectedProbe {
 public static void main(String[] args)throws Exception {
  var graph=new MolecularGraph(List.of(
   new MolecularGraph.Atom("sodium","Na",null,1,0,false,"UNSPECIFIED",new MolecularGraph.Coordinates(0,0,0),Map.of()),
   new MolecularGraph.Atom("chloride","Cl",null,-1,0,false,"UNSPECIFIED",new MolecularGraph.Coordinates(5,0,0),Map.of())),List.of(),Map.of("fixture","supplied disconnected salt; no generated counterion"));
  var result=new TreeMap<String,Object>();
  for(var entry:Map.of("legacy",new OclMolecularBackend(),"corrected",OclMolecularBackend.forChemicalStateValidation()).entrySet()) {
   try {var r=entry.getValue().sanitize(graph,new MolecularSanitizer.SanitizationPolicy(Set.of(),true));result.put(entry.getKey(),Map.of("valid",r.valid(),"sourcePreserved",graph.equals(r.graph()),"evidence",r.evidence()));}
   catch(Exception e){result.put(entry.getKey(),Map.of("failure",e.toString()));}
  }
  result.put("dimensions",OclMolecularBackend.forChemicalStateValidation().validateDimensions(graph,MolecularValidationService.NeutralityPolicy.OBSERVE_ONLY));
  Files.write(Path.of(args[0]),SystemStateView.bytes(result));
 }
}
