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

class F08SourceStateTest {
    static final Path ROOT=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/groups-f08-v1");
    static Stream<RuleManifest> definitions() throws Exception {return RuleRegistry.load(ROOT).manifests().values().stream();}
    static Fixture positive(String id) {
        if(id.contains("HYDRAZINE")||id.contains("DIAZENE")) {
            int h1=id.charAt(id.lastIndexOf(".H")+2)-'0',h2=id.charAt(id.length()-1)-'0';boolean single=id.contains("HYDRAZINE");
            var atoms=new StringBuilder("n:N m:N");var bonds=new StringBuilder("n:m:"+(single?"SINGLE":"DOUBLE"));var hs=new StringBuilder("n:"+h1+" m:"+h2);
            for(int side=0;side<2;side++)for(int k=0;k<(single?2:1)-(side==0?h1:h2);k++) {
                String a="r"+side+k;atoms.append(" ").append(a).append(":C");bonds.append(" ").append(side==0?"n":"m").append(":").append(a).append(":SINGLE");hs.append(" ").append(a).append(":3");
            }
            return fixture(atoms.toString(),bonds.toString(),hs.toString());
        }
        if(id.contains("AZIDE"))return id.endsWith("DOUBLE_DOUBLE")
                ?fixture("r:C a:N n:N:1 m:N:-1","r:a:SINGLE a:n:DOUBLE n:m:DOUBLE","r:3 a:0 n:0 m:0")
                :fixture("r:C a:N:-1 n:N:1 m:N","r:a:SINGLE a:n:SINGLE n:m:TRIPLE","r:3 a:0 n:0 m:0");
        if(id.contains("DIAZONIUM"))return fixture("r:C n:N:1 m:N","r:n:SINGLE n:m:TRIPLE","r:3 n:0 m:0");
        return id.contains("C_NEG")?fixture("c:C:-1 n:N:1 m:N","c:n:SINGLE n:m:TRIPLE","c:2 n:0 m:0")
                :fixture("c:C n:N:1 m:N:-1","c:n:DOUBLE n:m:DOUBLE","c:2 n:0 m:0");
    }
    static JsonNode evaluate(RuleManifest m,Fixture f) throws Exception {
        var n=(ObjectNode)JSON.readTree(SystemStateView.bytes(m));n.put("qualification","QUALIFIED");m=RuleRegistry.decode(SystemStateView.bytes(n));
        var s=system(List.of(f.graph()),true,false);var original=SystemStateView.bytes(s.snapshot());var r=report(m,s,coverage(s,f));
        assertArrayEquals(original,SystemStateView.bytes(s.snapshot()));return r;
    }
    @ParameterizedTest @MethodSource("definitions") void exactStatesExplicitHydrogenAndDistinctOccurrences(RuleManifest m) throws Exception {
        var f=positive(m.ruleId());assertEquals("SUPPORTED_PRESENT",evaluate(m,f).path("assessment").asText(),m.ruleId());
        assertEquals("SUPPORTED_PRESENT",evaluate(m,explicit(f)).path("assessment").asText(),m.ruleId());
        assertEquals(2,evaluate(m,doubled(f)).path("occurrences").size(),m.ruleId());
        var aa=new ArrayList<>(f.graph().atoms());Collections.reverse(aa);var bb=new ArrayList<>(f.graph().bonds());Collections.reverse(bb);
        assertEquals(normalized(evaluate(m,f)),normalized(evaluate(m,new Fixture(new MolecularGraph(aa,bb,Map.of()),f.hydrogens()))));
    }
    @ParameterizedTest @MethodSource("definitions") void missingAuthoritativeHIsNeverMatcherInferred(RuleManifest m) throws Exception {
        var f=positive(m.ruleId());var s=system(List.of(f.graph()),true,false);var c=coverage(s,f);var atom=(ObjectNode)c.path("atomState").path("n");
        atom.put("hydrogenMode","UNKNOWN");atom.putNull("implicitHydrogenCount");var n=(ObjectNode)JSON.readTree(SystemStateView.bytes(m));n.put("qualification","QUALIFIED");
        assertEquals("UNKNOWN_INCONCLUSIVE",report(RuleRegistry.decode(SystemStateView.bytes(n)),s,c).path("assessment").asText());
    }
    @Test void cyclicNitrogenPairsAndSharedAttachmentCarbonAreIncluded() throws Exception {
        for(var m:definitions().toList()) {
            if(m.ruleId().endsWith("HYDRAZINE.NEUTRAL.H1_H1")) {
                var f=fixture("n:N m:N c:C","n:m:SINGLE m:c:SINGLE c:n:SINGLE","n:1 m:1 c:2");
                assertEquals("SUPPORTED_PRESENT",evaluate(m,f).path("assessment").asText());
            }
            if(m.ruleId().endsWith("DIAZENE.NEUTRAL.H0_H0")) {
                var f=fixture("n:N m:N c:C","n:m:DOUBLE m:c:SINGLE c:n:SINGLE","n:0 m:0 c:2");
                assertEquals("SUPPORTED_PRESENT",evaluate(m,f).path("assessment").asText());
            }
        }
    }
    @Test void bondAndChargeDepictionsAreNotNormalizedIntoOneIdentity() throws Exception {
        var ds=definitions().toList();assertEquals(14,ds.size());
        for(var m:ds)for(var other:ds)if(!m.ruleId().equals(other.ruleId())) {
            var r=evaluate(m,positive(other.ruleId()));assertNotEquals("SUPPORTED_PRESENT",r.path("assessment").asText(),m.ruleId()+" vs "+other.ruleId());
        }
    }
    public static void main(String[] args) throws Exception {
        var rows=new ArrayList<JsonNode>();for(var m:definitions().toList())rows.add(evaluate(m,positive(m.ruleId())));
        Files.write(Path.of(args[0]),SystemStateView.bytes(rows));
    }
}
