package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.system;

/** Production resources must be the exact scientifically adopted, already tested definitions. */
class AdoptedGroupDefinitionsTest {
    static final Path ROOT=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/groups-adopted-v1");
    static List<Path> candidates() throws Exception {
        var paths=new ArrayList<Path>();paths.add(Path.of("software/qualification/f18-terminal-alkyne-contract-20261006/CANDIDATE_MANIFEST.json"));
        for(var dir:List.of("f05-acyl-sulfonyl-chloride-review-20261006","f14-isocyanate-review-20261006"))try(var files=Files.list(Path.of("software/qualification",dir))){paths.addAll(files.filter(p->p.toString().endsWith(".manifest.json")).sorted().toList());}
        return List.copyOf(paths);
    }
    @Test void exactApprovedBytesAreLoadableWithoutLegacyRegistryMigration() throws Exception {
        var production=RuleRegistry.load(ROOT);assertEquals(5,production.manifests().size());
        for(var p:candidates()) {
            var m=RuleRegistry.decode(Files.readAllBytes(p));
            assertArrayEquals(Files.readAllBytes(p),Files.readAllBytes(ROOT.resolve(m.ruleId()+".rule.json")));
            assertEquals(m,production.manifests().get(m.key()));
            assertFalse(RuleRegistry.scientific().manifests().containsKey(m.key()));
        }
    }
    @Test void productionDefinitionsExecuteThroughExistingGroupInterpreter() throws Exception {
        for(var manifest:RuleRegistry.load(ROOT).manifests().values()) {
            var n=(ObjectNode)JSON.readTree(SystemStateView.bytes(manifest));n.put("qualification","QUALIFIED"); // Engineering qualification only.
            var m=RuleRegistry.decode(SystemStateView.bytes(n));
            var id=m.ruleId();Fixture f;
            if(id.contains("CHLORIDE"))f=AcylSulfonylChlorideCandidateTest.fixture(id.contains("SULFONYL"),"Cl");
            else if(id.contains("CYANATE"))f=IsocyanateCandidateTest.fixture(id.contains("THIO"),false);
            else f=new Fixture(new totah.lab.athena.design.backend.MolecularGraph(
                    List.of(AthenaScientificRulesAcceptanceTest.atom("a","C",0,false,0,0,0),AthenaScientificRulesAcceptanceTest.atom("b","C",0,false,1.2,0,0)),
                    List.of(AthenaScientificRulesAcceptanceTest.bond("a","b",totah.lab.athena.design.backend.MolecularGraph.BondOrder.TRIPLE)),Map.of()),Map.of("a",1,"b",1));
            for(var fixture:List.of(f,explicit(f),doubled(f))) {
                var state=system(List.of(fixture.graph()),true,false);var result=report(m,state,coverage(state,fixture));
                assertEquals("SUPPORTED_PRESENT",result.get("assessment").asText(),id+result);
            }
        }
    }
}
