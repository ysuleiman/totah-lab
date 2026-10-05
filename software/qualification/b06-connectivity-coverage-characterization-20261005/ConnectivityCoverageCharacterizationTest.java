package totah.lab.daedalus.system;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.nio.file.*;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.gaia.structure.*;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.graph.ResidueGraph;
import totah.lab.mnemosyne.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.RuleRegistryAcceptanceTest.*;
/** Immutable characterization of legacy behavior, not endorsement of its negative. */
class ConnectivityCoverageCharacterizationTest {
    static Map<String,Object> observe(ConnectivityProvenance provenance)throws Exception {
        var raw=SystemQualificationAcceptanceTest.state(0,false);
        var structure=new Structure(raw.graph().structure().getChains(),List.of(new Bond(atom(1,"C1"),atom(95,"H1"),BondOrder.SINGLE)),new ConnectivityMetadata(provenance,List.of("Synthetic imported listed edge only; no assertion of exhaustive sulfur connectivity")));
        var s=new SystemStateView(raw.identity(),ResidueGraph.from(structure),raw.components(),raw.sources(),Set.of(),raw.charges(),true,false,List.of("No pair-scoped completeness declaration"));
        var m=RuleRegistry.bundled().manifests().values().stream().filter(x->x.ruleId().equals("SULF.SS.001")).findFirst().orElseThrow();
        var request=new RuleRequest(s.binding(),m.key(),RuleRegistry.digest(m),List.of(atom(17,"SG"),atom(48,"SG")),List.of(),List.of(),4.5,2,100,100);
        var collector=RuleAnalyzers.collector(m,request);var finding=collector.analyze(s,List.of(),Map.of()).getFirst();
        var e=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"coverage-characterization"),"raw","athena:rule-measurements",finding.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8),collector.method(),s.subject(),T,List.of());
        var result=RuleAnalyzers.evaluator(m,request).analyze(s,List.of(e),Map.of()).getFirst();
        return Map.of("provenance",provenance,"legacyStatus",result.status(),"rawMeasurements",finding.measurements(),"sourceSnapshot",s.snapshot(),"manifest",m);
    }
    @Test void fullyMappedListedEdgesAreMistakenForCompleteNegativeCoverage()throws Exception {
        assertEquals(EvidenceInterpretation.Status.ABSENT_FALSE,observe(ConnectivityProvenance.EXPLICIT).get("legacyStatus"));
        assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,observe(ConnectivityProvenance.PARTIAL).get("legacyStatus"));
    }
    public static void main(String[] args)throws Exception {Files.write(Path.of(args[0]),SystemStateView.bytes(Map.of("explicit",observe(ConnectivityProvenance.EXPLICIT),"partial",observe(ConnectivityProvenance.PARTIAL))));}
}
