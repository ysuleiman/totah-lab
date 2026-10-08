package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.system.*;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Additive lineage adapter for the unchanged qualified fragment-view selection only. */
final class SourceFragmentLineageRules {
    static final String ID="ATHENA.A08.SOURCE_FRAGMENT_PARENT_LINEAGE";
    private SourceFragmentLineageRules() { }
    static void validate(RuleManifest m){
        require(Set.of("athena-rule/2","athena-rule/3").contains(m.schema())&&m.ruleId().equals(ID)&&m.version().equals("1.0.0")&&m.profile().equals("ATHENA_A08_SOURCE_LINEAGE_V1")&&m.implementationVersion().equals("1")&&m.qualification()==SystemGraphCertificate.Status.NOT_EVALUATED&&m.family()==RuleManifest.Family.MOTIF&&m.requiredCapabilities().isEmpty(),"A08 lineage manifest contract");
        try(var in=SourceFragmentLineageRules.class.getResourceAsStream("source-fragment-lineage-v1/"+ID+".rule.json")){require(in!=null,"A08 lineage definition missing");var d=JSON.readTree(in);require(JSON.valueToTree(m.parameters()).equals(d.get("parameters"))&&JSON.valueToTree(m.scientificSources()).equals(d.get("scientificSources"))&&JSON.valueToTree(m.negativeCoverage()).equals(d.get("negativeCoverage")),"A08 lineage definition changed");}catch(java.io.IOException e){throw new IllegalArgumentException("A08 lineage definition unreadable",e);}
    }
    static ScientificReference method(RuleManifest m,boolean evaluate){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.source-fragment-lineage",m.key()+(evaluate?"/evaluate":"/collect"),RuleRegistry.digest(m));}
    static SystemGraphAnalyzer analyzer(RuleManifest m,RuleRequest request,boolean evaluate){validate(m);return new SystemGraphAnalyzer(){
        public ScientificReference method(){return SourceFragmentLineageRules.method(m,evaluate);}
        public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
        public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
        public Set<String> evidenceTypes(){return Set.of("athena:source-fragment-parent-lineage","athena:group-source-coverage","athena:rule-measurements","athena:event-source","athena:source-artifact","athena:rule-manifest","athena:rule-policy-context","athena:rule-qualification-receipt","athena:rule-research-eligibility","athena:rule-implementation-qualification","athena:system-binding","athena:system-certificate","athena:rule-request","athena:system-state");}
        public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> supplied,Map<String,String> config)throws Exception {
            require(s.binding().equals(request.state())&&m.key().equals(request.manifestKey())&&RuleRegistry.digest(m).equals(request.manifestSha256())&&request.atoms().isEmpty()&&request.first().isEmpty()&&request.second().isEmpty(),"A08 lineage exact source state request required");
            var inputs=index(supplied);var v=new TreeMap<String,String>();v.put("proposition",ID+"/1");v.put("payload",canonical(s.snapshot()));
            if(!evaluate)return List.of(finding(s,SUPPORTED_PRESENT,v,"Preserved raw immutable source snapshot only; no parent selection or DERIVED result"));
            var raws=inputs.values().stream().filter(e->e.evidenceType().equals("athena:rule-measurements")&&e.method().equals(SourceFragmentLineageRules.method(m,false))).toList();require(raws.size()==1&&read(raws.getFirst()).equals(JSON.readTree(canonical(s.snapshot()))),"A08 lineage source replay mismatch");
            var at=S1Qualification.current(m,s,request,inputs);if(at.isEmpty())return List.of(finding(s,NOT_EVALUATED,v,"Independent current lineage-adapter authority absent"));
            var parents=new ArrayList<EvidenceEnvelope>();
            for(var e:inputs.values())if(e.evidenceType().equals("athena:event-source")){var record=new EvidenceExchange().decodeRecord(e.readPayload());if(record instanceof EvidenceInterpretation i&&"UNIQUE_LARGEST_HEAVY_SOURCE_COMPONENT_VIEW".equals(i.measurements().get("proposition")))parents.add(e);}
            if(parents.size()!=1)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,v,"One exact original governed fragment-parent interpretation required"));
            var parent=parents.getFirst();var interpretation=(EvidenceInterpretation)new EvidenceExchange().decodeRecord(parent.readPayload());
            require(parent.method().equals(interpretation.evaluator())&&!interpretation.subjects().isEmpty()&&interpretation.subjects().stream().allMatch(x->x.state().equals(s.identity())),"A08 original parent producer/subjects mismatch");
            // Governed pipeline interpretation inputs bind complete envelope records, not merely payload bytes.
            var deps=new ArrayList<EvidenceEnvelope>();var seen=new HashSet<String>();var exchange=new EvidenceExchange();
            for(var p:interpretation.inputs()){var e=inputs.get(canonical(p.reference()));if(e==null)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,v,"Original governed input artifact absent"));require(seen.add(canonical(p.reference()))&&exchange.contentDigest(e).equals(p.sha256()),"A08 exact original governed input record missing/changed");deps.add(e);}
            var manifests=new ArrayList<EvidenceEnvelope>();for(var e:deps)if(e.evidenceType().equals("athena:rule-manifest")&&read(e).path("ruleId").asText().equals(SourceFragmentParentRules.ID))manifests.add(e);
            if(manifests.size()!=1)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,v,"Unique exact original selection manifest required"));var manifestEnvelope=manifests.getFirst();var selection=RuleRegistry.decode(manifestEnvelope.readPayload());
            require(interpretation.evaluator().equals(SourceFragmentParentRules.method(selection,true)),"A08 wrong original selection implementation/profile");
            var snapshots=deps.stream().filter(e->e.evidenceType().equals("athena:system-state")).toList();require(snapshots.size()==1&&read(snapshots.getFirst()).equals(JSON.readTree(canonical(s.snapshot()))),"A08 exact original source snapshot required");
            RuleRequest selectedRequest=null;
            for(var e:deps)if(e.evidenceType().equals("athena:rule-qualification-receipt")){
                var receipt=totah.lab.athena.system.rules.research.ResearchDocuments.decode(e.readPayload(),totah.lab.athena.system.rules.research.RuleQualificationReceipt.class);
                if(!receipt.ruleKey().equals(selection.key())||!receipt.manifestSha256().equals(RuleRegistry.digest(selection)))continue;
                for(var de:deps)if(de.payloadSha256().equals(receipt.request().sha256())){var r=JSON.treeToValue(read(de),RuleRequest.class);require(selectedRequest==null||selectedRequest.equals(r),"A08 conflicting original requests");selectedRequest=r;}
            }
            if(selectedRequest==null)return List.of(finding(s,NOT_EVALUATED,v,"Original current qualified selection invocation unavailable"));
            var originalAt=S1Qualification.current(selection,s,selectedRequest,index(deps));if(originalAt.isEmpty()||!originalAt.get().equals(at.get())||!interpretation.recordedAt().equals(at.get()))return List.of(finding(s,NOT_EVALUATED,v,"Original selection needs independent authority at current invocation"));
            var verified=SourceFragmentParentRules.analyzer(selection,selectedRequest,true).analyze(s,deps,Map.of()).getFirst();require(verified.status()==interpretation.status()&&verified.measurements().equals(interpretation.measurements()),"A08 original result replay mismatch");
            if(verified.status()!=SUPPORTED_PRESENT)return List.of(finding(s,verified.status(),v,"Original parent selection did not qualify; no fallback or DERIVED state"));
            var selectedRef=reference(JSON.readTree(verified.measurements().get("selectedComponent")));var component=s.components().stream().filter(c->c.identity().equals(selectedRef)).findFirst().orElseThrow();
            var payload=receipt(s,m,selection,manifestEnvelope,snapshots.getFirst(),parent,component,deps);
            for(var e:inputs.values())if(e.evidenceType().equals("athena:source-fragment-parent-lineage"))require(e.method().equals(SourceFragmentLineageRules.method(m,true))&&read(e).equals(payload),"A08 supplied lineage payload replay mismatch");
            v.put("payload",canonical(payload));v.put("stateKind","DERIVED");v.put("resultingStateSha256",payload.path("resultingStateSha256").asText());
            return List.of(finding(s,SUPPORTED_PRESENT,v,"Exact immutable fragment-parent view lineage only; no chemical equivalence or downstream source admission"));
        }
        private Finding finding(SystemStateView s,EvidenceInterpretation.Status status,Map<String,String> v,String reason){return new Finding(evaluate?"evaluate":"collect",List.of(s.subject()),status,v,List.of(reason),m.limitations());}
    };}
    private static JsonNode receipt(SystemStateView s,RuleManifest m,RuleManifest selection,EvidenceEnvelope manifest,EvidenceEnvelope snapshot,EvidenceEnvelope parent,SystemStateView.Component selected,List<EvidenceEnvelope> deps)throws Exception {
        var p=JSON.createObjectNode();p.put("schema","athena-source-fragment-parent-lineage/1");p.put("stateKind","DERIVED");p.put("operation","FRAGMENT_PARENT");p.set("sourceState",JSON.valueToTree(s.binding()));p.set("sourceSnapshot",JSON.valueToTree(pin(snapshot)));p.put("sourceStateSha256",hash(s.snapshot()));p.set("parentInterpretation",JSON.valueToTree(pin(parent)));p.put("definition",ID+"/1");p.put("selectionDefinition",SourceFragmentParentRules.ID+"/1");p.set("selectionManifest",JSON.valueToTree(pin(manifest)));p.set("implementation",JSON.valueToTree(method(m,true)));p.set("profile",JSON.valueToTree(new ScientificReference(ScientificReference.Kind.POLICY,"athena.source-fragment-parent",selection.profile(),RuleRegistry.digest(selection))));
        var parameters=HbondCandidateRules.parameter(m,"lineageParameters");p.set("parameters",parameters);p.put("parametersSha256",hash(parameters));p.putArray("dataTables");p.set("selectedComponent",JSON.valueToTree(selected.identity()));p.set("resultingGraph",JSON.valueToTree(selected.chemistry()));
        var digest=JSON.createObjectNode();for(var key:List.of("stateKind","sourceStateSha256","operation","selectionManifest","implementation","profile","parametersSha256","dataTables","selectedComponent","resultingGraph"))digest.set(key,p.get(key));p.put("resultingStateSha256",hash(digest));
        var atoms=p.putArray("atomLineage");for(var a:selected.chemistry().atoms()){var n=atoms.addObject();n.set("sourceComponent",JSON.valueToTree(selected.identity()));n.put("sourceAtomId",a.id());n.put("derivedAtomId",a.id());n.set("sourceAtomReference",JSON.valueToTree(selected.correspondenceAlternatives().getFirst().get(a.id())));}
        var bonds=p.putArray("bondLineage");for(var b:selected.chemistry().bonds()){var n=bonds.addObject();n.set("sourceComponent",JSON.valueToTree(selected.identity()));n.put("sourceBondId",b.id());n.put("derivedBondId",b.id());}
        var excluded=p.putArray("excludedSourceComponents");var changes=p.putObject("changes");for(var key:List.of("addedAtoms","removedFromDerivedView","addedBonds","removedBondsFromDerivedView","changedAtoms","changedBonds","changedCoordinates"))changes.putArray(key);
        for(var c:s.components().stream().sorted(Comparator.comparing(x->canonical(x.identity()))).toList())if(!c.identity().equals(selected.identity())){
            var n=excluded.addObject();n.set("component",JSON.valueToTree(c.identity()));n.put("graphSha256",hash(c.chemistry()));n.set("atomIds",JSON.valueToTree(c.chemistry().atoms().stream().map(a->a.id()).toList()));n.set("bondIds",JSON.valueToTree(c.chemistry().bonds().stream().map(b->b.id()).toList()));
            for(var a:c.chemistry().atoms())((com.fasterxml.jackson.databind.node.ArrayNode)changes.get("removedFromDerivedView")).add(JSON.valueToTree(Map.of("sourceComponent",c.identity(),"sourceAtomId",a.id())));
            for(var b:c.chemistry().bonds())((com.fasterxml.jackson.databind.node.ArrayNode)changes.get("removedBondsFromDerivedView")).add(JSON.valueToTree(Map.of("sourceComponent",c.identity(),"sourceBondId",b.id())));
        }
        p.set("sourceWitnesses",JSON.valueToTree(deps.stream().map(EventPayload::pin).sorted(Comparator.comparing(EventPayload::canonical)).toList()));return p;
    }
}
