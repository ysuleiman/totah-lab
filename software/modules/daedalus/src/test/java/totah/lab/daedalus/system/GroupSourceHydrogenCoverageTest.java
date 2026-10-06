package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.system;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;

/** Permanent witness: source-H requirements must not be replaced by matcher inference. */
class GroupSourceHydrogenCoverageTest {
    static totah.lab.athena.system.rules.RuleManifest corrected(int i) throws Exception {
        try(var in=GroupSourceHydrogenCoverageTest.class.getResourceAsStream("/totah/lab/athena/system/rules/groups-f07-source-h-v4/ATHENA.GROUP."+F07SourceIdentityTest.IDS[i]+".rule.json")) {
            assertNotNull(in);var n=(ObjectNode)JSON.readTree(in);n.put("qualification","QUALIFIED");
            return totah.lab.athena.system.rules.RuleRegistry.decode(totah.lab.athena.system.SystemStateView.bytes(n));
        }
    }
    static JsonNode missing(int i,boolean corrected) throws Exception {
        var f=F07SourceIdentityTest.positive(i);var s=system(List.of(f.graph()),true,false);var c=coverage(s,f);
        var carbon=(ObjectNode)c.path("atomState").path("c");carbon.put("hydrogenMode","UNKNOWN");carbon.putNull("implicitHydrogenCount");
        return report(corrected?corrected(i):F07SourceIdentityTest.rule(i),s,c);
    }
    @ParameterizedTest @ValueSource(ints={0,2,4,5,6,7,8})
    void historicalVariableCarbonHGapIsPreservedAsCharacterization(int i) throws Exception {
        var r=missing(i,false);assertFalse(r.path("negativeCoverage").path("REQUIRED_H_STATE").asBoolean());
        assertEquals("SUPPORTED_PRESENT",r.path("assessment").asText()); // historical defect, not normative acceptance
    }
    @ParameterizedTest @ValueSource(ints={0,2,4,5,6,7,8})
    void correctedMissingVariableHIsInconclusive(int i) throws Exception {
        var r=missing(i,true);assertEquals("UNKNOWN_INCONCLUSIVE",r.path("assessment").asText());assertTrue(r.path("occurrences").isEmpty());
        assertEquals(F07SourceIdentityTest.rule(i).parameters().get("definition"),corrected(i).parameters().get("definition"));
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6,7,8})
    void completeSourceReportsRemainByteIdentical(int i) throws Exception {
        var f=F07SourceIdentityTest.positive(i);var state=system(List.of(f.graph()),true,false);
        assertEquals(report(F07SourceIdentityTest.rule(i),state,coverage(state,f)),report(corrected(i),state,coverage(state,f)));
    }
    @ParameterizedTest @ValueSource(ints={0,2})
    void oneUnknownOccurrenceDoesNotEraseIndependentKnownOccurrence(int i) throws Exception {
        var f=doubled(F07SourceIdentityTest.positive(i));var state=system(List.of(f.graph()),true,false);var c=coverage(state,f);
        var atom=(ObjectNode)c.path("atomState").path("c");atom.put("hydrogenMode","UNKNOWN");atom.putNull("implicitHydrogenCount");
        var r=report(corrected(i),state,c);assertEquals("SUPPORTED_PRESENT",r.path("assessment").asText());assertEquals(1,r.path("occurrences").size());
        assertFalse(r.path("negativeCoverage").path("REQUIRED_H_STATE").asBoolean());
    }
    public static void main(String[] args) throws Exception {
        var rows=new ArrayList<JsonNode>();for(int i:new int[]{0,2,4,5,6,7,8}){rows.add(missing(i,false));rows.add(missing(i,true));}
        Files.write(Path.of(args[0]),totah.lab.athena.system.SystemStateView.bytes(rows));
    }
}
