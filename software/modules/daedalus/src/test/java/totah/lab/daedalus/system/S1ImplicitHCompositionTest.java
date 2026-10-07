package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;
import static totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest.AT;

/** Separate composition qualification; all authority is synthetic and supplied explicitly. */
class S1ImplicitHCompositionTest {
    @TempDir Path temp;
    static final Path MANIFEST=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/implicit-h-proxy-s1-v1/ATHENA.I03.HEAVY_ATOM_DIRECTIONAL_PROXY_SP3_AMINES.rule.json");
    record Sample(ImplicitHProxyAcceptanceTest.Fixture proxy,S1ResearchFixtures.Qualified authority) { }
    Sample sample(String pair,String variant)throws Exception {
        var p=pair.split(":");var fs=new ArrayList<B01FunctionalGroupAcceptanceTest.Fixture>();int index=0;
        for(var name:p) {
            var f=ChemicalRoleAcceptanceTest.chemical(switch(name){case "PRIMARY"->"methylamine";case "SECONDARY"->"dimethylamine";default->"trimethylamine";});
            double origin=index==0?0:variant.equals("far")?4:3,sign=index==0?1:-1;
            for(var a:f.graph().atoms())f=WaterBridgeFixtures.position(f,a.id(),a.element().equals("N")?origin:origin+sign*Math.cos(Math.toRadians(109.5)),a.element().equals("N")?0:Math.sin(Math.toRadians(109.5)),0);
            if(variant.equals("not-sp3")){var bond=f.graph().bonds().getFirst();var changed=new totah.lab.athena.design.backend.MolecularGraph.Bond(bond.id(),bond.firstAtomId(),bond.secondAtomId(),totah.lab.athena.design.backend.MolecularGraph.BondOrder.DOUBLE,false,bond.stereochemistry(),bond.properties());f=new B01FunctionalGroupAcceptanceTest.Fixture(new totah.lab.athena.design.backend.MolecularGraph(f.graph().atoms(),List.of(changed),f.graph().properties()),Map.of("a0",2,"a1",1));}
            if(variant.equals("explicit-donor")&&index==0)f=explicit(f);
            fs.add(f);index++;
        }
        var f=new S1NitrogenAcceptanceTest.Fixture(variant.equals("missing-none")?"missing-none":variant.equals("known-connection")?"known-connection":"primary",fs);
        var producer=S1ResearchFixtures.qualify(f.manifest,f.state,f.selected,temp,"producer");f.manifest=producer.manifest();f.inputs.addAll(producer.artifacts());
        if(!variant.equals("unqualified-scope"))S1QualifiedProducerTest.qualifyScope(f,temp);
        var findings=f.result();if(!Set.of("unqualified-scope","missing-none","known-connection","not-sp3").contains(variant))for(var finding:findings)assertEquals(EvidenceInterpretation.Status.SUPPORTED_PRESENT,finding.status(),finding.toString());
        if(variant.equals("not-sp3"))for(var finding:findings)assertEquals("NOT_SP3",finding.measurements().get("hybridization"));
        var proxy=new ImplicitHProxyAcceptanceTest.Fixture(new HbondCandidateFixtures.Sample(f.state,f.manifest,f.request(),f.inputs));
        proxy.manifest=RuleRegistry.decode(Files.readAllBytes(MANIFEST));
        for(var finding:findings) {
            if(variant.equals("missing-endpoint")&&finding.measurements().get("atom").contains("\"residueNumber\":2"))continue;
            var deps=f.inputs.stream().map(e->new EvidenceInterpretation.Input(e.reference(),e.payloadSha256())).toList();
            var method=RuleAnalyzers.evaluator(f.manifest,f.request()).method();
            var interpretation=new EvidenceInterpretation(ref(ScientificReference.Kind.EVIDENCE_INTERPRETATION,finding.id()),deps,method,Map.of(),finding.subjects(),finding.status(),finding.measurements(),finding.reasons(),finding.limitations(),Optional.empty(),AT);
            ((ArrayNode)proxy.plan.get("hybridizationEvidence")).add(proxy.add(new EvidenceExchange().encodeRecord(interpretation),method));
        }
        var composition=S1ResearchFixtures.qualify(proxy.manifest,f.state,List.of(),temp,"composition");proxy.manifest=composition.manifest();
        if(!variant.equals("no-inventory"))proxy.coverage(EvidenceInterpretation.Status.SUPPORTED_PRESENT);
        return new Sample(proxy,composition);
    }
    JsonNode evaluate(Sample sample,String variant)throws Exception {
        var f=sample.proxy();var inputs=new ArrayList<>(f.inputs());var request=sample.authority().request();
        var collector=RuleAnalyzers.collector(f.manifest,request);var raw=collector.analyze(f.state,inputs,Map.of()).getFirst();
        inputs.add(f.envelope("athena:rule-measurements",raw.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8),collector.method()));
        if(!variant.equals("no-composition-authority"))inputs.addAll(sample.authority().artifacts());
        var evaluated=RuleAnalyzers.evaluator(f.manifest,request).analyze(f.state,inputs,Map.of("trustSP3","true")).getFirst();
        var report=JSON.readTree(evaluated.measurements().get("payload"));assertEquals(evaluated.status().name(),report.path("assessment").asText());return report;
    }
    @ParameterizedTest @ValueSource(strings={"PRIMARY:PRIMARY","PRIMARY:SECONDARY","PRIMARY:TERTIARY","SECONDARY:PRIMARY","SECONDARY:SECONDARY","SECONDARY:TERTIARY"})
    void sixIndependentlyQualifiedPairs(String pair)throws Exception {var r=evaluate(sample(pair,"valid"),"valid");assertEquals("SUPPORTED_PRESENT",r.path("assessment").asText(),r.toPrettyString());assertTrue(r.path("complete").asBoolean());assertEquals(1,r.path("candidates").size());assertEquals("HEAVY_ATOM_DIRECTIONAL_PROXY",r.path("geometryKind").asText());}
    @ParameterizedTest @ValueSource(strings={"far","no-inventory","missing-endpoint","unqualified-scope","missing-none","explicit-donor","no-composition-authority","known-connection","not-sp3"})
    void qualificationAndNegativeCoverageRemainSeparate(String variant)throws Exception {
        var r=evaluate(sample("PRIMARY:PRIMARY",variant),variant);
        String expected=switch(variant){case "far"->"ABSENT_FALSE";case "no-inventory"->"SUPPORTED_PRESENT";case "no-composition-authority"->"NOT_EVALUATED";default->"UNKNOWN_INCONCLUSIVE";};
        assertEquals(expected,r.path("assessment").asText(),r.toPrettyString());assertEquals(variant.equals("far"),r.path("complete").asBoolean());
    }
    @Test void currentPolicyCompositionUsesItsOwnClockAndReceipt()throws Exception {
        var sample=sample("PRIMARY:PRIMARY","valid");var f=sample.proxy();var research=sample.authority();var inputs=new ArrayList<>(f.inputs());inputs.addAll(research.artifacts());
        inputs.removeIf(e->e.evidenceType().equals("athena:rule-qualification-receipt")&&e.reference().id().startsWith("composition/"));
        var context=research.artifacts().stream().filter(e->e.evidenceType().equals("athena:rule-policy-context")).findFirst().orElseThrow();
        var implementation=research.artifacts().stream().filter(e->{try{return JSON.readTree(e.readPayload()).path("schema").asText().equals("athena-rule-implementation-qualification/2");}catch(Exception ignored){return false;}}).findFirst().orElseThrow();
        var catalog=new EvidenceSnapshotCatalog(Files.createDirectory(temp.resolve("current")));var pipeline=pipeline();var foundation=pipeline.run(catalog,Optional.empty(),f.state,List.of(),Map.of(),List.of(),ref(ScientificReference.Kind.ACTIVITY,"composition-foundation"),AT);
        var executor=new RuleExecutionPipeline(pipeline,OCL,(ctx,at)->{if(!at.equals(AT)||!ctx.issuer().equals(totah.lab.athena.system.rules.research.ResearchV2Fixtures.EXECUTOR))throw new java.io.IOException("synthetic current time mismatch");});
        var result=executor.runCurrent(catalog,foundation,Map.of(),new RuleRegistry().register(f.manifest),totah.lab.athena.system.rules.research.ResearchDocuments.encode(f.manifest),research.request(),Optional.empty(),new RuleExecutionPipeline.ResearchExecutionInputs(inputs,ResearchGatePipelineAcceptanceTest.pin(context.readPayload()),ResearchGatePipelineAcceptanceTest.pin(implementation.readPayload())),ref(ScientificReference.Kind.ACTIVITY,"composition-current"),AT);
        var history=catalog.read(result.published().catalogSnapshot()).orElseThrow().history();var evaluations=history.interpretations().values().stream().filter(i->i.evaluator().namespace().equals("athena.implicit-h-proxy-s1")&&i.evaluator().id().endsWith("/evaluate")).toList();assertEquals(1,evaluations.size(),history.interpretations().values().stream().filter(i->i.status()==EvidenceInterpretation.Status.FAILED).toList().toString());assertEquals(EvidenceInterpretation.Status.SUPPORTED_PRESENT,evaluations.getFirst().status(),evaluations.toString());
    }
    public static void main(String[] args)throws Exception {var t=new S1ImplicitHCompositionTest();t.temp=Files.createDirectories(Path.of(args[0]));Files.write(Path.of(args[1]),SystemStateView.bytes(t.evaluate(t.sample("PRIMARY:PRIMARY","valid"),"valid")));}
}
