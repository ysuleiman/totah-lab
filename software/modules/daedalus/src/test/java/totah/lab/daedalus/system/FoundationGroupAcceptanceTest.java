package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.SystemStateView;
import totah.lab.athena.system.rules.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;

/** Hand-authored constitutional/state oracles; the matcher does not generate expected chemistry. */
class FoundationGroupAcceptanceTest {
    static final List<String> GROUPS=List.of("CARBOXYLIC_ACID.FORMYL","CARBOXYLATE.FORMYL","ALCOHOL","PHENOL","ETHER","CARBOXYLIC_ACID","CARBOXYLATE","THIOETHER","SULFOXIDE","HALOGENATED","AMINE.PRIMARY_ALIPHATIC","AMINE.SECONDARY_ALIPHATIC","AMINE.TERTIARY_ALIPHATIC");
    static RuleManifest candidate(String group)throws Exception {
        var path=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/groups-foundation-v1/ATHENA.GROUP."+group+".rule.json");
        var node=(ObjectNode)JSON.readTree(Files.readAllBytes(path));node.put("qualification","QUALIFIED"); // Only the fixture under test; production candidates remain NOT_EVALUATED.
        return RuleRegistry.decode(SystemStateView.bytes(node));
    }
    static Fixture graph(String elements,String edges,String counts,Map<Integer,Integer> charges,Set<Integer> aromatic) {
        var es=elements.split(" ");var hs=counts.split(" ");var atoms=new ArrayList<MolecularGraph.Atom>();var bonds=new ArrayList<MolecularGraph.Bond>();var hydrogens=new TreeMap<String,Integer>();
        for(int i=0;i<es.length;i++){atoms.add(atom("a"+i,es[i],charges.getOrDefault(i,0),aromatic.contains(i),i,0,0));hydrogens.put("a"+i,Integer.parseInt(hs[i]));}
        for(String e:edges.split(" ")){var parts=e.split("[-=:]");String a="a"+parts[0],b="a"+parts[1];bonds.add(bond(a,b,e.contains(":")?MolecularGraph.BondOrder.AROMATIC:e.contains("=")?MolecularGraph.BondOrder.DOUBLE:MolecularGraph.BondOrder.SINGLE));}
        return new Fixture(new MolecularGraph(atoms,bonds,Map.of("fixture","foundation-synthetic")),hydrogens);
    }
    static Fixture positiveFixture(String group){return switch(group){
        case "CARBOXYLIC_ACID.FORMYL"->graph("C O O","0=1 0-2","1 0 1",Map.of(),Set.of());
        case "CARBOXYLATE.FORMYL"->graph("C O O","0=1 0-2","1 0 0",Map.of(2,-1),Set.of());
        case "ALCOHOL"->fixture("methanol");
        case "PHENOL"->graph("C C C C C C O","0:1 1:2 2:3 3:4 4:5 5:0 5-6","1 1 1 1 1 0 1",Map.of(),Set.of(0,1,2,3,4,5));
        case "ETHER"->graph("C O C","0-1 1-2","3 0 3",Map.of(),Set.of());
        case "CARBOXYLIC_ACID"->fixture("acid");case "CARBOXYLATE"->fixture("carboxylate");
        case "THIOETHER"->graph("C S C","0-1 1-2","3 0 3",Map.of(),Set.of());
        case "SULFOXIDE"->graph("C S O C","0-1 1=2 1-3","3 0 0 3",Map.of(),Set.of());
        case "HALOGENATED"->graph("C Cl","0-1","3 0",Map.of(),Set.of());
        case "AMINE.PRIMARY_ALIPHATIC"->fixture("amine");
        case "AMINE.SECONDARY_ALIPHATIC"->graph("C N C","0-1 1-2","3 1 3",Map.of(),Set.of());
        case "AMINE.TERTIARY_ALIPHATIC"->graph("C N C C","0-1 1-2 1-3","3 0 3 3",Map.of(),Set.of());
        default->throw new IllegalArgumentException(group);
    };}
    static com.fasterxml.jackson.databind.JsonNode evaluate(String group,Fixture f)throws Exception {var s=system(List.of(f.graph()),true,false);return report(candidate(group),s,coverage(s,f));}
    @TestFactory Stream<DynamicTest> identitiesStateSymmetryAndReplay(){
        return GROUPS.stream().flatMap(group->Stream.of("positive","explicit","double","permuted","unknownCharge","unknownH").map(kind->DynamicTest.dynamicTest(group+"/"+kind,()->{
            var f=positiveFixture(group);if(kind.equals("explicit"))f=explicit(f);if(kind.equals("double"))f=doubled(f);
            if(kind.equals("permuted")){var a=new ArrayList<>(f.graph().atoms());var b=new ArrayList<>(f.graph().bonds());Collections.reverse(a);Collections.reverse(b);f=new Fixture(new MolecularGraph(a,b,f.graph().properties()),f.hydrogens());}
            var s=system(List.of(f.graph()),true,false);var c=coverage(s,f);
            if(kind.equals("unknownCharge"))c.get("atomState").forEach(n->((ObjectNode)n).put("chargeStatus","UNKNOWN_INCONCLUSIVE"));
            if(kind.equals("unknownH"))c.get("atomState").forEach(n->{((ObjectNode)n).put("hydrogenMode","UNKNOWN");((ObjectNode)n).putNull("implicitHydrogenCount");});
            byte[] before=SystemStateView.bytes(s.snapshot());var r=report(candidate(group),s,c);assertArrayEquals(before,SystemStateView.bytes(s.snapshot()));
            boolean inconclusive=kind.equals("unknownCharge")||kind.equals("unknownH")&&!group.equals("HALOGENATED");
            assertEquals(inconclusive?"UNKNOWN_INCONCLUSIVE":"SUPPORTED_PRESENT",r.get("assessment").asText(),r.toString());
            assertEquals(inconclusive?0:kind.equals("double")?2:1,r.get("occurrences").size());
            if(kind.equals("permuted")){var normal=evaluate(group,positiveFixture(group));var left=r.get("occurrences").deepCopy();var right=normal.get("occurrences").deepCopy();left.forEach(x->((ObjectNode)x).remove("occurrenceId"));right.forEach(x->((ObjectNode)x).remove("occurrenceId"));assertEquals(left,right);}
        })));
    }
    @TestFactory Stream<DynamicTest> constitutionalNearMisses(){
        var cases=List.of(new String[]{"ALCOHOL","ether"},new String[]{"ALCOHOL","phenol"},new String[]{"PHENOL","methanol"},new String[]{"ETHER","methanol"},
                new String[]{"CARBOXYLIC_ACID","carboxylate"},new String[]{"CARBOXYLIC_ACID","ester"},new String[]{"CARBOXYLATE","acid"},new String[]{"CARBOXYLATE","ester"},
                new String[]{"THIOETHER","thiol"},new String[]{"THIOETHER","sulfonium"},new String[]{"THIOETHER","sulfoxide"},new String[]{"SULFOXIDE","thioether"},
                new String[]{"AMINE.PRIMARY_ALIPHATIC","amide"},new String[]{"AMINE.PRIMARY_ALIPHATIC","secondary"},new String[]{"AMINE.SECONDARY_ALIPHATIC","amine"},
                new String[]{"AMINE.TERTIARY_ALIPHATIC","amine"},new String[]{"HALOGENATED","ethane"});
        return cases.stream().map(c->DynamicTest.dynamicTest(c[0]+" vs "+c[1],()->{
            var f=switch(c[1]){case "ether"->positiveFixture("ETHER");case "phenol"->positiveFixture("PHENOL");case "sulfoxide"->positiveFixture("SULFOXIDE");case "thioether"->positiveFixture("THIOETHER");case "secondary"->positiveFixture("AMINE.SECONDARY_ALIPHATIC");default->fixture(c[1]);};
            var r=evaluate(c[0],f);assertEquals("ABSENT_FALSE",r.get("assessment").asText(),r.toString());assertTrue(r.get("occurrences").isEmpty());
        }));
    }
    @Test void esterKeepsSpecificAcylContextWhenEtherIdentityAlsoMatches()throws Exception {
        var f=fixture("ester");var ether=evaluate("ETHER",f);assertEquals("SUPPORTED_PRESENT",ether.get("assessment").asText());
        assertTrue(ether.get("occurrences").get(0).get("contextAtomIds").toString().contains("a1"));assertEquals("SUPPORTED_PRESENT",report("ESTER",f).get("assessment").asText());
    }
    @Test void chargeSeparatedSulfoxideIsNotNormalized()throws Exception {
        var f=graph("C S O C","0-1 1-2 1-3","3 0 0 3",Map.of(1,1,2,-1),Set.of());assertEquals("UNSUPPORTED",evaluate("SULFOXIDE",f).get("assessment").asText());
    }
    @Test void thiopheneIsNotThioether()throws Exception {
        var f=graph("S C C C C","0:1 1:2 2:3 3:4 4:0","0 1 1 1 1",Map.of(),Set.of(0,1,2,3,4));assertEquals("ABSENT_FALSE",evaluate("THIOETHER",f).get("assessment").asText());
    }
    @TestFactory Stream<DynamicTest> halogenElementsAndDistinctOccurrences(){return Stream.of("F","Cl","Br","I").map(x->DynamicTest.dynamicTest(x,()->{
        assertEquals("SUPPORTED_PRESENT",evaluate("HALOGENATED",graph("C "+x,"0-1","3 0",Map.of(),Set.of())).get("assessment").asText());
        var tri=graph("C "+x+" "+x+" "+x,"0-1 0-2 0-3","1 0 0 0",Map.of(),Set.of());assertEquals(3,evaluate("HALOGENATED",tri).get("occurrences").size());
    }));}
    @Test void carbonicAcidIsNotCarboxylicAcid()throws Exception {
        var carbonic=graph("C O O O","0=1 0-2 0-3","0 0 1 1",Map.of(),Set.of());assertEquals("ABSENT_FALSE",evaluate("CARBOXYLIC_ACID",carbonic).get("assessment").asText());
        var bicarbonate=graph("C O O O","0=1 0-2 0-3","0 0 1 0",Map.of(3,-1),Set.of());assertEquals("ABSENT_FALSE",evaluate("CARBOXYLATE",bicarbonate).get("assessment").asText());
    }
    @Test void componentExclusionCannotStandInForLocalAmineContext()throws Exception {
        var mixed=graph("N C C C O N","0-1 1-2 2-3 3=4 3-5","2 2 2 0 0 2",Map.of(),Set.of());
        assertEquals(1,evaluate("AMINE.PRIMARY_ALIPHATIC",mixed).get("occurrences").size());
        var candidate=changedDefinition(candidate("AMINE.PRIMARY_ALIPHATIC"),d->{
            d.put("query","[#6]-[N;X3;H2;+0]");
            ((ObjectNode)d.get("supportedDomain")).putArray("unsupportedQueries").addObject().put("query","[N]-[C](=O)").put("reason","whole-component amide exclusion witness");
        });
        var state=system(List.of(mixed.graph()),true,false);var result=report(candidate,state,coverage(state,mixed));
        assertEquals("UNSUPPORTED",result.get("assessment").asText());assertTrue(result.get("occurrences").isEmpty());
        assertEquals(6,result.get("sourceGraph").get("atoms").size());
    }
    @Test void unknownAromaticModelCannotEstablishPhenol()throws Exception {
        var f=positiveFixture("PHENOL");var state=system(List.of(f.graph()),true,false);var c=coverage(state,f);
        c.get("atomState").forEach(n->((ObjectNode)n).put("aromaticityModel","UNKNOWN"));
        assertEquals("UNKNOWN_INCONCLUSIVE",report(candidate("PHENOL"),state,c).get("assessment").asText());
    }
    @Test void sulfoxideSourceParityIsPreservedWithoutAssigningAbsoluteStereo()throws Exception {
        var f=graph("C S O C C","0-1 1=2 1-3 3-4","3 0 0 2 3",Map.of(),Set.of());
        for(var parity:List.of("PARITY_1","PARITY_2")){
            var atoms=f.graph().atoms().stream().map(a->new MolecularGraph.Atom(a.id(),a.element(),a.isotope(),a.formalCharge(),a.explicitHydrogens(),a.aromatic(),a.id().equals("a1")?parity:a.stereochemistry(),a.coordinates(),a.properties())).toList();
            var alternate=new Fixture(new MolecularGraph(atoms,f.graph().bonds(),f.graph().properties()),f.hydrogens());
            var result=evaluate("SULFOXIDE",alternate);assertEquals("SUPPORTED_PRESENT",result.get("assessment").asText());
            assertEquals(parity,result.get("sourceGraph").get("atoms").get(1).get("stereochemistry").asText());
        }
    }
    @Test void etherIncludesEpoxideAndAnisoleButNotFuranOxygen()throws Exception {
        var epoxide=graph("C O C","0-1 1-2 2-0","2 0 2",Map.of(),Set.of());assertEquals("SUPPORTED_PRESENT",evaluate("ETHER",epoxide).get("assessment").asText());
        var anisole=graph("C C C C C C O C","0:1 1:2 2:3 3:4 4:5 5:0 5-6 6-7","1 1 1 1 1 0 0 3",Map.of(),Set.of(0,1,2,3,4,5));assertEquals("SUPPORTED_PRESENT",evaluate("ETHER",anisole).get("assessment").asText());
        var furan=graph("O C C C C","0:1 1:2 2:3 3:4 4:0","0 1 1 1 1",Map.of(),Set.of(0,1,2,3,4));assertEquals("ABSENT_FALSE",evaluate("ETHER",furan).get("assessment").asText());
    }
    public static void main(String[] args)throws Exception {var results=new ArrayList<Object>();for(String group:GROUPS)results.add(evaluate(group,positiveFixture(group)));Files.write(Path.of(args[0]),SystemStateView.bytes(results));}
}
