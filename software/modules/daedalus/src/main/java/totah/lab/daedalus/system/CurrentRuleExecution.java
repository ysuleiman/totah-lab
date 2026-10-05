package totah.lab.daedalus.system;

import totah.lab.athena.design.backend.SubstructureMatcher;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.athena.system.rules.research.*;
import totah.lab.mnemosyne.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Evidence-first current-policy orchestration. No scientific domain or legacy rule is changed here. */
final class CurrentRuleExecution {
    private final SystemQualificationPipeline pipeline;
    private final SubstructureMatcher matcher;
    private final ResearchTimeAuthority timeAuthority;
    CurrentRuleExecution(SystemQualificationPipeline pipeline,SubstructureMatcher matcher,ResearchTimeAuthority timeAuthority){this.pipeline=pipeline;this.matcher=matcher;this.timeAuthority=timeAuthority;}
    private static final ScientificReference METHOD=new ScientificReference(ScientificReference.Kind.METHOD,"daedalus.research","current-execution","1");
    @FunctionalInterface private interface Check { Map<String,String> apply()throws Exception; }
    private static SystemGraphAnalyzer check(String stage,Check check){return new SystemGraphAnalyzer(){
        public ScientificReference method(){return new ScientificReference(ScientificReference.Kind.METHOD,"daedalus.research",stage,"1");}
        public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
        public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
        public Set<String> evidenceTypes(){return Set.of("athena:system-state");}
        public List<Finding> analyze(SystemStateView state,List<EvidenceEnvelope> input,Map<String,String> config)throws Exception {
            var values=check.apply();var status=values.getOrDefault("eligible","true").equals("true")?EvidenceInterpretation.Status.SUPPORTED_PRESENT:EvidenceInterpretation.Status.UNSUPPORTED;
            return List.of(new Finding(stage,List.of(state.subject()),status,values,List.of("administrative gate result; not an interaction negative"),List.of("supplied source evidence preserved before evaluation")));
        }
    };}
    RuleExecutionPipeline.Result run(EvidenceSnapshotCatalog catalog,SystemQualificationPipeline.Published foundation,
            Map<String,String> foundationConfiguration,RuleRegistry registry,byte[] manifestBytes,RuleRequest request,
            Optional<EvidenceEnvelope> reuse,RuleExecutionPipeline.ResearchExecutionInputs research,ScientificReference run,Instant at)throws IOException {
        var state=foundation.state();var inputs=new ArrayList<>(research.artifacts());
        inputs.add(envelope(run,"manifest","athena:rule-manifest",manifestBytes,state,at,METHOD));
        inputs.add(envelope(run,"request","athena:rule-request",SystemStateView.bytes(request),state,at,METHOD));
        inputs.add(envelope(run,"foundation","athena:system-certificate",SystemStateView.bytes(foundation.certificate()),state,at,METHOD));
        inputs.add(envelope(run,"binding","athena:system-binding",SystemStateView.bytes(state.binding()),state,at,METHOD));
        reuse.ifPresent(inputs::add);
        // No parsing, gate, clock callback or scientific collector runs before this admission/read-back.
        var current=step(catalog,foundation,state,inputs,List.of(),run,"admission",at);
        var reader=reader(catalog,current,inputs);var selected=new RuleManifest[1];var context=new RulePolicyContext[1];
        var guard=check("definition",()->{
            var m=RuleRegistry.decode(manifestBytes);if(!m.schema().equals("athena-rule/3"))throw new IOException("current mode requires /3");
            registry.require(request.manifestKey(),request.manifestSha256());
            if(!m.key().equals(request.manifestKey())||!RuleRegistry.digest(m).equals(request.manifestSha256())||!state.binding().equals(request.state()))throw new IOException("rule/request/state mismatch");
            if(!foundation.certificate().binding().equals(state.binding())||!foundation.certificate().configurationSha256().equals(SystemStateView.digest(new TreeMap<>(foundationConfiguration))))throw new IOException("foundation binding/configuration mismatch");
            selected[0]=m;
            return Map.of("manifest",RuleRegistry.digest(m));
        });
        current=step(catalog,current,state,inputs,List.of(guard),run,"definition",at);
        if(!success(catalog,current,guard))return new RuleExecutionPipeline.Result(current,reuse);
        var m=selected[0];EvidenceEnvelope measurement;
        if(reuse.isPresent())measurement=reuse.orElseThrow();
        else {
            var collector=RuleAnalyzers.collector(m,request,matcher);
            current=step(catalog,current,state,inputs,List.of(collector),run,"collection",at);
            var payload=payload(catalog,current,collector);
            if(payload.isEmpty())return new RuleExecutionPipeline.Result(current,Optional.empty());
            measurement=envelope(run,"measurements",m.implementationId().equals("athena.group")?"athena:group-identities":"athena:rule-measurements",payload.orElseThrow(),state,at,collector.method());
        }
        inputs.add(measurement);
        current=step(catalog,current,state,inputs,List.of(),run,"measurements",at);
        var researchReader=reader(catalog,current,inputs);var eligibility=new ResearchEligibility[1];
        var gate=check("research-eligibility",()->{
            context[0]=ResearchDocuments.decode(researchReader.read(research.policyContext()),RulePolicyContext.class);
            if(timeAuthority==null)throw new IOException("CURRENT execution requires an application time authority");
            timeAuthority.verifyCurrent(context[0],at);
            var policy=ResearchDocuments.decode(researchReader.read(m.research().reviewPolicy()),RuleReviewPolicy.class);
            eligibility[0]=new ScientificRuleResearchGate().evaluate(m,policy,context[0],researchReader,at,QualificationMode.CURRENT);
            return Map.of("eligible",Boolean.toString(eligibility[0].eligible()),"payload",new String(ResearchDocuments.encode(eligibility[0]),StandardCharsets.UTF_8));
        });
        current=step(catalog,current,state,inputs,List.of(gate),run,"research",at);
        if(eligibility[0]==null)return new RuleExecutionPipeline.Result(current,Optional.of(measurement));
        inputs.add(envelope(run,"eligibility","athena:rule-research-eligibility",ResearchDocuments.encode(eligibility[0]),state,at,gate.method()));
        current=step(catalog,current,state,inputs,List.of(),run,"eligibility",at);
        if(!eligibility[0].eligible())return new RuleExecutionPipeline.Result(current,Optional.of(measurement));
        var qualificationReader=reader(catalog,current,inputs);var receipt=new RuleQualificationReceipt[1];
        var qualification=check("rule-qualification",()->{
            var implementation=ResearchDocuments.decode(qualificationReader.read(research.implementationReport()),RuleImplementationQualification.class);
            receipt[0]=RuleQualification.qualify(m,eligibility[0],implementation,foundation.certificate(),state,request,context[0],qualificationReader,at);
            return Map.of("payload",new String(ResearchDocuments.encode(receipt[0]),StandardCharsets.UTF_8));
        });
        current=step(catalog,current,state,inputs,List.of(qualification),run,"qualification",at);
        if(receipt[0]==null)return new RuleExecutionPipeline.Result(current,Optional.of(measurement));
        inputs.add(envelope(run,"qualified-rule","athena:rule-qualification-receipt",ResearchDocuments.encode(receipt[0]),state,at,qualification.method()));
        current=step(catalog,current,state,inputs,List.of(),run,"receipt",at);
        var finalReader=reader(catalog,current,inputs);var evaluator=RuleAnalyzers.evaluator(m,request);
        var guarded=new SystemGraphAnalyzer(){
            public ScientificReference method(){return evaluator.method();}
            public Set<SystemGraphCertificate.Capability> requires(){return evaluator.requires();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return evaluator.evidenceTypes();}
            public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> e,Map<String,String> config)throws Exception {
                timeAuthority.verifyCurrent(context[0],at);
                registry.requireCurrent(m.key(),RuleRegistry.digest(m),receipt[0],context[0],finalReader,at);
                return evaluator.analyze(s,e,config);
            }
        };
        current=step(catalog,current,state,inputs,List.of(guarded),run,"evaluation",at);
        return new RuleExecutionPipeline.Result(current,Optional.of(measurement));
    }
    private SystemQualificationPipeline.Published step(EvidenceSnapshotCatalog c,SystemQualificationPipeline.Published p,SystemStateView s,List<EvidenceEnvelope> e,List<SystemGraphAnalyzer> a,ScientificReference run,String phase,Instant at)throws IOException {
        return pipeline.run(c,Optional.of(p.catalogSnapshot()),s,e,Map.of(),a,new ScientificReference(run.kind(),run.namespace(),run.id()+"/"+phase,run.version()),at);
    }
    private static EvidenceEnvelope envelope(ScientificReference run,String id,String type,byte[] bytes,SystemStateView s,Instant at,ScientificReference method){return SystemQualificationPipeline.envelope(run,id,type,bytes,method,s.subject(),at,List.of("administrative research qualification; no biological conclusion"));}
    private static boolean success(EvidenceSnapshotCatalog c,SystemQualificationPipeline.Published p,SystemGraphAnalyzer a)throws IOException {
        var h=c.read(p.catalogSnapshot()).orElseThrow().history();return p.certificate().interpretations().stream().map(h.interpretations()::get).anyMatch(i->i.evaluator().equals(a.method())&&i.status()==EvidenceInterpretation.Status.SUPPORTED_PRESENT);
    }
    private static Optional<byte[]> payload(EvidenceSnapshotCatalog c,SystemQualificationPipeline.Published p,SystemGraphAnalyzer a)throws IOException {
        var h=c.read(p.catalogSnapshot()).orElseThrow().history();return p.certificate().interpretations().stream().map(h.interpretations()::get)
                .filter(i->i.evaluator().equals(a.method())&&i.status()==EvidenceInterpretation.Status.SUPPORTED_PRESENT&&i.measurements().containsKey("payload"))
                .map(i->i.measurements().get("payload").getBytes(StandardCharsets.UTF_8)).findFirst();
    }
    private static ResearchArtifactReader reader(EvidenceSnapshotCatalog catalog,SystemQualificationPipeline.Published p,List<EvidenceEnvelope> selected)throws IOException {
        var stored=catalog.read(p.catalogSnapshot()).orElseThrow(()->new IOException("missing explicit snapshot")).history().envelopes();
        var bytes=new HashMap<String,byte[]>();
        for(var e:selected){var durable=stored.get(e.reference());if(durable==null)throw new IOException("selected artifact not admitted");var value=durable.readPayload();
            var prior=bytes.putIfAbsent(durable.payloadSha256(),value);if(prior!=null&&!Arrays.equals(prior,value))throw new IOException("conflicting content identity");}
        return source->{var value=bytes.get(source.sha256());if(value==null)throw new IOException("missing explicit artifact pin: "+source.locator());
            if(!EvidenceExchange.sha256(value).equals(source.sha256()))throw new IOException("artifact checksum mismatch");return value.clone();};
    }
}
