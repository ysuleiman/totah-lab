package totah.lab.daedalus.system;
import java.util.*;
import java.nio.file.*;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
/** Synthetic migration witness, no scientific system or production change. */
public class ConsumerImpactProbe {
 public static void main(String[] args)throws Exception {
  // Symmetric glyoxal connectivity; one carbonyl near the explicit methanol donor,
  // the other remote. Coordinates are deliberately synthetic, not energy-minimized.
  var g=new MolecularGraph(List.of(atom("O1","O",0,false,12,0,0),atom("C1","C",0,false,13.2,0,0),
   atom("C2","C",0,false,4,0,0),atom("O2","O",0,false,2.8,0,0)),
   List.of(bond("O1","C1",MolecularGraph.BondOrder.DOUBLE),bond("C1","C2",MolecularGraph.BondOrder.SINGLE),bond("C2","O2",MolecularGraph.BondOrder.DOUBLE)),Map.of("fixture","symmetric-carbonyl-consumer-witness"));
  var m=manifest(RuleRegistry.scientific(),"HBOND");
  var s=system(List.of(methanol(),g),true,false);
  var measures=AthenaScientificRules.collect(s,m,request(s,m,1000,false),OCL);
  var t=new AthenaScientificRulesAcceptanceTest();t.temp=Path.of(args[0]);Files.createDirectories(t.temp);
  var result=t.run(s,m,"consumer-gate",1000,false,Optional.empty());
  System.out.println("rule="+m.ruleId()+" profile="+m.profile()+" implementation="+m.implementationVersion());
  System.out.println("carbonylMatches="+OCL.match(m.parameters().get("pattern.carbonyl").value(),g).queryToTargetAtomIds());
  System.out.println("rawCandidates="+measures.raw().candidates());
  System.out.println("candidateSupported="+measures.candidateSupported());
  System.out.println("negativeCoverage="+measures.negativeCoverage());
  System.out.println("assessment="+t.assessment(result).status());
 }
}
