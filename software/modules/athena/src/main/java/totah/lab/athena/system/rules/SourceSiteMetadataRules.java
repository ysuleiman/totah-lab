package totah.lab.athena.system.rules;

import totah.lab.athena.system.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Governed V18 source-token metadata; no chemical eligibility or conformer selection. */
final class SourceSiteMetadataRules {
    static final String ID="ATHENA.V18.MMCIF_SOURCE_SITE_METADATA";
    private SourceSiteMetadataRules() { }
    static void validate(RuleManifest m){
        require(Set.of("athena-rule/2","athena-rule/3").contains(m.schema())&&m.ruleId().equals(ID)&&m.version().equals("1.0.0")&&m.profile().equals("ATHENA_V18_SOURCE_METADATA_V1")&&m.implementationVersion().equals("1")&&m.qualification()==SystemGraphCertificate.Status.NOT_EVALUATED&&m.family()==RuleManifest.Family.VALIDATOR&&m.requiredCapabilities().isEmpty(),"V18 manifest contract");
        try(var in=SourceSiteMetadataRules.class.getResourceAsStream("source-site-metadata-v1/"+ID+".rule.json")){require(in!=null,"V18 definition missing");var d=JSON.readTree(in);require(JSON.valueToTree(m.parameters()).equals(d.get("parameters"))&&JSON.valueToTree(m.scientificSources()).equals(d.get("scientificSources"))&&JSON.valueToTree(m.negativeCoverage()).equals(d.get("negativeCoverage")),"V18 definition changed");}catch(java.io.IOException e){throw new IllegalArgumentException("V18 definition unreadable",e);}
    }
    static ScientificReference method(RuleManifest m,boolean evaluate){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.source-site-metadata",m.key()+(evaluate?"/evaluate":"/collect"),RuleRegistry.digest(m));}
    static SystemGraphAnalyzer analyzer(RuleManifest m,RuleRequest request,boolean evaluate){validate(m);return new SystemGraphAnalyzer(){
        public ScientificReference method(){return SourceSiteMetadataRules.method(m,evaluate);}
        public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
        public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
        public Set<String> evidenceTypes(){return Set.of("athena:source-site-metadata-plan","athena:source-site-metadata","athena:rule-measurements","athena:source-artifact","athena:rule-manifest","athena:rule-policy-context","athena:rule-qualification-receipt","athena:rule-research-eligibility","athena:rule-implementation-qualification","athena:system-binding","athena:system-certificate","athena:rule-request","athena:system-state");}
        public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> supplied,Map<String,String> config)throws Exception {
            require(s.binding().equals(request.state())&&m.key().equals(request.manifestKey())&&RuleRegistry.digest(m).equals(request.manifestSha256())&&request.atoms().isEmpty()&&request.first().isEmpty()&&request.second().isEmpty(),"V18 exact selected state request required");
            var inputs=index(supplied);var v=new TreeMap<String,String>();v.put("proposition",SourceSiteMetadata.DEFINITION);v.put("stateBinding",canonical(s.binding()));v.put("metadataCoverage","UNKNOWN");v.put("ensembleResolution","UNKNOWN");v.put("resolvedMembership","[]");v.put("limitations",canonical(m.limitations()));
            var plans=inputs.values().stream().filter(e->e.evidenceType().equals("athena:source-site-metadata-plan")).toList();if(plans.size()!=1)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,v,"One exact nonconflicting source plan required"));var pe=plans.getFirst();var plan=read(pe);require(binding(plan.get("state")).equals(s.binding()),"V18 plan state mismatch");
            if(!inputs.containsKey(canonical(plan.path("source").path("reference")))||!inputs.containsKey(canonical(plan.path("sourceProtocol").path("reference"))))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,v,"Original source or explicit mapping protocol artifact absent"));
            var source=pinned(plan.get("source"),inputs);require(source.evidenceType().equals("athena:source-artifact"),"Original source artifact required");var protocol=pinned(plan.get("sourceProtocol"),inputs);require(protocol.evidenceType().equals("athena:source-artifact")&&!protocol.payloadSha256().equals(source.payloadSha256()),"Independent original source mapping protocol required");
            com.fasterxml.jackson.databind.JsonNode raw;try{raw=SourceSiteMetadata.extract(source.readPayload(),plan,pin(pe));}catch(SourceSiteMetadata.Outside outside){return List.of(finding(s,UNSUPPORTED,v,outside.getMessage()));}
            v.put("payload",canonical(raw));v.put("sourceProtocol",canonical(pin(protocol)));v.put("selectedStateMapping",canonical(plan.path("correspondence")));
            if(!evaluate)return List.of(finding(s,SUPPORTED_PRESENT,v,"Raw source metadata extraction only; no scientific/source-mapping authority"));
            var reports=inputs.values().stream().filter(e->e.evidenceType().equals("athena:source-site-metadata")||e.evidenceType().equals("athena:rule-measurements")&&e.method().equals(SourceSiteMetadataRules.method(m,false))).toList();if(reports.size()!=1)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,v,"One exact preserved metadata report required"));var re=reports.getFirst();require(re.method().equals(SourceSiteMetadataRules.method(m,false))&&read(re).equals(raw),"V18 original-byte extraction replay mismatch");v.put("rawMetadataPin",canonical(pin(re)));
            var at=S1Qualification.current(m,s,request,inputs);if(at.isEmpty())return List.of(finding(s,NOT_EVALUATED,v,"Independent current V18 authority absent"));
            // Exact plan/protocol review is a separate existing Research Gate invocation, never inferred from parsing.
            boolean mappingAuthority=false;
            for(var e:inputs.values())if(e.evidenceType().equals("athena:rule-manifest")){
                var review=totah.lab.athena.system.rules.research.ResearchDocuments.decode(e.readPayload(),RuleManifest.class);
                if(!review.ruleId().equals(ID+".SOURCE_MAPPING")||!review.schema().equals("athena-rule/3")||review.scientificSources().stream().noneMatch(p->p.sha256().equals(pe.payloadSha256()))||review.scientificSources().stream().noneMatch(p->p.sha256().equals(protocol.payloadSha256())))continue;
                var time=S1Qualification.current(review,s,null,inputs);mappingAuthority|=time.isPresent()&&time.get().equals(at.get());
            }
            if(!mappingAuthority)return List.of(finding(s,NOT_EVALUATED,v,"Independent exact source plan and mapping protocol authority absent"));
            if(raw.path("atomSiteRows").size()>request.maximumNodes()||raw.path("alternateCategories").findValues("rows").stream().mapToInt(com.fasterxml.jackson.databind.JsonNode::size).sum()>request.maximumCandidates())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,v,"Complete source metadata exceeds selected coverage budget"));
            var rows=new HashSet<Integer>();var atoms=new HashSet<AtomReference>();boolean complete=true;
            for(var pair:array(plan,"correspondence")){fields(pair,"rowOrdinal","atom");int row=SourceSiteMetadata.integer(pair.get("rowOrdinal"));var atom=JSON.treeToValue(pair.get("atom"),AtomReference.class);require(rows.add(row)&&atoms.add(atom),"V18 noninjective source-state correspondence");complete&=row<raw.path("atomSiteRows").size()&&s.atoms().containsKey(atom);}
            if(!complete||!atoms.equals(s.atoms().keySet())||raw.path("atomSiteRows").isEmpty())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,v,"Exact exhaustive selected-state mapping unavailable"));
            v.put("metadataCoverage","COMPLETE_FOR_SELECTED_BLOCK");var members=SourceSiteMetadata.memberships(raw);if(members.isPresent()){v.put("ensembleResolution","EXPLICIT_MEMBERSHIP_RESOLVED");v.put("resolvedMembership",canonical(members.get()));}
            return List.of(finding(s,SUPPORTED_PRESENT,v,"Faithful bound source metadata; ensemble membership availability is separate and never chemical completeness/coexistence"));
        }
        private Finding finding(SystemStateView s,EvidenceInterpretation.Status status,Map<String,String> v,String reason){return new Finding(evaluate?"evaluate":"collect",List.of(s.subject()),status,v,List.of(reason),m.limitations());}
    };}
}
