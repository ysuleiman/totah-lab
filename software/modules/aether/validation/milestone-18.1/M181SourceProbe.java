package totah.lab.athena.fragment.quantum;
import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.gaia.structure.*;
/** Isolated read-only probe of frozen M18, with no public API changes. */
public final class M181SourceProbe {
 public static void main(String[] args)throws Exception {
  Path out=Path.of(args[0]); Files.createDirectories(out);
  var method=M18InterfaceProbe.class.getDeclaredMethod("mettl7",String.class,Path.class);method.setAccessible(true);
  for(String s:List.of("7A","7B")) {
   var env=(QuantumEnvironment)method.invoke(null,s,out);
   var result=new TreeMap<String,Object>();
   for(String component:List.of("receptor","ligand")) {
    var structure=component.equals("receptor")?env.state().receptor():env.state().ligand();
    var rows=new ArrayList<Map<String,Object>>();
    for(var chain:structure.getChains())for(var residue:chain.residues())for(var a:residue.getAtoms()) {
     var ref=new AtomReference(chain.id(),residue.getNumber(),residue.getInsertionCode()==null?' ':residue.getInsertionCode(),a.getName());
     rows.add(Map.of("reference",ref,"residue",residue.getName(),"serial",a.getPdbSerial(),"element",a.getElement().name(),"xyz",a.getPosition(),"partial_charge",a.getCharge()));
    }
    result.put(component,Map.of("atoms",rows,"bonds",structure.getBonds().stream().sorted(Comparator.comparing(Bond::toString)).toList(),"connectivity",structure.getConnectivityMetadata().toString(),"structure_hash",QuantumEnvironment.structureHash(structure)));
   }
   FragmentFeatureJson.write(out.resolve(s+"-source.json"),new ObjectMapper().enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writerWithDefaultPrettyPrinter().writeValueAsString(result));
   var selection=FragmentSelection.select(env);
   FragmentFeatureJson.write(out.resolve(s+"-selection.json"),FragmentFeatureJson.selection(selection));
   var plans=FragmentCalculationPlan.pairs(env,selection);
   for(int i=0;i<plans.size();i++) {
    var row=FragmentFeatureService.calculate(plans.get(i),out.resolve("UNUSED_CACHE"),x->{throw new AssertionError("Uncertified source entered SCF");});
    FragmentFeatureJson.write(out.resolve(s+String.format(Locale.ROOT,"-feature-%03d.json",i)),FragmentFeatureJson.feature(row));
   }
  }
 }
}
