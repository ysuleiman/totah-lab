package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.SystemStateView;
import totah.lab.athena.system.rules.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.system;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;
import static totah.lab.daedalus.system.F07SourceIdentityTest.fixture;

class F15BoronSourceStateTest {
    static final Path ROOT=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/groups-f15-v1");
    static Stream<RuleManifest> definitions() throws Exception {return RuleRegistry.load(ROOT).manifests().values().stream();}
    static Fixture positive(String id) {
        if(id.contains("FOUR_SINGLE"))return fixture("b:B:-1 c:C d:C e:C f:C","b:c:SINGLE b:d:SINGLE b:e:SINGLE b:f:SINGLE","b:0 c:3 d:3 e:3 f:3");
        if(id.contains("DIESTER"))return fixture("b:B c:C o:O p:O r:C s:C","b:c:SINGLE b:o:SINGLE b:p:SINGLE o:r:SINGLE p:s:SINGLE","b:0 c:3 o:0 p:0 r:3 s:3");
        if(id.contains("MONOESTER"))return fixture("b:B c:C o:O p:O r:C","b:c:SINGLE b:o:SINGLE b:p:SINGLE o:r:SINGLE","b:0 c:3 o:0 p:1 r:3");
        return fixture("b:B c:C o:O p:O","b:c:SINGLE b:o:SINGLE b:p:SINGLE","b:0 c:3 o:1 p:1");
    }
    static JsonNode evaluate(RuleManifest m,Fixture f) throws Exception {return F08SourceStateTest.evaluate(m,f);}
    @ParameterizedTest @MethodSource("definitions") void sourceStateExplicitHAndPermutation(RuleManifest m) throws Exception {
        var f=positive(m.ruleId());assertEquals("SUPPORTED_PRESENT",evaluate(m,f).path("assessment").asText(),m.ruleId());
        assertEquals("SUPPORTED_PRESENT",evaluate(m,explicit(f)).path("assessment").asText());
        assertEquals(2,evaluate(m,doubled(f)).path("occurrences").size());
        var aa=new ArrayList<>(f.graph().atoms());Collections.reverse(aa);var bb=new ArrayList<>(f.graph().bonds());Collections.reverse(bb);
        assertEquals(normalized(evaluate(m,f)),normalized(evaluate(m,new Fixture(new MolecularGraph(aa,bb,Map.of()),f.hydrogens()))));
    }
    @ParameterizedTest @MethodSource("definitions") void incompleteHydrogenIsNotInferred(RuleManifest m) throws Exception {
        var f=positive(m.ruleId());var s=system(List.of(f.graph()),true,false);var c=coverage(s,f);var atom=(ObjectNode)c.path("atomState").path("b");
        atom.put("hydrogenMode","UNKNOWN");atom.putNull("implicitHydrogenCount");var n=(ObjectNode)JSON.readTree(SystemStateView.bytes(m));n.put("qualification","QUALIFIED");
        assertEquals("UNKNOWN_INCONCLUSIVE",report(RuleRegistry.decode(SystemStateView.bytes(n)),s,c).path("assessment").asText());
    }
    @Test void statesAreDistinctAndAlternativeBoronCoordinationIsNotNormalized() throws Exception {
        var ds=definitions().toList();assertEquals(4,ds.size());
        for(var m:ds)for(var other:ds)if(!m.ruleId().equals(other.ruleId()))assertNotEquals("SUPPORTED_PRESENT",evaluate(m,positive(other.ruleId())).path("assessment").asText());
        var borate=fixture("b:B:-1 c:C o:O p:O q:O","b:c:SINGLE b:o:SINGLE b:p:SINGLE b:q:SINGLE","b:0 c:3 o:1 p:1 q:1");
        for(var m:ds)assertEquals(m.ruleId().contains("FOUR_SINGLE"),evaluate(m,borate).path("assessment").asText().equals("SUPPORTED_PRESENT"));
    }
    @Test void cyclicAndSharedCarbonEsterAttachmentsAreIncluded() throws Exception {
        var m=definitions().filter(x->x.ruleId().contains("DIESTER")).findFirst().orElseThrow();
        for(var f:List.of(fixture("b:B c:C o:O p:O r:C s:C","b:c:SINGLE b:o:SINGLE b:p:SINGLE o:r:SINGLE p:s:SINGLE r:s:SINGLE","b:0 c:3 o:0 p:0 r:2 s:2"),
                fixture("b:B c:C o:O p:O r:C","b:c:SINGLE b:o:SINGLE b:p:SINGLE o:r:SINGLE p:r:SINGLE","b:0 c:3 o:0 p:0 r:2"))) {
            var r=evaluate(m,f);assertEquals("SUPPORTED_PRESENT",r.path("assessment").asText());assertEquals(1,r.path("occurrences").size());
        }
        var peroxide=fixture("b:B c:C o:O p:O r:O s:C","b:c:SINGLE b:o:SINGLE b:p:SINGLE o:r:SINGLE p:s:SINGLE","b:0 c:3 o:0 p:0 r:1 s:3");
        assertNotEquals("SUPPORTED_PRESENT",evaluate(m,peroxide).path("assessment").asText());
    }
    public static void main(String[] args) throws Exception {
        var rows=new ArrayList<JsonNode>();for(var m:definitions().toList())rows.add(evaluate(m,positive(m.ruleId())));
        Files.write(Path.of(args[0]),SystemStateView.bytes(rows));
    }
}
