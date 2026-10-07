import java.nio.file.*;
import java.util.*;
import totah.lab.athena.design.backend.ocl.OclMolecularBackend;
import totah.lab.athena.system.SystemStateView;
/** Research comparison of original feature OR branches; no runtime matcher or chemical authority. */
public class ReferenceUnionProbe {
 public static void main(String[] args)throws Exception {
  Path q=Path.of(args[0]);var backend=new OclMolecularBackend();var out=new ArrayList<Object>();
  for(var line:Files.readAllLines(q.resolve("REFERENCE_PANEL.tsv")).stream().skip(1).toList()) {
   var row=line.split("\t");var graph=backend.decodeStructure("SMILES",row[1]);var counts=new TreeMap<String,Set<String>>();var details=new TreeMap<String,Object>();
   for(var query:Files.readAllLines(q.resolve("bounded-queries.tsv"))) {var pair=query.split("\t");var result=backend.match(pair[1],graph);var sets=new TreeSet<String>();for(var match:result.queryToTargetAtomIds())sets.add(new TreeSet<>(match.values()).toString());String feature=pair[0].split("_")[0];counts.computeIfAbsent(feature,k->new TreeSet<>()).addAll(sets);details.put(pair[0],Map.of("targetSets",sets,"correspondences",result.queryToTargetAtomIds()));}
   var sizes=new TreeMap<String,Integer>();counts.forEach((k,v)->sizes.put(k,v.size()));out.add(Map.of("id",row[0],"matches",sizes,"branchDetails",details));
  }
  Files.write(q.resolve("OCL_BOUNDED_REFERENCE.json"),SystemStateView.bytes(out));
 }
}
