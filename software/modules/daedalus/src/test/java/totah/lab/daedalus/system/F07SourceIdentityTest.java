package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.SystemStateView;
import totah.lab.athena.system.rules.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;

/** Approved source identities only. No chemical-function or interaction inference. */
class F07SourceIdentityTest {
    static final String[] IDS={"IMINE.N_METHYL.CARBON_BOUND","IMINE.METHYLENE.CARBON_BOUND",
            "OXIME.HYDROXY.CARBON_BOUND","NITROSO.CARBON_BOUND",
            "IMINE.N_CARBON_BOUND.ACYCLIC","IMINE.N_CARBON_BOUND.EXOCYCLIC",
            "IMINE.N_CARBON_BOUND.ENDOCYCLIC","IMINE.N_H.ACYCLIC_C","IMINE.N_H.CYCLIC_C"};
    static RuleManifest rule(int i) throws Exception {
        try(var in=F07SourceIdentityTest.class.getResourceAsStream("/totah/lab/athena/system/rules/groups-f07-v1/ATHENA.GROUP."+IDS[i]+".rule.json")) {
            assertNotNull(in);var n=(ObjectNode)JSON.readTree(in);assertEquals("NOT_EVALUATED",n.get("qualification").asText());
            n.put("qualification","QUALIFIED"); // Engineering fixture; not a production receipt.
            return RuleRegistry.decode(SystemStateView.bytes(n));
        }
    }
    static Fixture fixture(String atoms,String bonds,String counts) {
        var aa=new ArrayList<MolecularGraph.Atom>();var bb=new ArrayList<MolecularGraph.Bond>();var hh=new TreeMap<String,Integer>();
        for(String token:atoms.split(" ")) {var s=token.split(":");aa.add(atom(s[0],s[1],s.length>2?Integer.parseInt(s[2]):0,false,aa.size(),0,0));}
        for(String token:bonds.split(" ")) {var s=token.split(":");bb.add(bond(s[0],s[1],MolecularGraph.BondOrder.valueOf(s[2])));}
        for(String token:counts.split(" ")) {var s=token.split(":");hh.put(s[0],Integer.parseInt(s[1]));}
        return new Fixture(new MolecularGraph(aa,bb,Map.of()),hh);
    }
    static Fixture positive(int i) {
        return switch(i) {
            case 0,1,4 -> fixture("c:C n:N r:C","c:n:DOUBLE n:r:SINGLE","c:2 n:0 r:3");
            case 2 -> fixture("c:C n:N o:O","c:n:DOUBLE n:o:SINGLE","c:2 n:0 o:1");
            case 3 -> fixture("r:C n:N o:O","r:n:SINGLE n:o:DOUBLE","r:3 n:0 o:0");
            case 5 -> fixture("c:C n:N r:C a:C b:C","c:n:DOUBLE n:r:SINGLE c:a:SINGLE a:b:SINGLE b:c:SINGLE","c:0 n:0 r:3 a:2 b:2");
            case 6 -> fixture("c:C n:N r:C t:C","c:n:DOUBLE n:r:SINGLE r:t:SINGLE t:c:SINGLE","c:1 n:0 r:2 t:2");
            case 7 -> fixture("c:C n:N","c:n:DOUBLE","c:2 n:1");
            default -> fixture("c:C n:N a:C b:C","c:n:DOUBLE c:a:SINGLE a:b:SINGLE b:c:SINGLE","c:0 n:1 a:2 b:2");
        };
    }
    static JsonNode evaluate(int i,Fixture f) throws Exception {
        var s=system(List.of(f.graph()),true,false);var before=SystemStateView.bytes(s.snapshot());
        var result=report(rule(i),s,coverage(s,f));assertArrayEquals(before,SystemStateView.bytes(s.snapshot()));return result;
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6,7,8})
    void positiveExplicitHydrogenOccurrencesAndPermutation(int i) throws Exception {
        var f=positive(i);assertEquals("SUPPORTED_PRESENT",evaluate(i,f).path("assessment").asText());
        assertEquals("SUPPORTED_PRESENT",evaluate(i,explicit(f)).path("assessment").asText());
        assertEquals(2,evaluate(i,doubled(f)).path("occurrences").size());
        var aa=new ArrayList<>(f.graph().atoms());Collections.reverse(aa);var bb=new ArrayList<>(f.graph().bonds());Collections.reverse(bb);
        assertEquals(normalized(evaluate(i,f)),normalized(evaluate(i,new Fixture(new MolecularGraph(aa,bb,Map.of()),f.hydrogens()))));
        var members=evaluate(i,f).path("occurrences").get(0).path("memberAtomIds");
        assertEquals(i==2?3:2,members.size());assertFalse(members.toString().contains("\"r\""));
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6,7,8})
    void incompleteStateAndCoverageAreNotNegative(int i) throws Exception {
        var f=positive(i);var s=system(List.of(f.graph()),true,false);
        for(String mode:List.of("H","charge","graph")) {
            var c=coverage(s,f);var n=(ObjectNode)c.path("atomState").path("n");
            if(mode.equals("H")){n.put("hydrogenMode","UNKNOWN");n.putNull("implicitHydrogenCount");}
            else if(mode.equals("charge"))n.put("chargeStatus","UNKNOWN_INCONCLUSIVE");else c.put("completeGraph","UNKNOWN_INCONCLUSIVE");
            assertEquals("UNKNOWN_INCONCLUSIVE",report(rule(i),s,c).path("assessment").asText());
        }
    }
    @Test void imineOverlapReusesMethylCorrespondence() throws Exception {
        var f=positive(0);var s=system(List.of(f.graph()),true,false);
        var methyl=report(manifest("METHYL"),s,coverage(s,f));
        assertEquals("SUPPORTED_PRESENT",methyl.path("assessment").asText());
        var member=methyl.path("occurrences").get(0).path("memberAtomIds").get(0).asText();
        assertEquals(member,evaluate(0,f).path("occurrences").get(0).path("roleCorrespondenceAlternatives").get(0).path("methylCarbon").get(0).asText());
        assertEquals(evaluate(0,f).path("occurrences").get(0).path("memberAtomIds"),evaluate(1,f).path("occurrences").get(0).path("memberAtomIds"));
    }
    @Test void constitutionalNearMisses() throws Exception {
        var ethyl=fixture("c:C n:N r:C t:C","c:n:DOUBLE n:r:SINGLE r:t:SINGLE","c:2 n:0 r:2 t:3");
        assertEquals("ABSENT_FALSE",evaluate(0,ethyl).path("assessment").asText());
        assertEquals("SUPPORTED_PRESENT",evaluate(1,ethyl).path("assessment").asText());
        var substituted=fixture("c:C n:N r:C t:C","c:n:DOUBLE n:r:SINGLE c:t:SINGLE","c:1 n:0 r:3 t:3");
        assertEquals("SUPPORTED_PRESENT",evaluate(0,substituted).path("assessment").asText());
        assertEquals("ABSENT_FALSE",evaluate(1,substituted).path("assessment").asText());
        var ether=fixture("c:C n:N o:O r:C","c:n:DOUBLE n:o:SINGLE o:r:SINGLE","c:2 n:0 o:0 r:3");
        assertEquals("ABSENT_FALSE",evaluate(2,ether).path("assessment").asText());
        var nitrite=fixture("r:C x:O n:N o:O","r:x:SINGLE x:n:SINGLE n:o:DOUBLE","r:3 x:0 n:0 o:0");
        assertEquals("ABSENT_FALSE",evaluate(3,nitrite).path("assessment").asText());
    }
    @Test void endocyclicImineIsSupportedByItsOwnClass() throws Exception {
        var ring=fixture("c:C n:N r:C t:C","c:n:DOUBLE n:r:SINGLE r:t:SINGLE t:c:SINGLE","c:1 n:0 r:2 t:2");
        for(int i:List.of(0,1,4,5,7,8))assertEquals("ABSENT_FALSE",evaluate(i,ring).path("assessment").asText());
        assertEquals("SUPPORTED_PRESENT",evaluate(6,ring).path("assessment").asText());
    }
    @Test void chargedNitroIsNotNeutralNitroso() throws Exception {
        var nitro=fixture("r:C n:N:1 o:O x:O:-1","r:n:SINGLE n:o:DOUBLE n:x:SINGLE","r:3 n:0 o:0 x:0");
        assertEquals("UNSUPPORTED",evaluate(3,nitro).path("assessment").asText());
    }
    @Test void carbonSubstitutionAndSymmetryKeepExactMemberSet() throws Exception {
        for(int i:List.of(0,2)) {
            var f=i==0?fixture("c:C n:N r:C a:C b:C","c:n:DOUBLE n:r:SINGLE c:a:SINGLE c:b:SINGLE","c:0 n:0 r:3 a:3 b:3")
                    :fixture("c:C n:N o:O a:C b:C","c:n:DOUBLE n:o:SINGLE c:a:SINGLE c:b:SINGLE","c:0 n:0 o:1 a:3 b:3");
            var result=evaluate(i,f);assertEquals("SUPPORTED_PRESENT",result.path("assessment").asText());
            assertEquals(1,result.path("occurrences").size());
            var hetero=i==0?fixture("c:C n:N r:C x:O","c:n:DOUBLE n:r:SINGLE c:x:SINGLE","c:1 n:0 r:3 x:1")
                    :fixture("c:C n:N o:O x:O","c:n:DOUBLE n:o:SINGLE c:x:SINGLE","c:1 n:0 o:1 x:1");
            assertEquals("UNSUPPORTED",evaluate(i,hetero).path("assessment").asText());
        }
    }
    @Test void aromaticNitrogenBoundaryAndContradictoryHydrogensStayNonNegative() throws Exception {
        var pyridine=ChemicalRoleAcceptanceTest.chemical("pyridine");
        for(int i:List.of(0,1,4,5,6,7,8))assertEquals("ABSENT_FALSE",evaluate(i,pyridine).path("assessment").asText());
        for(int i=0;i<IDS.length;i++) {
            var f=positive(i);var bad=new TreeMap<>(f.hydrogens());bad.put("n",f.hydrogens().get("n")+1);
            assertNotEquals("SUPPORTED_PRESENT",evaluate(i,new Fixture(f.graph(),bad)).path("assessment").asText());
            assertNotEquals("ABSENT_FALSE",evaluate(i,new Fixture(f.graph(),bad)).path("assessment").asText());
        }
    }
    @Test void registryRemainsOptInAndMethylDefinitionIsActuallyComposed() throws Exception {
        var methyl=JSON.readTree(manifest("METHYL").parameters().get("definition").value());
        var d=JSON.readTree(rule(0).parameters().get("definition").value());
        assertEquals(methyl.path("query").asText().replace("[*]","[N;!a;+0;R0]")+"=[C;!a;+0]",d.path("query").asText());
        assertEquals(methyl.path("requiredState").path("roleHeavyDegree").path("methylCarbon"),d.path("requiredState").path("roleHeavyDegree").path("methylCarbon"));
        for(int i=0;i<IDS.length;i++)assertFalse(RuleRegistry.scientific().manifests().containsKey(rule(i).key()));
    }
    @Test void allTopologySubclassesAreDistinctAndRetainSpecializations() throws Exception {
        for(int i=4;i<IDS.length;i++)for(int j=4;j<IDS.length;j++)
            assertEquals(i==j?"SUPPORTED_PRESENT":"ABSENT_FALSE",evaluate(i,positive(j)).path("assessment").asText(),IDS[i]+" / "+IDS[j]);
        assertEquals("SUPPORTED_PRESENT",evaluate(0,positive(5)).path("assessment").asText()); // exocyclic carbon, N methyl
    }
    @Test void aromaticClassUsesExistingHeteroaromaticIdentityInsteadOfLocalizedImine() throws Exception {
        var f=ChemicalRoleAcceptanceTest.chemical("pyridine");var state=system(List.of(f.graph()),true,false);
        var path=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/groups-foundation-v2");
        var m=RuleRegistry.load(path).manifests().values().stream().filter(x->x.ruleId().equals("ATHENA.GROUP.HETEROAROMATIC.RING6")).findFirst().orElseThrow();
        var qualified=(ObjectNode)JSON.readTree(SystemStateView.bytes(m));qualified.put("qualification","QUALIFIED");
        assertEquals("SUPPORTED_PRESENT",report(RuleRegistry.decode(SystemStateView.bytes(qualified)),state,coverage(state,f)).path("assessment").asText());
    }
    @ParameterizedTest @ValueSource(ints={3,5,6,7,8})
    void endocyclicConnectivityHasNoInventedRingSizeCutoff(int size) throws Exception {
        var atoms=new StringBuilder("c:C n:N");var bonds=new StringBuilder("c:n:DOUBLE");var hs=new StringBuilder("c:1 n:0");
        String previous="n";
        for(int k=2;k<size;k++){String id="r"+k;atoms.append(" ").append(id).append(":C");bonds.append(" ").append(previous).append(":").append(id).append(":SINGLE");hs.append(" ").append(id).append(":2");previous=id;}
        bonds.append(" ").append(previous).append(":c:SINGLE");
        assertEquals("SUPPORTED_PRESENT",evaluate(6,fixture(atoms.toString(),bonds.toString(),hs.toString())).path("assessment").asText());
    }
    @Test void aromaticCarbonAttachmentIsContextNotEndocyclicImineNitrogen() throws Exception {
        var benzene=FoundationVocabularyAcceptanceTest.molecule("benzene");
        var aa=new ArrayList<>(benzene.graph().atoms());aa.add(atom("n","N",0,false,8,0,0));aa.add(atom("c","C",0,false,9,0,0));
        var bb=new ArrayList<>(benzene.graph().bonds());bb.add(bond("a0","n",MolecularGraph.BondOrder.SINGLE));bb.add(bond("n","c",MolecularGraph.BondOrder.DOUBLE));
        var hs=new TreeMap<>(benzene.hydrogens());hs.put("a0",0);hs.put("n",0);hs.put("c",2);
        var f=new Fixture(new MolecularGraph(aa,bb,Map.of()),hs);
        assertEquals("SUPPORTED_PRESENT",evaluate(4,f).path("assessment").asText());
        assertEquals("SUPPORTED_PRESENT",evaluate(1,f).path("assessment").asText());
    }
    public static void main(String[] args) throws Exception {
        var results=new ArrayList<JsonNode>();for(int i=0;i<IDS.length;i++){results.add(evaluate(i,positive(i)));results.add(evaluate(i,explicit(positive(i))));}
        Files.write(Path.of(args[0]),SystemStateView.bytes(results));
    }
}
