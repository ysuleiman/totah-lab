package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.system.*;
import totah.lab.mnemosyne.*;
import totah.lab.athena.system.rules.research.ResearchDocuments;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** V07-only reviewed reference applicability. Does not perceive links or alter source chemistry. */
final class RestraintSource {
    static final String FACT="ATHENA.V07.TRANS_PROFILE_APPLICABILITY_SOURCE_FACT";
    static final List<String> ROLES=List.of("N","CA","C","O","CB","OG","OG1","CG1","CG2");
    record Checked(JsonNode binding, EvidenceInterpretation.Status referenceStatus,
                   EvidenceInterpretation.Status correspondenceStatus, List<EvidenceEnvelope> witnesses,
                   Map<String,EvidenceInterpretation.Status> links) {
        EvidenceInterpretation.Status applicability(){return ResidueContextSource.combine(links.values());}
    }
    private RestraintSource() { }
    static byte[] resource(String name)throws IOException {
        try(var in=RestraintSource.class.getResourceAsStream("/totah/lab/athena/system/rules/restraint-deviation-v1/"+name)) {
            if(in==null)throw new IOException("Missing pinned V07 resource "+name);return in.readAllBytes();
        }
    }
    static JsonNode rows()throws IOException{return JSON.readTree(resource("REFERENCE_ROWS.json")).get("rows");}
    static JsonNode specs()throws IOException {
        byte[] bytes=resource("SOURCES.json");require(EvidenceExchange.sha256(bytes).equals("6daf43c444cde56676131d5dec6359800ea0099db3a0a98b8cc8427745f36a6d"),"V07 source manifest digest");
        return JSON.readTree(bytes);
    }
    static JsonNode spec(String name)throws IOException {for(var s:specs())if(s.path("file").asText().equals(name))return s;throw new IOException("Unknown V07 artifact "+name);}
    static boolean artifact(JsonNode p,String name,Map<String,EvidenceEnvelope> inputs)throws IOException {
        var s=spec(name);require(p!=null&&p.isObject(),"Exact artifact pin");fields(p,"reference","sha256");reference(p.get("reference"));
        require(text(p,"sha256").equals(s.path("sha256").asText()),"Unapproved V07 reference digest");
        require(EvidenceExchange.sha256(resource(name)).equals(s.path("sha256").asText()),"Changed packaged V07 reference");
        var matches=inputs.values().stream().filter(e->canonical(pin(e)).equals(canonical(p))).toList();
        if(matches.isEmpty()){require(inputs.values().stream().noneMatch(e->JSON.valueToTree(e.reference()).equals(p.get("reference"))),"Reference identity supplied with changed bytes");return false;}require(matches.size()==1&&matches.getFirst().evidenceType().equals("athena:source-artifact"),"Reference artifact type");pinned(p,inputs);return true;
    }
    static boolean profile(JsonNode n,Map<String,EvidenceEnvelope> inputs)throws IOException {
        fields(n,"identity","repository","commit","profileManifest","referenceRows","sourceArtifacts","licenseArtifacts","centralModifications","hydrogenRestraints","numericProfile");
        var expected=JSON.readTree(resource("REFERENCE_PROFILE.json"));
        require(text(n,"identity").equals(expected.path("profile").asText()),"V07 profile identity");
        for(var k:List.of("repository","commit","centralModifications","hydrogenRestraints","numericProfile"))require(n.get(k).equals(expected.get(k.equals("centralModifications")?"centralReferenceModifications":k)),"V07 exact profile "+k);
        boolean complete=artifact(n.get("profileManifest"),"REFERENCE_PROFILE.json",inputs)&artifact(n.get("referenceRows"),"REFERENCE_ROWS.json",inputs);
        for(var key:List.of("sourceArtifacts","licenseArtifacts")) {
            var supplied=array(n,key);var wanted=expected.get(key.equals("sourceArtifacts")?"artifacts":"licenseArtifacts");require(supplied.size()==wanted.size(),"Complete V07 artifact pin inventory");
            String prior="";var hashes=new HashSet<String>();for(var p:supplied){String c=canonical(p);require(c.compareTo(prior)>0,"Sorted unique reference pins");prior=c;hashes.add(text(p,"sha256"));}
            for(var s:wanted){JsonNode selected=null;for(var a:supplied)if(a.path("sha256").equals(s.get("sha256")))selected=a;require(selected!=null,"Exact reference digest inventory");String path=s.path("path").asText();complete&=artifact(selected,path.substring(path.lastIndexOf('/')+1),inputs);}
        }return complete;
    }
    static void pins(JsonNode n,Map<String,EvidenceEnvelope> inputs,Set<String> deps,boolean ordered)throws IOException {
        require(n.isArray(),"Pin array");String prior="";var seen=new HashSet<String>();for(var p:n){String key=canonical(p);require(seen.add(key)&&(ordered||key.compareTo(prior)>0),"Pin ordering/uniqueness");prior=key;pinned(p,inputs);if(deps!=null)require(deps.contains(key),"Witness input omitted consumed pin");}
    }
    static void locations(JsonNode n,Map<String,EvidenceEnvelope> inputs,Set<String> deps)throws IOException {
        require(n.isArray(),"Locator array");var seen=new HashSet<String>();for(var l:n){fields(l,"artifact","selector");text(l,"selector");require(seen.add(canonical(l)),"Duplicate source locator");var e=pinned(l.get("artifact"),inputs);require(e.evidenceType().equals("athena:source-artifact"),"Original source artifact required");if(deps!=null)require(deps.contains(canonical(l.get("artifact"))),"Locator dependency omitted");}
    }
    static void strings(JsonNode n){require(n.isArray(),"String array");for(var v:n)require(v.isTextual()&&!v.asText().isBlank(),"Nonblank string");}
    static Checked check(SystemStateView s,EvidenceEnvelope envelope,EvidenceEnvelope context,ResidueContextSource.Checked c,Map<String,EvidenceEnvelope> inputs)throws Exception {
        var n=read(envelope);fields(n,"schema","definition","stateBinding","contextBinding","centralResidue","candidateIdentity","referenceProfile","linkBindings","atomCorrespondence","preparationReferences","limitations");
        require(text(n,"schema").equals("athena-restraint-source-reference-binding/1")&&text(n,"definition").equals(RestraintDeviationRules.ID+"/1"),"Exact V07 binding contract");
        require(n.get("stateBinding").equals(JSON.valueToTree(s.binding()))&&n.get("contextBinding").equals(JSON.valueToTree(pin(context)))&&n.get("centralResidue").equals(c.binding().path("central").path("residue")),"V07 immutable context binding");
        require(n.get("candidateIdentity").isNull()||Set.of("SER","THR","VAL").contains(n.get("candidateIdentity").asText()),"V07 candidate enum");
        var correspondence=(Objects.equals(n.get("candidateIdentity"),c.binding().path("central").get("candidateIdentity"))||(n.get("candidateIdentity").isNull()&&c.identity("central")!=null&&!Set.of("SER","THR","VAL").contains(c.identity("central"))))?SUPPORTED_PRESENT:UNKNOWN_INCONCLUSIVE;
        pins(n.get("preparationReferences"),inputs,null,true);require(n.get("preparationReferences").equals(c.binding().get("preparationReferences")),"Preparation lineage binding");strings(n.get("limitations"));
        boolean complete=profile(n.get("referenceProfile"),inputs);var roles=array(n,"atomCorrespondence");require(roles.size()==ROLES.size(),"Exact role inventory");
        var sourceIds=new HashSet<String>();var coordinateIds=new HashSet<String>();
        for(int i=0;i<ROLES.size();i++) {var row=roles.get(i);fields(row,"role","source","coordinate");String role=ROLES.get(i);require(text(row,"role").equals(role),"Exact role order");
            if(!row.get("source").isNull()){fields(row.get("source"),"component","atomId");reference(row.path("source").get("component"));text(row.get("source"),"atomId");}if(!row.get("coordinate").isNull())ResidueContextSource.coordinate(row.get("coordinate"));
            var source=c.binding().path("central").path("roles").get(role);var coord=c.coordinate("central",role);
            if(!row.get("source").equals(source)||!row.get("coordinate").equals(JSON.valueToTree(coord)))correspondence=UNKNOWN_INCONCLUSIVE;
            require(!row.get("source").isNull()||row.get("coordinate").isNull(),"Coordinate without source");
            if(!row.get("source").isNull())require(sourceIds.add(canonical(row.get("source"))),"Source collision");if(!row.get("coordinate").isNull())require(coordinateIds.add(canonical(row.get("coordinate"))),"Coordinate collision");
        }
        var links=array(n,"linkBindings");require(links.size()==2,"Two reference links");for(int i=0;i<2;i++) {
            var l=links.get(i);fields(l,"direction","from","to","referenceLink","centralComponent","sourceLocations");linkShape(l,i);locations(l.get("sourceLocations"),inputs,null);
            if(!Objects.equals(l.get("from"),c.binding().path(i==0?"previous":"central").path("roles").get("C"))||!Objects.equals(l.get("to"),c.binding().path(i==0?"central":"next").path("roles").get("N")))correspondence=UNKNOWN_INCONCLUSIVE;
        }
        var statuses=new LinkedHashMap<String,EvidenceInterpretation.Status>();statuses.put("incoming",UNKNOWN_INCONCLUSIVE);statuses.put("outgoing",UNKNOWN_INCONCLUSIVE);
        var witnesses=new ArrayList<EvidenceEnvelope>();JsonNode first=null;
        for(var e:inputs.values())if(e.evidenceType().equals("athena:event-source")) {
            var rec=new EvidenceExchange().decodeRecord(e.readPayload());if(!(rec instanceof EvidenceInterpretation w)||!Objects.equals(w.measurements().get("proposition"),FACT+"/1"))continue;
            var v=w.measurements();require(v.keySet().equals(Set.of("proposition","stateBinding","contextBinding","sourceReferenceBinding","factValues","sourceProtocol","sourceLocations","preparationReferences")),"Exact V07 witness fields");
            if(!JSON.readTree(v.get("sourceReferenceBinding")).equals(JSON.valueToTree(pin(envelope))))continue;
            require(w.evaluator().equals(e.method())&&w.subjects().contains(s.subject()),"Profile witness method/subject");
            require(JSON.readTree(v.get("stateBinding")).equals(n.get("stateBinding"))&&JSON.readTree(v.get("contextBinding")).equals(n.get("contextBinding")),"Witness exact state/context");
            var deps=new HashSet<String>();for(var in:w.inputs()){var pp=JSON.valueToTree(Map.of("reference",in.reference(),"sha256",in.sha256()));boolean referenceArtifact=false;for(var sp:specs())if(sp.path("sha256").asText().equals(in.sha256()))referenceArtifact=true;if(!referenceArtifact||inputs.values().stream().anyMatch(x->x.reference().equals(in.reference())))pinned(pp,inputs);deps.add(canonical(pp));}
            require(deps.contains(canonical(pin(envelope)))&&deps.contains(canonical(pin(context))),"Binding dependencies required");
            var proto=JSON.readTree(v.get("sourceProtocol"));require(pinned(proto,inputs).evidenceType().equals("athena:source-artifact")&&deps.contains(canonical(proto)),"Independent source protocol pin");
            locations(JSON.readTree(v.get("sourceLocations")),inputs,deps);var prep=JSON.readTree(v.get("preparationReferences"));pins(prep,inputs,deps,true);require(prep.equals(n.get("preparationReferences")),"Witness preparation equality");
            var f=JSON.readTree(v.get("factValues"));fields(f,"centralResidue","referenceProfile","centralModifications","linkFacts","sourceStateDescription","sourceEvidence","reasons");text(f,"sourceStateDescription");strings(f.get("reasons"));
            require(f.get("centralResidue").equals(n.get("centralResidue"))&&f.get("referenceProfile").equals(n.get("referenceProfile"))&&f.get("centralModifications").equals(n.path("referenceProfile").get("centralModifications")),"Witness reference assignment");
            pins(f.get("sourceEvidence"),inputs,deps,false);for(var pp:f.get("sourceEvidence"))original(pp,inputs,envelope);
            var facts=array(f,"linkFacts");require(facts.size()==2,"Both link facts");var nextStatuses=new LinkedHashMap<String,EvidenceInterpretation.Status>();
            for(int i=0;i<2;i++){var l=facts.get(i);fields(l,"direction","from","to","referenceLink","centralComponent","applicability","sourceLocations","applicabilityEvidence","rationale");linkShape(l,i);text(l,"rationale");locations(l.get("sourceLocations"),inputs,deps);pins(l.get("applicabilityEvidence"),inputs,deps,false);for(var pp:l.get("applicabilityEvidence"))original(pp,inputs,envelope);
                require(Set.of("APPLICABLE","EXCLUDED","UNRESOLVED").contains(text(l,"applicability")),"Applicability enum");var status=switch(text(l,"applicability")){case "APPLICABLE"->SUPPORTED_PRESENT;case "EXCLUDED"->UNSUPPORTED;default->UNKNOWN_INCONCLUSIVE;};
                if(l.get("from").isNull()||l.get("to").isNull()||!l.get("from").equals(links.get(i).get("from"))||!l.get("to").equals(links.get(i).get("to"))||l.get("sourceLocations").isEmpty()||l.get("applicabilityEvidence").isEmpty()||f.get("sourceEvidence").isEmpty()||JSON.readTree(v.get("sourceLocations")).isEmpty()||w.status()!=SUPPORTED_PRESENT)status=UNKNOWN_INCONCLUSIVE;
                nextStatuses.put(i==0?"incoming":"outgoing",status);
            }
            witnesses.add(e);if(first==null){first=f;statuses.putAll(nextStatuses);}else if(!first.equals(f)){statuses.replaceAll((k,z)->UNKNOWN_INCONCLUSIVE);}else for(var k:statuses.keySet())statuses.put(k,ResidueContextSource.combine(List.of(statuses.get(k),nextStatuses.get(k))));
        }
        return new Checked(n,complete?SUPPORTED_PRESENT:UNKNOWN_INCONCLUSIVE,correspondence,List.copyOf(witnesses),Map.copyOf(statuses));
    }
    private static void original(JsonNode p,Map<String,EvidenceEnvelope> inputs,EvidenceEnvelope binding)throws IOException {
        var e=pinned(p,inputs);require(!e.reference().equals(binding.reference())&&(e.evidenceType().equals("athena:source-artifact")||e.evidenceType().equals("athena:event-source")),"Original/profile evidence only");
        if(e.evidenceType().equals("athena:event-source")){var w=(EvidenceInterpretation)new EvidenceExchange().decodeRecord(e.readPayload());require(Objects.equals(w.measurements().get("proposition"),"ATHENA.V09_V10.RESIDUE_CONTEXT_SOURCE_FACT/1"),"Only original residue source witness may support profile review");}
    }
    private static void linkShape(JsonNode l,int i){require(text(l,"direction").equals(i==0?"INCOMING":"OUTGOING")&&text(l,"referenceLink").equals("TRANS")&&l.path("centralComponent").isIntegralNumber()&&l.path("centralComponent").intValue()==(i==0?2:1),"Exact directed reference link");}
    static boolean authorized(Checked c,SystemStateView s,Map<String,EvidenceEnvelope> inputs,Instant at)throws Exception {
        var reviewed=new ArrayList<Set<String>>();for(var e:inputs.values())if(e.evidenceType().equals("athena:rule-manifest")){var m=ResearchDocuments.decode(e.readPayload(),RuleManifest.class);if(!m.ruleId().equals(FACT)||!m.schema().equals("athena-rule/3"))continue;var time=S1Qualification.current(m,s,null,inputs);if(time.isPresent()&&time.get().equals(at))reviewed.add(new HashSet<>(m.scientificSources().stream().map(RuleManifest.Source::sha256).toList()));}
        if(c.witnesses().isEmpty())return false;
        for(var e:c.witnesses()){var w=(EvidenceInterpretation)new EvidenceExchange().decodeRecord(e.readPayload());var needed=new HashSet<String>();needed.add(e.payloadSha256());for(var d:w.inputs())needed.add(d.sha256());if(!w.recordedAt().equals(at)||reviewed.stream().noneMatch(h->h.containsAll(needed)))return false;}return true;
    }
}
