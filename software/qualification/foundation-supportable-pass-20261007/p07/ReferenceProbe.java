import java.nio.file.*;
import java.util.*;
import totah.lab.athena.design.backend.ocl.OclMolecularBackend;
/** Compare exact FDef query transfer through existing B00; not a new matcher or classifier. */
public class ReferenceProbe {
 public static void main(String[] args)throws Exception {
  Path q=Path.of(args[0]);var backend=new OclMolecularBackend();var out=new ArrayList<String>();out.add("id\tfeature\tmatches\tstatus");
  for(var line:Files.readAllLines(q.resolve("REFERENCE_PANEL.tsv")).stream().skip(1).toList()) {
   var row=line.split("\t");
   for(var query:Files.readAllLines(q.resolve(args.length>1?args[1]+"-queries.tsv":"queries.tsv"))) {var pair=query.split("\t");try {var graph=backend.decodeStructure("SMILES",row[1]);var result=backend.match(pair[1],graph);out.add(row[0]+"\t"+pair[0]+"\t"+result.queryToTargetAtomIds().size()+"\tOK");}catch(Exception e){out.add(row[0]+"\t"+pair[0]+"\t\t"+e.getClass().getSimpleName()+":"+e.getMessage());}}
  }
  Files.write(q.resolve(args.length>1?"OCL_"+args[1].toUpperCase()+"_REFERENCE.tsv":"OCL_REFERENCE.tsv"),out);
 }
}
