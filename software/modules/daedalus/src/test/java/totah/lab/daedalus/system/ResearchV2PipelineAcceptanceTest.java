package totah.lab.daedalus.system;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ResearchV2PipelineAcceptanceTest {
    @TempDir Path temp;
    @Test void twoStageCurrentPathPublishesOnlyAfterSeparatedQualification()throws Exception {
        var result=ResearchGatePipelineAcceptanceTest.run(temp,"valid",true);
        assertEquals(1,result.history().envelopes().values().stream().filter(e->e.evidenceType().equals("athena:rule-qualification-receipt")).count());
        assertTrue(result.history().interpretations().values().stream().anyMatch(i->i.evaluator().id().endsWith("/evaluate")&&i.status()==EvidenceInterpretation.Status.SUPPORTED_PRESENT));
    }
    @ParameterizedTest @ValueSource(strings={"valid","public","positive","conflict","partial","inherited","no-coverage","reverse","unrelated-component"})
    void directPathPreservesSelectionAndEvidence(String variant)throws Exception {
        var r=DirectAssessmentExecutionAcceptanceTest.run(temp,variant,true);
        if(!variant.equals("public"))assertEquals(1,r.calls());
        assertFalse(DirectAssessmentExecutionAcceptanceTest.findings(r).isEmpty());
        if(Set.of("inherited","no-coverage").contains(variant))assertTrue(r.received().isEmpty());
        else if(!variant.equals("public"))assertEquals(1,r.received().size());
    }
    @ParameterizedTest @ValueSource(strings={"expired","swapped-science","swapped-context","swapped-implementation","wrong-manifest","mixed-report","failed-check","missing-check","no-clock","time-failure","final-time-failure","wrong-definition","missing-system","invalidated","request-state","request-hash","configuration","bad-implementation"})
    void failedGateExecutesZeroTimesAndPreservesInputs(String variant)throws Exception {
        var r=DirectAssessmentExecutionAcceptanceTest.run(temp,variant,true);
        assertEquals(0,r.calls());assertTrue(DirectAssessmentExecutionAcceptanceTest.findings(r).isEmpty());
    }
    @Test void newExecutableRequiresFreshReportWhileScientificApprovalSurvives()throws Exception {
        var oldDir=temp.resolve("old");var newDir=temp.resolve("new");var staleDir=temp.resolve("stale");
        Files.createDirectories(oldDir);Files.createDirectories(newDir);Files.createDirectories(staleDir);
        var old=ResearchGatePipelineAcceptanceTest.run(oldDir,"valid",true);
        var fresh=ResearchGatePipelineAcceptanceTest.run(newDir,"implementation-revision",true);
        var stale=ResearchGatePipelineAcceptanceTest.run(staleDir,"stale-implementation",true);
        assertEquals(old.manifest().research().definitionSha256(),fresh.manifest().research().definitionSha256());
        assertEquals(old.manifest().research().dossier(),fresh.manifest().research().dossier());
        var receipt=old.history().envelopes().values().stream().filter(e->e.evidenceType().equals("athena:rule-qualification-receipt")).findFirst().orElseThrow();
        assertTrue(fresh.history().envelopes().values().stream().anyMatch(e->e.evidenceType().equals("athena:rule-qualification-receipt")));
        assertFalse(stale.history().envelopes().values().stream().anyMatch(e->e.evidenceType().equals("athena:rule-qualification-receipt")));
        var decoded=totah.lab.athena.system.rules.research.ResearchDocuments.decode(receipt.readPayload(),totah.lab.athena.system.rules.research.RuleQualificationReceipt.class);
        var reads=new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(java.io.IOException.class,()->totah.lab.athena.system.rules.research.RuleQualification.verify(fresh.manifest(),decoded,fresh.context(),source->{reads.incrementAndGet();throw new java.io.IOException("must reject before reading");},totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest.AT));
        assertEquals(0,reads.get());
    }
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]);Files.createDirectories(root);var two=root.resolve("two");var direct=root.resolve("direct");Files.createDirectories(two);Files.createDirectories(direct);
        var a=ResearchGatePipelineAcceptanceTest.run(two,"valid",true);var b=DirectAssessmentExecutionAcceptanceTest.run(direct,"valid",true);
        Files.write(Path.of(args[1]),totah.lab.athena.system.SystemStateView.bytes(Map.of("two",a.result().published().catalogSnapshot(),"direct",b.published().catalogSnapshot(),"calls",b.calls())));
    }
}
