package totah.lab.daedalus.system;

import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.athena.system.rules.research.*;
import totah.lab.mnemosyne.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/** Evidence-first composition of the existing qualification pipeline, not a new journal. */
public final class RuleExecutionPipeline {
    private final SystemQualificationPipeline pipeline;
    private final totah.lab.athena.design.backend.SubstructureMatcher matcher;
    private final ResearchTimeAuthority timeAuthority;
    public RuleExecutionPipeline(SystemQualificationPipeline pipeline){this(pipeline,null);}
    public RuleExecutionPipeline(SystemQualificationPipeline pipeline,totah.lab.athena.design.backend.SubstructureMatcher matcher){this(pipeline,matcher,null);}
    public RuleExecutionPipeline(SystemQualificationPipeline pipeline,totah.lab.athena.design.backend.SubstructureMatcher matcher,ResearchTimeAuthority timeAuthority){this.pipeline=Objects.requireNonNull(pipeline);this.matcher=matcher;this.timeAuthority=timeAuthority;}
    public record ResearchExecutionInputs(List<EvidenceEnvelope> artifacts,RuleManifest.Source policyContext,RuleManifest.Source implementationReport) {
        public ResearchExecutionInputs { artifacts=List.copyOf(artifacts);Objects.requireNonNull(policyContext);Objects.requireNonNull(implementationReport); }
    }
    public Result runCurrent(EvidenceSnapshotCatalog catalog,SystemQualificationPipeline.Published foundation,
            Map<String,String> foundationConfiguration,RuleRegistry registry,byte[] manifestBytes,RuleRequest request,
            Optional<EvidenceEnvelope> reuseMeasurements,ResearchExecutionInputs research,ScientificReference run,Instant at)throws IOException {
        return new CurrentRuleExecution(pipeline,matcher,timeAuthority).run(catalog,foundation,foundationConfiguration,registry,manifestBytes,request,reuseMeasurements,research,run,at);
    }
    /** Direct Findings under current policy; only explicitly selected applicable evidence is evaluated. */
    public SystemQualificationPipeline.Published evaluateCurrent(EvidenceSnapshotCatalog catalog,
            SystemQualificationPipeline.Published foundation,Map<String,String> foundationConfiguration,
            RuleRegistry registry,byte[] manifestBytes,RuleRequest request,ResearchExecutionInputs research,
            ScientificReference run,Instant at)throws IOException {
        return new CurrentRuleExecution(pipeline,matcher,timeAuthority).evaluate(catalog,foundation,
                foundationConfiguration,registry,manifestBytes,request,research,run,at);
    }
    public record Result(SystemQualificationPipeline.Published published,Optional<EvidenceEnvelope> measurements) { }
    public Result run(EvidenceSnapshotCatalog catalog,SystemQualificationPipeline.Published foundation,
                      Map<String,String> foundationConfiguration,RuleRegistry registry,byte[] manifestBytes,RuleRequest request,
                      Optional<EvidenceEnvelope> reuseMeasurements,ScientificReference run,Instant at)throws IOException {
        var state=foundation.state();var sourceMethod=new ScientificReference(ScientificReference.Kind.METHOD,"athena.rules","definition-admission","1");
        var manifestEnvelope=SystemQualificationPipeline.envelope(run,"manifest","athena:rule-manifest",manifestBytes,sourceMethod,RuleAnalyzers.subjects(state,request),at,List.of("source bytes retained even if invalid"));
        var requestEnvelope=SystemQualificationPipeline.envelope(run,"request","athena:rule-request",SystemStateView.bytes(request),sourceMethod,RuleAnalyzers.subjects(state,request),at,List.of("explicit selection, not implicit current state"));
        var guard=new SystemGraphAnalyzer(){
            public ScientificReference method(){return sourceMethod;}
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return Set.of("athena:rule-manifest","athena:rule-request","athena:system-state");}
            public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> evidence,Map<String,String> config)throws Exception {
                var m=RuleRegistry.decode(manifestEnvelope.readPayload());
                if(m.schema().equals("athena-rule/3"))throw new IllegalArgumentException("/3 requires runCurrent; no historical fallback");
                registry.require(request.manifestKey(),request.manifestSha256());
                if(!m.key().equals(request.manifestKey())||!RuleRegistry.digest(m).equals(request.manifestSha256())||!s.binding().equals(request.state()))throw new IllegalArgumentException("manifest/request/state mismatch");
                foundation.certificate().require(SystemGraphCertificate.Capability.DISTANCE_QUERIES,s,foundationConfiguration,foundation.certificate().evidenceSnapshot(),foundation.certificate().analyzers(),false);
                return List.of(new Finding("definition",List.of(s.subject()),EvidenceInterpretation.Status.SUPPORTED_PRESENT,Map.of("manifestSha256",RuleRegistry.digest(m)),List.of("registered manifest and explicit foundation bindings verified"),m.limitations()));
            }
        };
        var admitted=pipeline.run(catalog,Optional.of(foundation.catalogSnapshot()),state,List.of(manifestEnvelope,requestEnvelope),Map.of(),List.of(guard),phase(run,"definition"),at);
        if(!successful(catalog,admitted,guard.method()))return new Result(admitted,Optional.empty());
        var manifest=RuleRegistry.decode(manifestBytes);EvidenceEnvelope measurements;
        var parent=admitted;
        if(reuseMeasurements.isPresent())measurements=reuseMeasurements.orElseThrow();
        else {
            var collector=RuleAnalyzers.collector(manifest,request,matcher);
            parent=pipeline.run(catalog,Optional.of(admitted.catalogSnapshot()),state,List.of(manifestEnvelope,requestEnvelope),Map.of(),List.of(collector),phase(run,"collection"),at);
            if(!successful(catalog,parent,collector.method()))return new Result(parent,Optional.empty());
            var finding=catalog.read(parent.catalogSnapshot()).orElseThrow().history().interpretations().values().stream()
                    .filter(i->i.evaluator().equals(collector.method())&&i.reference().id().startsWith(run.id()+"/collection/")&&i.measurements().containsKey("payload")).findFirst().orElseThrow();
            measurements=SystemQualificationPipeline.envelope(run,"measurements","athena:rule-measurements",finding.measurements().get("payload").getBytes(StandardCharsets.UTF_8),collector.method(),RuleAnalyzers.subjects(state,request),at,
                    List.of("collection interpretation="+finding.reference(),"collection snapshot="+parent.catalogSnapshot(),"measurement values within explicit collection scope; native clash output retains its protocol-bound selection"));
        }
        var evaluated=pipeline.run(catalog,Optional.of(parent.catalogSnapshot()),state,List.of(manifestEnvelope,requestEnvelope,measurements),Map.of(),List.of(RuleAnalyzers.evaluator(manifest,request)),phase(run,"evaluation"),at);
        return new Result(evaluated,Optional.of(measurements));
    }
    private static boolean successful(EvidenceSnapshotCatalog catalog,SystemQualificationPipeline.Published p,ScientificReference method)throws IOException {
        var h=catalog.read(p.catalogSnapshot()).orElseThrow().history();
        return p.certificate().interpretations().stream().map(h.interpretations()::get).anyMatch(i->i.evaluator().equals(method)&&i.status()==EvidenceInterpretation.Status.SUPPORTED_PRESENT);
    }
    private static ScientificReference phase(ScientificReference r,String suffix){return new ScientificReference(r.kind(),r.namespace(),r.id()+"/"+suffix,r.version());}
}
