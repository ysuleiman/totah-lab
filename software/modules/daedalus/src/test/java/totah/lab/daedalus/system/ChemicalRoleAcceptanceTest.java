package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.system.SystemStateView;
import totah.lab.athena.system.rules.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;

/** Reviewed state-specific role predicates, without interaction geometry or biological truth. */
class ChemicalRoleAcceptanceTest {
    static final Path RES=Path.of("software/modules/daedalus/src/test/resources/chemical-role-perception");
    static Fixture chemical(String name)throws Exception {
        var data=JSON.readTree(Files.readAllBytes(RES.resolve("fixtures.json"))).get(name);
        if(data==null)return FoundationVocabularyAcceptanceTest.molecule(name);
        var elements=data.get("elements").asText().split(" ");var counts=data.get("hydrogens").asText().split(" ");
        var aromatic=new HashSet<Integer>();data.path("aromatic").forEach(n->aromatic.add(n.intValue()));
        var atoms=new ArrayList<MolecularGraph.Atom>();var bonds=new ArrayList<MolecularGraph.Bond>();var hs=new TreeMap<String,Integer>();
        for(int i=0;i<elements.length;i++){atoms.add(atom("a"+i,elements[i],data.path("charges").path(Integer.toString(i)).asInt(0),aromatic.contains(i),i,0,0));hs.put("a"+i,Integer.parseInt(counts[i]));}
        for(String edge:data.get("edges").asText().split(" "))if(!edge.isBlank()){
            var ends=edge.split("[-=:#]");var order=edge.contains("#")?MolecularGraph.BondOrder.TRIPLE:edge.contains(":")?MolecularGraph.BondOrder.AROMATIC:edge.contains("=")?MolecularGraph.BondOrder.DOUBLE:MolecularGraph.BondOrder.SINGLE;
            bonds.add(bond("a"+ends[0],"a"+ends[1],order));
        }
        return new Fixture(new MolecularGraph(atoms,bonds,Map.of("fixture",name)),hs);
    }
    static RuleManifest candidate(String name)throws Exception {
        var p=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/perception-foundation-v1/ATHENA.PERCEPTION."+name+".rule.json");
        var n=(ObjectNode)JSON.readTree(Files.readAllBytes(p));assertEquals("NOT_EVALUATED",n.get("qualification").asText());n.put("qualification","QUALIFIED");return RuleRegistry.decode(SystemStateView.bytes(n));
    }
    static List<String[]> cases()throws Exception{return Files.readAllLines(RES.resolve("cases.tsv")).stream().filter(x->!x.startsWith("#")&&!x.isBlank()).map(x->x.split("\\|")).toList();}
    static JsonNode run(String name,Fixture f)throws Exception {var s=system(List.of(f.graph()),true,false);var before=SystemStateView.bytes(s.snapshot());var r=report(candidate(name),s,coverage(s,f));assertArrayEquals(before,SystemStateView.bytes(s.snapshot()));return r;}
    @TestFactory Stream<DynamicTest> sourceStateRolesAndCorrespondence()throws Exception {
        return cases().stream().flatMap(row->Stream.of("positive","explicit","double","permuted","unknownH","unknownCharge","incomplete").map(kind->DynamicTest.dynamicTest(row[0]+"/"+kind,()->{
            var f=chemical(row[1]);if(kind.equals("explicit"))f=explicit(f);if(kind.equals("double"))f=doubled(f);
            if(kind.equals("permuted")){var a=new ArrayList<>(f.graph().atoms());var b=new ArrayList<>(f.graph().bonds());Collections.reverse(a);Collections.reverse(b);f=new Fixture(new MolecularGraph(a,b,f.graph().properties()),f.hydrogens());}
            var s=system(List.of(f.graph()),true,false);var c=coverage(s,f);var m=candidate(row[0]);var d=JSON.readTree(m.parameters().get("definition").value());
            if(kind.equals("unknownH"))c.get("atomState").forEach(n->{((ObjectNode)n).put("hydrogenMode","UNKNOWN");((ObjectNode)n).putNull("implicitHydrogenCount");});
            if(kind.equals("unknownCharge"))c.get("atomState").forEach(n->((ObjectNode)n).put("chargeStatus","UNKNOWN_INCONCLUSIVE"));
            if(kind.equals("incomplete"))c.put("completeGraph","UNKNOWN_INCONCLUSIVE");
            var r=report(m,s,c);boolean unknown=kind.equals("unknownCharge")||kind.equals("incomplete")||(kind.equals("unknownH")&&!d.get("requiredState").get("hydrogenRoles").isEmpty());
            assertEquals(unknown?"UNKNOWN_INCONCLUSIVE":"SUPPORTED_PRESENT",r.get("assessment").asText(),r.toString());
            assertEquals(unknown?0:kind.equals("double")?2:1,r.get("occurrences").size());
            if(!unknown){assertEquals(row[2],r.get("occurrences").get(0).get("memberAtomIds").get(0).asText());assertEquals(1,r.get("occurrences").get(0).get("memberAtomIds").size());}
            if(kind.equals("permuted")){var normal=run(row[0],chemical(row[1]));assertEquals(normalized(normal),normalized(r));assertEquals(normal.get("contextAssessments"),r.get("contextAssessments"));}
            if(kind.equals("positive")&&row[0].startsWith("DONOR"))assertTrue(r.get("occurrences").get(0).get("explicitHydrogenAtomIds").isEmpty(),"implicit H never becomes invented D-H coordinates");
            if(kind.equals("explicit")&&row[0].startsWith("DONOR"))assertFalse(r.get("occurrences").get(0).get("explicitHydrogenAtomIds").isEmpty());
        })));
    }
    @TestFactory Stream<DynamicTest> excludedClassesAreNotUniversalAbsence()throws Exception {
        return cases().stream().flatMap(row->Arrays.stream(row[3].split(",")).map(name->DynamicTest.dynamicTest(row[0]+" vs "+name,()->{
            var r=run(row[0],chemical(name));assertEquals("ABSENT_FALSE",r.get("assessment").asText(),r.toString());
        })));
    }
    @Test void amideDonorAndCarbonylAcceptorCoexistAtDifferentAtoms()throws Exception {
        var f=chemical("acetamide");var donor=run("DONOR.AMIDE_NH2",f);var acceptor=run("ACCEPTOR.CARBONYL_O",f);
        assertEquals("a3",donor.get("occurrences").get(0).get("memberAtomIds").get(0).asText());assertEquals("a2",acceptor.get("occurrences").get(0).get("memberAtomIds").get(0).asText());
        assertEquals("ABSENT_FALSE",run("ACCEPTOR.AMINE_PRIMARY",f).get("assessment").asText());
    }
    @Test void legacyProfilesAreNotWidenedOrReplaced()throws Exception {
        assertEquals("2",RuleRegistry.scientific().manifests().values().stream().filter(m->m.ruleId().equals("ATHENA.HBOND.DIRECTIONAL")).findFirst().orElseThrow().implementationVersion());
        assertTrue(RuleRegistry.scientific().manifests().values().stream().noneMatch(m->m.ruleId().startsWith("ATHENA.PERCEPTION.")));
    }
    @Test void mixedContextsKeepIndependentRoleIdentities()throws Exception {
        var f=GroupContextAcceptanceTest.mixed();
        var amine=run("ACCEPTOR.AMINE_PRIMARY",f);assertEquals(1,amine.get("occurrences").size());assertEquals("a0",amine.get("occurrences").get(0).get("memberAtomIds").get(0).asText());
        var amide=run("DONOR.AMIDE_NH2",f);assertEquals(1,amide.get("occurrences").size());assertEquals("a5",amide.get("occurrences").get(0).get("memberAtomIds").get(0).asText());
    }
    @Test void contradictoryAcceptorHydrogenStateIsNotIgnored()throws Exception {
        var f=chemical("acetamide");var s=system(List.of(f.graph()),true,false);var c=coverage(s,f);
        ((ObjectNode)c.get("atomState").get("a2")).put("implicitHydrogenCount",1);
        assertEquals("UNKNOWN_INCONCLUSIVE",report(candidate("ACCEPTOR.CARBONYL_O"),s,c).get("assessment").asText());
    }
    @TestFactory Stream<DynamicTest> unmatchedUnknownStateIsNotNegative()throws Exception {
        return cases().stream().map(row->DynamicTest.dynamicTest(row[0]+" unknown negative",()->{
            var f=chemical(row[3].split(",")[0]);var s=system(List.of(f.graph()),true,false);var c=coverage(s,f);
            c.get("atomState").forEach(n->{((ObjectNode)n).put("hydrogenMode","UNKNOWN");((ObjectNode)n).putNull("implicitHydrogenCount");});
            var r=report(candidate(row[0]),s,c);
            var d=JSON.readTree(candidate(row[0]).parameters().get("definition").value());
            var required=new HashSet<String>();d.get("requiredState").get("hydrogenElements").forEach(n->required.add(n.asText()));
            if(f.graph().atoms().stream().anyMatch(atom->required.contains(atom.element())))assertEquals("UNKNOWN_INCONCLUSIVE",r.get("assessment").asText());
        }));
    }
    public static void main(String[] args)throws Exception {var results=new ArrayList<JsonNode>();for(var row:cases())results.add(run(row[0],chemical(row[1])));Files.write(Path.of(args[0]),SystemStateView.bytes(results));}
}
