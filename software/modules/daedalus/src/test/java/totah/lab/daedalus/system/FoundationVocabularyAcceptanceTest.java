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

/** Exact hand-authored structural oracles; no docking or data-driven expected outcomes. */
class FoundationVocabularyAcceptanceTest {
    static final Path RES=Path.of("software/modules/daedalus/src/test/resources/foundation-vocabulary");
    static Fixture molecule(String name)throws Exception {
        var data=JSON.readTree(Files.readAllBytes(RES.resolve("fixtures.json"))).required(name);
        var elements=data.get("elements").asText().split(" ");var counts=data.get("hydrogens").asText().split(" ");
        var aromatic=new HashSet<Integer>();data.path("aromatic").forEach(n->aromatic.add(n.intValue()));
        var atoms=new ArrayList<MolecularGraph.Atom>();var bonds=new ArrayList<MolecularGraph.Bond>();var hs=new TreeMap<String,Integer>();
        for(int i=0;i<elements.length;i++){atoms.add(atom("a"+i,elements[i],data.path("charges").path(Integer.toString(i)).asInt(0),aromatic.contains(i),i,0,0));hs.put("a"+i,Integer.parseInt(counts[i]));}
        for(String edge:data.get("edges").asText().split(" ")){
            var ends=edge.split("[-=:#]");var order=edge.contains("#")?MolecularGraph.BondOrder.TRIPLE:edge.contains(":")?MolecularGraph.BondOrder.AROMATIC:edge.contains("=")?MolecularGraph.BondOrder.DOUBLE:MolecularGraph.BondOrder.SINGLE;
            bonds.add(bond("a"+ends[0],"a"+ends[1],order));
        }
        return new Fixture(new MolecularGraph(atoms,bonds,Map.of("fixture",name)),hs);
    }
    static RuleManifest candidate(String group)throws Exception {
        var p=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/groups-foundation-v2/ATHENA.GROUP."+group+".rule.json");
        var n=(ObjectNode)JSON.readTree(Files.readAllBytes(p));assertEquals("NOT_EVALUATED",n.get("qualification").asText());
        n.put("qualification","QUALIFIED");return RuleRegistry.decode(SystemStateView.bytes(n));
    }
    static List<String[]> cases()throws Exception{return Files.readAllLines(RES.resolve("cases.tsv")).stream().filter(x->!x.startsWith("#")&&!x.isBlank()).map(x->x.split("\\|")).toList();}
    static JsonNode run(String group,Fixture f)throws Exception {
        var state=system(List.of(f.graph()),true,false);var before=SystemStateView.bytes(state.snapshot());var r=report(candidate(group),state,coverage(state,f));
        assertArrayEquals(before,SystemStateView.bytes(state.snapshot()));return r;
    }
    @TestFactory Stream<DynamicTest> stateSymmetryAndReplay()throws Exception {
        return cases().stream().flatMap(row->Stream.of("positive","explicit","double","permuted","unknownH","unknownCharge","unknownAromatic").map(kind->DynamicTest.dynamicTest(row[0]+"/"+kind,()->{
            var f=molecule(row[1]);if(kind.equals("explicit"))f=explicit(f);if(kind.equals("double"))f=doubled(f);
            if(kind.equals("permuted")){var a=new ArrayList<>(f.graph().atoms());var b=new ArrayList<>(f.graph().bonds());Collections.reverse(a);Collections.reverse(b);f=new Fixture(new MolecularGraph(a,b,f.graph().properties()),f.hydrogens());}
            var state=system(List.of(f.graph()),true,false);var c=coverage(state,f);var m=candidate(row[0]);var d=JSON.readTree(m.parameters().get("definition").value());
            if(kind.equals("unknownH"))c.get("atomState").forEach(n->{((ObjectNode)n).put("hydrogenMode","UNKNOWN");((ObjectNode)n).putNull("implicitHydrogenCount");});
            if(kind.equals("unknownCharge"))c.get("atomState").forEach(n->((ObjectNode)n).put("chargeStatus","UNKNOWN_INCONCLUSIVE"));
            if(kind.equals("unknownAromatic"))c.get("atomState").forEach(n->((ObjectNode)n).put("aromaticityModel","UNKNOWN"));
            var r=report(m,state,c);boolean unknown=kind.equals("unknownCharge")||(kind.equals("unknownH")&&!d.get("requiredState").get("hydrogenRoles").isEmpty())||(kind.equals("unknownAromatic")&&d.get("requiredState").get("aromaticity").asBoolean());
            assertEquals(unknown?"UNKNOWN_INCONCLUSIVE":"SUPPORTED_PRESENT",r.get("assessment").asText(),r.toString());
            assertEquals(unknown?0:Integer.parseInt(row[2])*(kind.equals("double")?2:1),r.get("occurrences").size());
            if(kind.equals("permuted")){var normal=run(row[0],molecule(row[1]));assertEquals(normalized(normal),normalized(r));assertEquals(normal.get("contextAssessments"),r.get("contextAssessments"));}
        })));
    }
    @TestFactory Stream<DynamicTest> constitutionalAndStateNearMisses()throws Exception {
        return cases().stream().flatMap(row->Arrays.stream(row[3].split(",")).map(name->DynamicTest.dynamicTest(row[0]+" vs "+name,()->{
            var r=run(row[0],molecule(name));assertEquals("ABSENT_FALSE",r.get("assessment").asText(),r.toString());assertTrue(r.get("occurrences").isEmpty());
        })));
    }
    @Test void aromaticAttachmentIsAnAmineButAcylAndAmidinoAreNot()throws Exception {
        assertEquals(1,run("AMINE.PRIMARY_CARBON_BOUND",molecule("aniline")).get("occurrences").size());
        for(String name:List.of("acetamide","guanidine")){var r=run("AMINE.PRIMARY_CARBON_BOUND",molecule(name));assertTrue(r.get("occurrences").isEmpty());assertTrue(r.get("contextAssessments").size()>0);}
    }
    @Test void fusedAndNestedRingsCoexistWithoutInventingPlanarity()throws Exception {
        var n=run("AROMATIC.RING6",molecule("naphthalene"));assertEquals(2,n.get("occurrences").size());
        var left=new HashSet<String>();n.get("occurrences").get(0).get("memberAtomIds").forEach(x->left.add(x.asText()));
        var right=new HashSet<String>();n.get("occurrences").get(1).get("memberAtomIds").forEach(x->right.add(x.asText()));left.retainAll(right);assertEquals(2,left.size());
        assertEquals(1,run("AROMATIC.RING6",molecule("indole")).get("occurrences").size());assertEquals(1,run("HETEROAROMATIC.RING5",molecule("indole")).get("occurrences").size());
        assertTrue(run("HETEROAROMATIC.RING6",molecule("indole")).get("occurrences").isEmpty());
    }
    @Test void nitroResonanceDrawingsRemainDifferentSourceStates()throws Exception {
        var a=run("NITRO.CHARGE_SEPARATED",molecule("nitromethane"));var b=run("NITRO.CHARGE_SEPARATED",molecule("nitromethane_other_drawing"));
        assertEquals(normalized(a).get(0).get("memberAtomIds"),normalized(b).get(0).get("memberAtomIds"));
        assertNotEquals(a.get("sourceGraph"),b.get("sourceGraph"));assertNotEquals(a.get("occurrences").get(0).get("roleCorrespondenceAlternatives"),b.get("occurrences").get(0).get("roleCorrespondenceAlternatives"));
    }
    @Test void amideAndCarbonylOverlapIsPreserved()throws Exception {
        var f=molecule("N_methylacetamide");var r=run("AMIDE.NH1",f);assertEquals(1,r.get("occurrences").size());assertEquals("SUPPORTED_PRESENT",report("CARBONYL",f).get("assessment").asText());assertEquals("SUPPORTED_PRESENT",report("AMIDE",f).get("assessment").asText());
    }
    @TestFactory Stream<DynamicTest> oxygenSulfurAndMixedRingCases() {
        return Stream.of("furan","thiophene","pyrimidine","cyclopentane","cyclopentene").map(name->DynamicTest.dynamicTest(name,()->{
            boolean six=name.equals("pyrimidine"),nonaromatic=name.startsWith("cyclo");
            var r=run("HETEROAROMATIC.RING"+(six?6:5),molecule(name));
            assertEquals(nonaromatic?"ABSENT_FALSE":"SUPPORTED_PRESENT",r.get("assessment").asText());
        }));
    }
    @Test void openingRingDigitCarriesQualifiedQueryBondSemantics()throws Exception {
        var graph=molecule("benzene").graph();
        assertTrue(BACKEND.match("[*]1~[*]~[*]~[*]~[*]~[*]~1",graph).queryToTargetAtomIds().isEmpty(),"preserved OCL closing-only flag limitation");
        assertEquals(1,BACKEND.match("[*]~1~[*]~[*]~[*]~[*]~[*]1",graph).queryToTargetAtomIds().size());
    }
    public static void main(String[] args)throws Exception {
        var results=new ArrayList<JsonNode>();for(var row:cases())results.add(run(row[0],molecule(row[1])));Files.write(Path.of(args[0]),SystemStateView.bytes(results));
    }
}
