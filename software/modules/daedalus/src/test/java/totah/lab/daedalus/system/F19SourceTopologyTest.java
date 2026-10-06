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
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.system;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;
import static totah.lab.daedalus.system.F07SourceIdentityTest.fixture;

/** Source topology, not hydrophobic fragments or a stability/validity classifier. */
class F19SourceTopologyTest {
    static final String[] IDS={"CARBON_BRANCH_POINT.THREE_CARBON_NEIGHBORS",
            "CARBON_BRANCH_POINT.FOUR_CARBON_NEIGHBORS","TERT_BUTYL.SOURCE_CONNECTIVITY","CARBON_SINGLE_BOND_TRIANGLE"};
    static RuleManifest rule(int i) throws Exception {
        try(var in=F19SourceTopologyTest.class.getResourceAsStream("/totah/lab/athena/system/rules/groups-f19-v1/ATHENA.GROUP."+IDS[i]+".rule.json")) {
            assertNotNull(in);var n=(ObjectNode)JSON.readTree(in);assertEquals("NOT_EVALUATED",n.path("qualification").asText());
            n.put("qualification","QUALIFIED");return RuleRegistry.decode(SystemStateView.bytes(n)); // engineering only
        }
    }
    static Fixture positive(int i) {
        return switch(i) {
            case 0 -> fixture("c:C a:C b:C d:C","c:a:SINGLE c:b:SINGLE c:d:SINGLE","c:1 a:3 b:3 d:3");
            case 1 -> fixture("c:C a:C b:C d:C e:C","c:a:SINGLE c:b:SINGLE c:d:SINGLE c:e:SINGLE","c:0 a:3 b:3 d:3 e:3");
            case 2 -> fixture("c:C a:C b:C d:C o:O","c:a:SINGLE c:b:SINGLE c:d:SINGLE c:o:SINGLE","c:0 a:3 b:3 d:3 o:1");
            default -> fixture("a:C b:C c:C","a:b:SINGLE b:c:SINGLE c:a:SINGLE","a:2 b:2 c:2");
        };
    }
    static JsonNode evaluate(int i,Fixture f) throws Exception {
        var s=system(List.of(f.graph()),true,false);var original=SystemStateView.bytes(s.snapshot());
        var r=report(rule(i),s,coverage(s,f));assertArrayEquals(original,SystemStateView.bytes(s.snapshot()));return r;
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3})
    void sourceIdentityExplicitHAndPermutation(int i) throws Exception {
        var f=positive(i);var result=evaluate(i,f);assertEquals("SUPPORTED_PRESENT",result.path("assessment").asText());
        assertEquals(1,result.path("occurrences").size());assertEquals("SUPPORTED_PRESENT",evaluate(i,explicit(f)).path("assessment").asText());
        assertEquals(2,evaluate(i,doubled(f)).path("occurrences").size());
        var aa=new ArrayList<>(f.graph().atoms());Collections.reverse(aa);var bb=new ArrayList<>(f.graph().bonds());Collections.reverse(bb);
        assertEquals(normalized(result),normalized(evaluate(i,new Fixture(new MolecularGraph(aa,bb,Map.of()),f.hydrogens()))));
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3})
    void incompleteSourceNeverClaimsNegative(int i) throws Exception {
        var f=positive(i);var s=system(List.of(f.graph()),true,false);
        for(String mode:List.of("H","graph","charge")) {
            var c=coverage(s,f);var a=(ObjectNode)c.path("atomState").path("c");
            if(mode.equals("H")){a.put("hydrogenMode","UNKNOWN");a.putNull("implicitHydrogenCount");}
            else if(mode.equals("charge"))a.put("chargeStatus","UNKNOWN_INCONCLUSIVE");else c.put("completeGraph","UNKNOWN_INCONCLUSIVE");
            assertEquals(i==3 && mode.equals("H")?"SUPPORTED_PRESENT":"UNKNOWN_INCONCLUSIVE",report(rule(i),s,c).path("assessment").asText());
        }
    }
    @Test void exactHeavyDegreeAndConnectivityNearMisses() throws Exception {
        assertEquals("ABSENT_FALSE",evaluate(0,positive(1)).path("assessment").asText());
        assertEquals("ABSENT_FALSE",evaluate(1,positive(0)).path("assessment").asText());
        assertEquals("ABSENT_FALSE",evaluate(2,positive(0)).path("assessment").asText());
        var chain=fixture("a:C b:C c:C","a:b:SINGLE b:c:SINGLE","a:3 b:2 c:3");
        assertEquals("ABSENT_FALSE",evaluate(3,chain).path("assessment").asText());
        var unsaturated=fixture("a:C b:C c:C","a:b:DOUBLE b:c:SINGLE c:a:SINGLE","a:1 b:1 c:2");
        assertEquals("ABSENT_FALSE",evaluate(3,unsaturated).path("assessment").asText());
    }
    @Test void symmetryIsCorrespondenceNotDuplicateIdentity() throws Exception {
        int[] alternatives={6,24,6,6};
        for(int i=0;i<4;i++)assertEquals(alternatives[i],evaluate(i,positive(i)).path("occurrences").get(0).path("roleCorrespondenceAlternatives").size());
        assertEquals(4,evaluate(2,positive(1)).path("occurrences").size()); // four distinct attached tBu member sets
    }
    @Test void substitutedFusedAndSpiroTrianglesCoexistWithBranchPoints() throws Exception {
        var fused=fixture("a:C b:C c:C d:C","a:b:SINGLE b:c:SINGLE c:a:SINGLE b:d:SINGLE d:c:SINGLE","a:2 b:1 c:1 d:2");
        assertEquals(2,evaluate(3,fused).path("occurrences").size());assertEquals(2,evaluate(0,fused).path("occurrences").size());
        var spiro=fixture("a:C b:C c:C d:C e:C","a:b:SINGLE b:c:SINGLE c:a:SINGLE a:d:SINGLE d:e:SINGLE e:a:SINGLE","a:0 b:2 c:2 d:2 e:2");
        assertEquals(2,evaluate(3,spiro).path("occurrences").size());assertEquals(1,evaluate(1,spiro).path("occurrences").size());
        var exo=fixture("a:C b:C c:C d:C","a:b:SINGLE b:c:SINGLE c:a:SINGLE a:d:DOUBLE","a:0 b:2 c:2 d:2");
        assertEquals("SUPPORTED_PRESENT",evaluate(3,exo).path("assessment").asText()); // triangle topology, not universal saturated fragment
    }
    public static void main(String[] args) throws Exception {
        var results=new ArrayList<JsonNode>();for(int i=0;i<4;i++){results.add(evaluate(i,positive(i)));results.add(evaluate(i,explicit(positive(i))));}
        Files.write(Path.of(args[0]),SystemStateView.bytes(results));
    }
}
