package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.athena.design.backend.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.system;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;
import static totah.lab.daedalus.system.FoundationGroupAcceptanceTest.graph;

/** Restricted state descriptions; no nonpolar energy or net-charge inference. */
class ChargeNonpolarAcceptanceTest {
    static RuleManifest candidate(String name)throws Exception {
        var p=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/perception-state-v1/ATHENA.PERCEPTION."+name+".rule.json");
        var m=(ObjectNode)JSON.readTree(Files.readAllBytes(p));assertEquals("NOT_EVALUATED",m.get("qualification").asText());m.put("qualification","QUALIFIED");return RuleRegistry.decode(SystemStateView.bytes(m));
    }
    static Fixture molecule(String name)throws Exception {return switch(name){
        case "ethane"->graph("C C","0-1","3 3",Map.of(),Set.of());
        case "propane"->graph("C C C","0-1 1-2","3 2 3",Map.of(),Set.of());
        case "isobutane"->graph("C C C C","0-1 0-2 0-3","1 3 3 3",Map.of(),Set.of());
        case "neopentane"->graph("C C C C C","0-1 0-2 0-3 0-4","0 3 3 3 3",Map.of(),Set.of());
        case "toluene"->graph("C C C C C C C","0:1 1:2 2:3 3:4 4:5 5:0 0-6","0 1 1 1 1 1 3",Map.of(),Set.of(0,1,2,3,4,5));
        case "nitrobenzene"->graph("C C C C C C N O O","0:1 1:2 2:3 3:4 4:5 5:0 0-6 6=7 6-8","0 1 1 1 1 1 0 0 0",Map.of(6,1,8,-1),Set.of(0,1,2,3,4,5));
        case "cf4"->graph("C F F F F","0-1 0-2 0-3 0-4","0 0 0 0 0",Map.of(),Set.of());
        default->ChemicalRoleAcceptanceTest.chemical(name);
    };}
    record Case(String rule,String fixture,int count) { }
    static List<Case> cases(){return List.of(
        new Case("FORMAL_CHARGE.PLUS_ONE","methylammonium",1),new Case("FORMAL_CHARGE.MINUS_ONE","nitrobenzene",1),new Case("FORMAL_CHARGE.PLUS_ONE","nitrobenzene",1),
        new Case("NONPOLAR.SATURATED_C_H3","ethane",2),new Case("NONPOLAR.SATURATED_C_H2","propane",1),new Case("NONPOLAR.SATURATED_C_H1","isobutane",1),new Case("NONPOLAR.SATURATED_C_H0","neopentane",1),
        new Case("NONPOLAR.AROMATIC_C_H1","benzene",6),new Case("NONPOLAR.AROMATIC_C_H0","toluene",1));}
    static JsonNode run(String rule,Fixture f)throws Exception{var s=system(List.of(f.graph()),true,false);return report(candidate(rule),s,coverage(s,f));}
    @TestFactory Stream<DynamicTest> stateCorrespondenceMatrix(){return cases().stream().flatMap(c->Stream.of("normal","explicitH","double","permuted","unknownCharge","unknownH","incomplete").map(kind->DynamicTest.dynamicTest(c.rule()+"/"+c.fixture()+"/"+kind,()->{
        var f=molecule(c.fixture());if(kind.equals("explicitH"))f=explicit(f);if(kind.equals("double"))f=doubled(f);if(kind.equals("permuted")){var atoms=new ArrayList<>(f.graph().atoms());var bonds=new ArrayList<>(f.graph().bonds());Collections.reverse(atoms);Collections.reverse(bonds);f=new Fixture(new MolecularGraph(atoms,bonds,f.graph().properties()),f.hydrogens());}
        var s=system(List.of(f.graph()),true,false);var coverage=coverage(s,f);var before=SystemStateView.bytes(s.snapshot());
        if(kind.equals("unknownCharge"))coverage.get("atomState").forEach(a->((ObjectNode)a).put("chargeStatus","UNKNOWN_INCONCLUSIVE"));
        if(kind.equals("unknownH"))coverage.get("atomState").forEach(a->{((ObjectNode)a).put("hydrogenMode","UNKNOWN");((ObjectNode)a).putNull("implicitHydrogenCount");});
        if(kind.equals("incomplete"))coverage.put("completeGraph","UNKNOWN_INCONCLUSIVE");
        var result=report(candidate(c.rule()),s,coverage);boolean unknown=kind.equals("unknownCharge")||kind.equals("incomplete")||kind.equals("unknownH")&&c.rule().startsWith("NONPOLAR");
        assertEquals(unknown?"UNKNOWN_INCONCLUSIVE":"SUPPORTED_PRESENT",result.get("assessment").asText(),result.toString());assertEquals(unknown?0:c.count()*(kind.equals("double")?2:1),result.get("occurrences").size());assertArrayEquals(before,SystemStateView.bytes(s.snapshot()));
        if(kind.equals("permuted"))assertEquals(normalized(run(c.rule(),molecule(c.fixture()))),normalized(result));
    })));}
    @TestFactory Stream<DynamicTest> constitutionalExclusions(){return Stream.of(
        new Case("NONPOLAR.SATURATED_C_H3","methanol",0),new Case("NONPOLAR.SATURATED_C_H0","cf4",0),
        new Case("NONPOLAR.AROMATIC_C_H0","nitrobenzene",0),new Case("NONPOLAR.AROMATIC_C_H1","nitrobenzene",5),
        new Case("FORMAL_CHARGE.PLUS_ONE","methylamine",0),new Case("FORMAL_CHARGE.MINUS_ONE","methanol",0),
        new Case("NONPOLAR.SATURATED_C_H3","benzene",0),new Case("NONPOLAR.AROMATIC_C_H1","ethane",0)
    ).map(c->DynamicTest.dynamicTest(c.toString(),()->{var r=run(c.rule(),molecule(c.fixture()));assertEquals(c.count()==0?"ABSENT_FALSE":"SUPPORTED_PRESENT",r.get("assessment").asText(),r.toString());assertEquals(c.count(),r.get("occurrences").size());}));}
    @Test void chargeSeparationDoesNotBecomeNetCationOrAnion()throws Exception{var f=molecule("nitrobenzene");assertEquals(0,f.graph().atoms().stream().mapToInt(MolecularGraph.Atom::formalCharge).sum());var plus=run("FORMAL_CHARGE.PLUS_ONE",f);var minus=run("FORMAL_CHARGE.MINUS_ONE",f);assertEquals("a6",plus.get("occurrences").get(0).get("memberAtomIds").get(0).asText());assertEquals("a8",minus.get("occurrences").get(0).get("memberAtomIds").get(0).asText());}
    @Test void featureRegistryDoesNotWidenLegacyInteractionProfiles()throws Exception{assertTrue(RuleRegistry.scientific().manifests().values().stream().noneMatch(m->m.ruleId().startsWith("ATHENA.PERCEPTION.")));}
    public static void main(String[] args)throws Exception{var reports=new ArrayList<JsonNode>();for(var c:cases())reports.add(run(c.rule(),molecule(c.fixture())));Files.write(Path.of(args[0]),SystemStateView.bytes(reports));}
}
