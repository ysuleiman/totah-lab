package totah.lab.daedalus.system;

import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.athena.system.rules.research.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
import static totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest.AT;

/** Synthetic engineering governance only. Never a production receipt or source protocol. */
final class S1ResearchFixtures {
    record Qualified(RuleManifest manifest,RuleRequest request,List<EvidenceEnvelope> artifacts) { }
    static Qualified qualify(RuleManifest source,SystemStateView state,List<totah.lab.gaia.structure.AtomReference> atoms,Path temp,String name)throws Exception {
        return qualify(source,state,atoms,temp,name,Map.of());
    }
    static Qualified qualify(RuleManifest source,SystemStateView state,List<totah.lab.gaia.structure.AtomReference> atoms,Path temp,String name,Map<String,byte[]> suppliedSources)throws Exception {
        var f=S1ResearchFixture.create(source,"valid",suppliedSources);var m=f.manifest();
        var request=new RuleRequest(state.binding(),m.key(),RuleRegistry.digest(m),atoms,List.of(),List.of(),0.1,0,10000,10000);
        var foundation=pipeline().run(new EvidenceSnapshotCatalog(Files.createDirectories(temp.resolve(name))),Optional.empty(),state,List.of(),Map.of(),List.of(),ref(ScientificReference.Kind.ACTIVITY,name),AT);
        var eligibility=f.run();var bytes=new TreeMap<String,byte[]>(f.bytes());
        var implementation=new RuleImplementationQualificationV2("athena-rule-implementation-qualification/2",m.key(),m.research().definitionSha256(),m.research().domain().sha256(),RuleRegistry.digest(m),List.of(f.raw()),List.of(f.raw()),List.of(new RuleImplementationQualification.Check("synthetic-positive",true,f.raw(),"engineering fixture only")),ResearchV2Fixtures.EXECUTOR,f.raw(),AT);
        for(var b:List.of(ResearchDocuments.encode(eligibility),ResearchDocuments.encode(implementation),SystemStateView.bytes(foundation.certificate()),SystemStateView.bytes(state.binding()),SystemStateView.bytes(request)))bytes.put(EvidenceExchange.sha256(b),b);
        ResearchArtifactReader reader=p->{var b=bytes.get(p.sha256());if(b==null)throw new java.io.IOException("missing fixture "+p);return b;};
        var receipt=RuleQualification.qualify(m,eligibility,implementation,foundation.certificate(),state,request,f.context(),reader,AT);
        var artifacts=new ArrayList<EvidenceEnvelope>();int count=0;
        for(var b:bytes.values())artifacts.add(envelope(state,name,"source"+count++,"athena:source-artifact",b));
        artifacts.add(envelope(state,name,"manifest","athena:rule-manifest",ResearchDocuments.encode(m)));
        artifacts.add(envelope(state,name,"context","athena:rule-policy-context",ResearchDocuments.encode(f.context())));
        artifacts.add(envelope(state,name,"receipt","athena:rule-qualification-receipt",ResearchDocuments.encode(receipt)));
        return new Qualified(m,request,List.copyOf(artifacts));
    }
    static EvidenceEnvelope envelope(SystemStateView state,String name,String id,String type,byte[] bytes){return SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,name),id,type,bytes,ResearchV2Fixtures.EXECUTOR.kind()==ScientificReference.Kind.METHOD?ResearchV2Fixtures.EXECUTOR:ref(ScientificReference.Kind.METHOD,"synthetic-research"),state.subject(),AT,List.of("synthetic engineering authority only"));}
}
