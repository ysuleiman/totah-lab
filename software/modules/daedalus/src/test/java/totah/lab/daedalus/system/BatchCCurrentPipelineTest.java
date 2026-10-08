package totah.lab.daedalus.system;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.athena.system.rules.research.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
import static totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest.AT;

class BatchCCurrentPipelineTest {
    @TempDir Path temp;
    @ParameterizedTest @CsvSource({"I11,valid","I11,missing-clock","I11,inherited","V18,valid","V18,missing-clock","V18,inherited","A08,valid","A08,missing-clock","A08,inherited"})
    void independentCurrentExecution(String family,String variant)throws Exception {
        SystemStateView state;S1ResearchFixtures.Qualified q;List<EvidenceEnvelope> supplied;String namespace;
        if(family.equals("I11")){var t=new ClPheAcceptanceTest();t.temp=temp;var sample=t.sample("valid");state=sample.source().state;q=sample.qualification();supplied=sample.inputs();namespace="athena.cl-phe-candidate";}
        else if(family.equals("V18")){var t=new SourceSiteMetadataAcceptanceTest();t.temp=temp;var sample=t.sample("valid");state=sample.state();q=sample.qualification();supplied=sample.inputs();namespace="athena.source-site-metadata";}
        else{var t=new SourceFragmentLineageAcceptanceTest();t.temp=temp;var sample=t.sample("valid");state=sample.state();q=sample.qualification();supplied=sample.inputs();namespace="athena.source-fragment-lineage";}
        var inputs=new ArrayList<>(supplied);inputs.addAll(q.artifacts());inputs.removeIf(e->e.evidenceType().equals("athena:rule-qualification-receipt")&&q.artifacts().contains(e));
        var context=q.artifacts().stream().filter(e->e.evidenceType().equals("athena:rule-policy-context")).findFirst().orElseThrow();var implementation=q.artifacts().stream().filter(e->{try{return B01FunctionalGroupAcceptanceTest.JSON.readTree(e.readPayload()).path("schema").asText().equals("athena-rule-implementation-qualification/2");}catch(Exception ex){return false;}}).findFirst().orElseThrow();
        var catalog=new EvidenceSnapshotCatalog(Files.createDirectory(temp.resolve("current")));var pipeline=pipeline();var foundation=pipeline.run(catalog,Optional.empty(),state,variant.equals("inherited")?supplied:List.of(),Map.of(),List.of(),ref(ScientificReference.Kind.ACTIVITY,"batch-c-foundation"),AT);
        var executor=new RuleExecutionPipeline(pipeline,OCL,variant.equals("missing-clock")?null:(ctx,at)->{if(!at.equals(AT)||!ctx.issuer().equals(ResearchV2Fixtures.EXECUTOR))throw new java.io.IOException("synthetic current time mismatch");});
        var result=executor.runCurrent(catalog,foundation,Map.of(),new RuleRegistry().register(q.manifest()),ResearchDocuments.encode(q.manifest()),q.request(),Optional.empty(),new RuleExecutionPipeline.ResearchExecutionInputs(variant.equals("inherited")?q.artifacts():inputs,ResearchGatePipelineAcceptanceTest.pin(context.readPayload()),ResearchGatePipelineAcceptanceTest.pin(implementation.readPayload())),ref(ScientificReference.Kind.ACTIVITY,"batch-c-current"),AT);
        var history=catalog.read(result.published().catalogSnapshot()).orElseThrow().history();var evaluations=history.interpretations().values().stream().filter(i->i.evaluator().namespace().equals(namespace)&&i.evaluator().id().endsWith("/evaluate")).toList();
        if(variant.equals("valid")){assertEquals(1,evaluations.size(),history.interpretations().values().stream().filter(i->i.status()==EvidenceInterpretation.Status.FAILED).toList().toString());assertEquals(EvidenceInterpretation.Status.SUPPORTED_PRESENT,evaluations.getFirst().status(),evaluations.toString());}else assertTrue(evaluations.stream().noneMatch(i->i.status()==EvidenceInterpretation.Status.SUPPORTED_PRESENT),evaluations.toString());
    }
}
