package totah.lab.athena.system.rules;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.system.*;
import totah.lab.mnemosyne.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** B01 payload interpreter. All chemical queries execute through the injected B00 matcher. */
final class FunctionalGroupRules {
    private FunctionalGroupRules() { }
    private static final Comparator<List<String>> ATOM_ORDER=(a,b)-> {
        for(int i=0;i<Math.min(a.size(),b.size());i++){int c=a.get(i).compareTo(b.get(i));if(c!=0)return c;}
        return Integer.compare(a.size(),b.size());
    };
    private static final String MATCHER="2026.7.2/athena-ocl-occurrences/2";
    private static final ObjectMapper JSON=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final List<String> NEGATIVE=List.of("VALID_DEFINITION","COMPLETE_GRAPH","COMPLETE_CHARGE_STATE",
            "REQUIRED_H_STATE","AROMATICITY_MODEL","SUPPORTED_DOMAIN","EXHAUSTIVE_B00");

    static void validate(RuleManifest m) {
        if(!Set.of("athena-rule/2","athena-rule/3").contains(m.schema())||!Set.of("1","2","3").contains(m.implementationVersion())
                ||!m.profile().equals(m.implementationVersion().equals("3")?"ATHENA_GROUP_MAPPING_V3":m.implementationVersion().equals("2")?"ATHENA_GROUP_CONTEXT_V2":"ATHENA_GROUP_B01_V1")||m.family()!=RuleManifest.Family.MOTIF
                ||!m.requiredCapabilities().isEmpty()||!m.parameters().keySet().equals(Set.of("definition"))
                ||!m.negativeCoverage().requirements().equals(NEGATIVE))throw new IllegalArgumentException("B01 manifest contract");
        try {definition(m);}catch(Exception e){throw new IllegalArgumentException("invalid group definition",e);}
    }
    private static JsonNode definition(RuleManifest m)throws Exception {
        var d=JSON.readTree(m.parameters().get("definition").value());
        var expectedFields=new HashSet<>(Set.of("schema","groupId","definitionVersion","patternId","patternVersion","query","memberQueryIndices","roles",
                "contextPolicy","requiredState","supportedDomain","negativeCoverageVersion","limitations","sourceReferences","requiredMatcher"));
        boolean local=!m.implementationVersion().equals("1");
        if(local)expectedFields.add("occurrenceExclusions");
        fields(d,expectedFields);
        equal(d,"schema",local?"athena-group-definition/2":"athena-group-definition/1");equal(d,"groupId",m.ruleId());equal(d,"definitionVersion",m.version());equal(d,"requiredMatcher",MATCHER);
        equal(d,"contextPolicy","ALL_ONE_BOND_NEIGHBORS_PLUS_QUERY_CONTEXT");
        for(String key:List.of("definitionVersion","patternId","patternVersion","query","negativeCoverageVersion"))text(d,key);
        if(!text(d,"negativeCoverageVersion").equals(m.negativeCoverage().version()))throw new IllegalArgumentException("negative policy mismatch");
        if(!d.path("memberQueryIndices").isArray()||d.path("memberQueryIndices").isEmpty()||!d.path("roles").isObject()
                ||d.path("roles").isEmpty()||!d.path("requiredState").isObject()||!d.path("supportedDomain").isObject())throw new IllegalArgumentException("group declarations required");
        indices(d.get("memberQueryIndices"));d.get("roles").forEach(FunctionalGroupRules::indices);
        var required=d.get("requiredState");
        var allowed=Set.of("graphCompleteness","formalCharge","hydrogenElements","hydrogenRoles","aromaticity","roleHeavyDegree","roleCharges","hydrogenConsistencyQueries");
        required.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw new IllegalArgumentException("unknown source-state requirement: "+k);});
        for(String flag:List.of("graphCompleteness","formalCharge","aromaticity"))if(!required.path(flag).isBoolean())throw new IllegalArgumentException("explicit state requirement required");
        if(!required.path("graphCompleteness").asBoolean()||!required.path("formalCharge").asBoolean())throw new IllegalArgumentException("group state coverage cannot be disabled");
        if(!required.path("hydrogenRoles").isArray()||!required.path("hydrogenElements").isArray())throw new IllegalArgumentException("H requirement arrays required");
        for(var h:required.get("hydrogenRoles")) {
            fields(h,Set.of("role","count"));
            if(!d.get("roles").has(text(h,"role"))||!h.path("count").isIntegralNumber()||h.path("count").intValue()<0)throw new IllegalArgumentException("invalid authoritative H requirement");
        }
        fields(d.get("supportedDomain"),Set.of("elements","unsupportedQueries","coveredAtomQuery"));
        if(!d.get("supportedDomain").path("elements").isArray()||d.get("supportedDomain").path("elements").isEmpty()||!d.get("supportedDomain").path("unsupportedQueries").isArray()||!d.get("supportedDomain").path("coveredAtomQuery").isTextual())throw new IllegalArgumentException("explicit domain required");
        for(var exclusion:d.get("supportedDomain").get("unsupportedQueries")){fields(exclusion,Set.of("query","reason"));text(exclusion,"query");text(exclusion,"reason");}
        references(d.get("sourceReferences"));
        if(local)validateExclusions(d);
        return d;
    }
    static SystemGraphAnalyzer analyzer(RuleManifest m,RuleRequest r,SubstructureMatcher matcher,boolean evaluate) {
        validate(m);
        return new SystemGraphAnalyzer() {
            public ScientificReference method(){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.groups",m.key()+ (evaluate?"/evaluate":"/collect"),RuleRegistry.digest(m));}
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return evaluate?Set.of("athena:group-identities","athena:system-state"):Set.of("athena:group-source-coverage","athena:group-definition","athena:system-state");}
            public List<Finding> analyze(SystemStateView state,List<EvidenceEnvelope> inputs,Map<String,String> config)throws Exception {
                if(!state.binding().equals(r.state())||!m.key().equals(r.manifestKey())||!RuleRegistry.digest(m).equals(r.manifestSha256()))throw new IllegalArgumentException("group request binding");
                if(!r.atoms().isEmpty()||!r.first().isEmpty()||!r.second().isEmpty())throw new IllegalArgumentException("B01 scope is the explicit complete component in source coverage");
                var relevant=inputs.stream().filter(e->e.evidenceType().equals(evaluate?"athena:group-identities":"athena:group-source-coverage")).toList();
                if(relevant.isEmpty())return List.of(finding(state,UNKNOWN_INCONCLUSIVE,Map.of(),"source coverage/report unavailable"));
                if(relevant.size()!=1)throw new IllegalArgumentException("one explicitly selected coverage/report required; no implicit conflict resolution");
                JsonNode report;
                if(!evaluate) {
                    var definitions=inputs.stream().filter(e->e.evidenceType().equals("athena:group-definition")).toList();
                    if(definitions.size()!=1)throw new IllegalArgumentException("one preserved group definition required");
                    if(!JSON.readTree(definitions.getFirst().readPayload()).equals(definition(m)))throw new IllegalArgumentException("definition evidence/manifest mismatch");
                }
                if(evaluate) {
                    var expectedMethod=new ScientificReference(ScientificReference.Kind.METHOD,"athena.groups",m.key()+"/collect",RuleRegistry.digest(m));
                    if(!relevant.getFirst().method().equals(expectedMethod))throw new IllegalArgumentException("group report collector provenance mismatch");
                    report=JSON.readTree(relevant.getFirst().readPayload());
                    equal(report,"schema",!m.implementationVersion().equals("1")?"athena-group-identities/2":"athena-group-identities/1");
                    if(!report.path("sourceStateBinding").equals(node(state.binding()))||!report.path("definitionDigest").asText().equals(hash(definition(m))))throw new IllegalArgumentException("report binding mismatch");
                    // Recompute all identities and assessments from preserved query results and source coverage.
                    report=build(state,m,r,report.get("sourceCoverage"),null,report.get("b00Results"));
                    if(!report.equals(JSON.readTree(relevant.getFirst().readPayload())))throw new IllegalArgumentException("inconsistent/tampered group report");
                } else report=build(state,m,r,JSON.readTree(relevant.getFirst().readPayload()),matcher,null);
                var status=EvidenceInterpretation.Status.valueOf(report.get("assessment").asText());
                if(m.retired()||(!m.schema().equals("athena-rule/3")&&m.qualification()!=SystemGraphCertificate.Status.QUALIFIED))status=NOT_EVALUATED;
                return List.of(finding(state,evaluate?status:SUPPORTED_PRESENT,Map.of("payload",report.toString(),"assessment",status.name(),"reportDigest",hash(report)),"group identity only; no interaction or biological inference"));
            }
            private Finding finding(SystemStateView s,EvidenceInterpretation.Status status,Map<String,String> values,String reason) {
                return new Finding(evaluate?"evaluate":"collect",List.of(s.subject()),status,values,List.of(reason),m.limitations());
            }
        };
    }

    private static JsonNode build(SystemStateView state,RuleManifest manifest,RuleRequest request,JsonNode coverage,
                                  SubstructureMatcher matcher,JsonNode replay)throws Exception {
        var d=definition(manifest);
        fields(coverage,Set.of("schema","stateBinding","componentReference","completeGraph","atomState","sourceReferences","limitations"));
        equal(coverage,"schema","athena-group-source-coverage/1");
        if(!coverage.path("stateBinding").equals(node(state.binding())))throw new IllegalArgumentException("coverage state mismatch");
        references(coverage.get("sourceReferences"));
        var component=state.components().stream().filter(c->node(c.identity()).equals(coverage.get("componentReference"))).findFirst().orElseThrow(()->new IllegalArgumentException("missing source component"));
        var graph=component.chemistry();graph.validateTopology(false);
        var source=coverage.get("atomState");if(source==null||!source.isObject())throw new IllegalArgumentException("atom coverage object required");
        var proof=new TreeMap<String,Boolean>();NEGATIVE.forEach(k->proof.put(k,true));
        proof.put("COMPLETE_GRAPH",!graph.atoms().isEmpty()&&text(coverage,"completeGraph").equals(SUPPORTED_PRESENT.name())&&component.correspondenceAlternatives().size()==1);
        var reasons=new TreeSet<String>();
        var hCounts=new TreeMap<String,Integer>();
        boolean sourceContradiction=false;
        var allowed=new HashSet<String>();d.path("supportedDomain").path("elements").forEach(x->allowed.add(x.asText()));
        for(var a:graph.atoms()) {
            if(!allowed.contains(a.element()))proof.put("SUPPORTED_DOMAIN",false);
            var s=source.get(a.id());
            if(s==null) {proof.put("COMPLETE_CHARGE_STATE",false);proof.put("REQUIRED_H_STATE",false);proof.put("AROMATICITY_MODEL",false);continue;}
            fields(s,Set.of("chargeStatus","formalCharge","hydrogenMode","explicitHydrogenAtomIds","implicitHydrogenCount","aromaticityStatus","aromaticityModel","evidenceReferences"));
            references(s.get("evidenceReferences"));
            if(!text(s,"chargeStatus").equals(SUPPORTED_PRESENT.name()))proof.put("COMPLETE_CHARGE_STATE",false);
            else if(!s.path("formalCharge").isIntegralNumber()||s.get("formalCharge").intValue()!=a.formalCharge())sourceContradiction=true;
            var actualH=new TreeSet<String>();
            for(var b:graph.bonds()) {
                var neighbor=b.firstAtomId().equals(a.id())?b.secondAtomId():b.secondAtomId().equals(a.id())?b.firstAtomId():null;
                if(neighbor!=null&&graph.atom(neighbor).orElseThrow().element().equals("H")) {
                    if(b.order()!=MolecularGraph.BondOrder.SINGLE)sourceContradiction=true;
                    actualH.add(neighbor);
                }
            }
            if(!s.path("explicitHydrogenAtomIds").isArray())throw new IllegalArgumentException("explicit H IDs must be an array");
            var declaredH=new TreeSet<String>();s.get("explicitHydrogenAtomIds").forEach(x->{if(!x.isTextual()||!declaredH.add(x.asText()))throw new IllegalArgumentException("invalid/duplicate explicit H ID");});
            if(!declaredH.equals(actualH))sourceContradiction=true;
            String mode=text(s,"hydrogenMode");
            if(!Set.of("UNKNOWN","EXPLICIT_GRAPH","AUTHORITATIVE_IMPLICIT").contains(mode))throw new IllegalArgumentException("unknown H provenance mode");
            if(!mode.equals("UNKNOWN")) {
                var implicit=s.get("implicitHydrogenCount");
                if(implicit==null||!implicit.isIntegralNumber()||implicit.intValue()<0)throw new IllegalArgumentException("authoritative H count required");
                if(mode.equals("EXPLICIT_GRAPH")&&implicit.intValue()!=0)sourceContradiction=true;
                if(a.explicitHydrogens()>0&&a.explicitHydrogens()!=implicit.intValue())sourceContradiction=true;
                hCounts.put(a.id(),actualH.size()+implicit.intValue());
            }
            if(contains(d.path("requiredState").path("hydrogenElements"),a.element())&&!hCounts.containsKey(a.id()))proof.put("REQUIRED_H_STATE",false);
            if(d.path("requiredState").path("aromaticity").asBoolean()&&(!text(s,"aromaticityStatus").equals(SUPPORTED_PRESENT.name())
                    ||!text(s,"aromaticityModel").equals("OCL/2026.7.2")))proof.put("AROMATICITY_MODEL",false);
        }
        var ids=graph.atoms().stream().map(MolecularGraph.Atom::id).collect(java.util.stream.Collectors.toSet());
        source.fieldNames().forEachRemaining(id->{if(!ids.contains(id))throw new IllegalArgumentException("unknown coverage atom "+id);});
        if(component.correspondenceAlternatives().size()==1) {
            var map=component.correspondenceAlternatives().getFirst();
            if(!map.keySet().equals(ids)||new HashSet<>(map.values()).size()!=ids.size())proof.put("COMPLETE_GRAPH",false);
            for(var a:graph.atoms()) {
                var ref=map.get(a.id());var target=state.atoms().get(ref);
                if(target==null||target.getElement()==null||!target.getElement().name().equalsIgnoreCase(a.element()))proof.put("COMPLETE_GRAPH",false);
                if(ref==null||!state.charges().charges().containsKey(ref)||state.charges().charge(ref)!=a.formalCharge())proof.put("COMPLETE_CHARGE_STATE",false);
            }
            var actual=new HashSet<totah.lab.gaia.structure.Bond>();
            state.graph().structure().bonds().stream().filter(b->map.containsValue(b.atom1())||map.containsValue(b.atom2())).forEach(actual::add);
            var mapped=new HashSet<totah.lab.gaia.structure.Bond>();
            // V3 preserves incomplete mapping as missing coverage. V1/V2 replay keeps its historical behavior.
            boolean constructMappedBonds=!manifest.implementationVersion().equals("3")
                    ||graph.bonds().stream().allMatch(b->map.get(b.firstAtomId())!=null&&map.get(b.secondAtomId())!=null
                        &&!map.get(b.firstAtomId()).equals(map.get(b.secondAtomId())));
            if(constructMappedBonds)graph.bonds().forEach(b->mapped.add(new totah.lab.gaia.structure.Bond(map.get(b.firstAtomId()),map.get(b.secondAtomId()),totah.lab.gaia.chemistry.BondOrder.valueOf(b.order().name()))));
            else proof.put("COMPLETE_GRAPH",false);
            if(!actual.equals(mapped))proof.put("COMPLETE_GRAPH",false);
        }
        var results=JSON.createObjectNode();
        var result=query(text(d,"query"),graph,matcher,replay,results);
        var hConsistency=new HashMap<Integer,Set<String>>();
        boolean contextRequiresH=false;
        for(var exclusion:d.path("occurrenceExclusions"))contextRequiresH|=!exclusion.path("hydrogenRoles").isEmpty();
        for(var atom:graph.atoms())if((contains(d.path("requiredState").path("hydrogenElements"),atom.element())||(contextRequiresH&&!atom.element().equals("H")))&&hCounts.containsKey(atom.id())) {
            int count=hCounts.get(atom.id());
            var constraint=d.path("requiredState").path("hydrogenConsistencyQueries").get(Integer.toString(count));
            if(constraint==null){proof.put("SUPPORTED_DOMAIN",false);reasons.add("source H count outside reviewed consistency-query domain");continue;}
            if(!hConsistency.containsKey(count))hConsistency.put(count,query(constraint.asText(),graph,matcher,replay,results).queryToTargetAtomIds().stream().flatMap(x->x.values().stream()).collect(java.util.stream.Collectors.toSet()));
            if(!hConsistency.get(count).contains(atom.id()))sourceContradiction=true;
        }
        if(!manifest.implementationVersion().equals("1")&&sourceContradiction) {
            proof.put("REQUIRED_H_STATE",false);proof.put("COMPLETE_CHARGE_STATE",false);
        }
        var members=new TreeMap<List<String>,SortedSet<String>>(ATOM_ORDER);
        var alternatives=new TreeMap<List<String>,SortedSet<String>>(ATOM_ORDER);
        var contexts=new TreeMap<List<String>,SortedSet<String>>(ATOM_ORDER);
        var memberIndices=indices(d.get("memberQueryIndices"));
        var roleIndices=new TreeMap<String,List<Integer>>();d.get("roles").fields().forEachRemaining(e->roleIndices.put(e.getKey(),indices(e.getValue())));
        var contextAssessments=new ArrayList<JsonNode>();
        var exclusions=new TreeMap<String,List<JsonNode>>();
        for(var exclusion:d.path("occurrenceExclusions")) {
            var receipt=query(text(exclusion,"query"),graph,matcher,replay,results);
            exclusions.put(text(exclusion,"id"),embeddings(receipt));
        }
        boolean localStateMissing=false;
        boolean unknownContext=false;
        for(var message:result.evidence().messages())if(message.startsWith("occurrenceEmbeddings=")) {
            var embeddings=JSON.readTree(message.substring("occurrenceEmbeddings=".length())).get("compiledQueryIndexToTarget");
            for(var embedding:embeddings) {
                var group=new TreeSet<String>();for(int i:memberIndices)group.add(mapped(embedding,i,ids));
                var roleMap=new TreeMap<String,List<String>>();
                for(var role:roleIndices.entrySet()) {var values=new ArrayList<String>();for(int i:role.getValue())values.add(mapped(embedding,i,ids));roleMap.put(role.getKey(),List.copyOf(values));}
                boolean known=true;
                for(var constraint:d.path("requiredState").path("hydrogenRoles")) {
                    String role=text(constraint,"role");int required=constraint.path("count").intValue();
                    if(!roleMap.containsKey(role))throw new IllegalArgumentException("unknown H role");
                    for(var atom:roleMap.get(role)) {
                        if(!hCounts.containsKey(atom)){known=false;localStateMissing=true;}
                        else if(hCounts.get(atom)!=required){known=false;sourceContradiction=true;}
                    }
                }
                for(var constraints:List.of("roleHeavyDegree","roleCharges")) {
                    var entries=d.path("requiredState").path(constraints).fields();
                    while(entries.hasNext()) {
                        var constraint=entries.next();
                        if(!roleMap.containsKey(constraint.getKey()))throw new IllegalArgumentException("unknown state role");
                        for(String atom:roleMap.get(constraint.getKey())) {
                            int actual=constraints.equals("roleCharges")?graph.atom(atom).orElseThrow().formalCharge():
                                    (int)graph.bonds().stream().filter(b->(b.firstAtomId().equals(atom)&&!graph.atom(b.secondAtomId()).orElseThrow().element().equals("H"))
                                    ||(b.secondAtomId().equals(atom)&&!graph.atom(b.firstAtomId()).orElseThrow().element().equals("H"))).count();
                            if(actual!=constraint.getValue().intValue()){known=false;proof.put("SUPPORTED_DOMAIN",false);}
                        }
                    }
                }
                boolean excluded=false;
                boolean uncertain=false;
                for(var exclusion:d.path("occurrenceExclusions")) {
                    var decision=contextDecision(exclusion,exclusions.get(text(exclusion,"id")),roleMap,
                            group,ids,hCounts,proof,coverage);
                    contextAssessments.add(decision);
                    excluded|=decision.get("status").asText().equals("EXCLUDED_BY_CONTEXT");
                    uncertain|=decision.get("status").asText().equals("UNKNOWN_CONTEXT");
                }
                if(!excluded&&uncertain)unknownContext=true;
                if(!known||excluded||uncertain)continue;
                var key=List.copyOf(group);members.putIfAbsent(key,group);
                alternatives.computeIfAbsent(key,k->new TreeSet<>()).add(new String(SystemStateView.bytes(roleMap),StandardCharsets.UTF_8));
                var context=contexts.computeIfAbsent(key,k->new TreeSet<>());
                embedding.forEach(x->{if(!group.contains(x.asText()))context.add(x.asText());});
                for(var b:graph.bonds()) {if(group.contains(b.firstAtomId())&&!group.contains(b.secondAtomId()))context.add(b.secondAtomId());if(group.contains(b.secondAtomId())&&!group.contains(b.firstAtomId()))context.add(b.firstAtomId());}
            }
        }
        if(Math.max(result.queryToTargetAtomIds().size(),members.size())>request.maximumCandidates()) {proof.put("EXHAUSTIVE_B00",false);reasons.add("candidate budget exceeded; raw complete results retained");}
        for(var exclusion:d.path("supportedDomain").path("unsupportedQueries")) {
            if(!query(text(exclusion,"query"),graph,matcher,replay,results).queryToTargetAtomIds().isEmpty()) {proof.put("SUPPORTED_DOMAIN",false);reasons.add(text(exclusion,"reason"));}
        }
        var cover=d.path("supportedDomain").path("coveredAtomQuery");
        if(cover.isTextual()&&!cover.asText().isEmpty()) {
            var required=query(cover.asText(),graph,matcher,replay,results).queryToTargetAtomIds().stream().flatMap(x->x.values().stream()).collect(java.util.stream.Collectors.toSet());
            var multiplicity=new HashMap<String,Integer>();members.values().forEach(set->set.forEach(a->multiplicity.merge(a,1,Integer::sum)));
            if(!multiplicity.keySet().equals(required)||multiplicity.values().stream().anyMatch(n->n!=1))proof.put("SUPPORTED_DOMAIN",false);
        }
        if(sourceContradiction){proof.put("REQUIRED_H_STATE",false);proof.put("COMPLETE_CHARGE_STATE",false);reasons.add("source-state assertion contradicts graph/query; never repaired by inference");}
        if(localStateMissing)reasons.add("matched occurrence lacks authoritative H state");
        var occurrences=JSON.createArrayNode();
        boolean positiveAllowed=proof.get("COMPLETE_GRAPH")&&proof.get("COMPLETE_CHARGE_STATE")&&proof.get("AROMATICITY_MODEL")&&proof.get("SUPPORTED_DOMAIN")&&!sourceContradiction;
        if(positiveAllowed)for(var key:members.keySet()) {
            var group=members.get(key);var all=new TreeSet<>(group);all.addAll(contexts.get(key));
            var occurrence=JSON.createObjectNode();
            occurrence.put("occurrenceId",SystemStateView.digest(Map.of("state",state.binding(),"definition",hash(d),"members",group)));
            occurrence.set("memberAtomIds",node(group));occurrence.set("contextAtomIds",node(contexts.get(key)));
            var roles=JSON.createArrayNode();for(String value:alternatives.get(key))roles.add(JSON.readTree(value));occurrence.set("roleCorrespondenceAlternatives",roles);
            occurrence.set("sourceBondIds",node(graph.bonds().stream().filter(b->all.contains(b.firstAtomId())&&all.contains(b.secondAtomId())).map(MolecularGraph.Bond::id).sorted().toList()));
            occurrence.set("explicitHydrogenAtomIds",node(all.stream().filter(a->graph.atom(a).orElseThrow().element().equals("H")).toList()));
            occurrence.set("sourceStateReferences",node(List.of(component.identity())));occurrences.add(occurrence);
        }
        if(unknownContext||(!manifest.implementationVersion().equals("1")&&localStateMissing)){proof.put("REQUIRED_H_STATE",false);reasons.add("occurrence exclusion context is incomplete; no negative inference");}
        boolean complete=proof.values().stream().allMatch(Boolean::booleanValue);
        var status=!proof.get("SUPPORTED_DOMAIN")?UNSUPPORTED:!occurrences.isEmpty()?SUPPORTED_PRESENT:complete?ABSENT_FALSE:UNKNOWN_INCONCLUSIVE;
        proof.forEach((k,v)->{if(!v)reasons.add("negative coverage missing: "+k);});
        var report=JSON.createObjectNode();report.put("schema",!manifest.implementationVersion().equals("1")?"athena-group-identities/2":"athena-group-identities/1");report.set("sourceStateBinding",node(state.binding()));report.set("componentReference",node(component.identity()));
        report.set("sourceGraph",node(graph));report.set("definition",d);report.put("definitionDigest",hash(d));
        var pattern=Map.of("id",text(d,"patternId"),"version",text(d,"patternVersion"),"query",text(d,"query"));report.set("pattern",node(pattern));report.put("patternDigest",SystemStateView.digest(pattern));
        report.set("sourceCoverage",coverage);report.put("sourceCoverageDigest",hash(coverage));report.set("b00Results",results);
        if(!manifest.implementationVersion().equals("1")) {
            contextAssessments.sort((a,b)->{
                int c=ATOM_ORDER.compare(strings(a.get("memberAtomIds")),strings(b.get("memberAtomIds")));
                if(c==0)c=a.get("roleCorrespondence").toString().compareTo(b.get("roleCorrespondence").toString());
                return c!=0?c:a.get("exclusionId").asText().compareTo(b.get("exclusionId").asText());
            });
            report.set("contextAssessments",node(contextAssessments));
        }
        report.set("occurrences",occurrences);report.set("negativeCoverage",node(proof));report.put("assessment",status.name());
        report.set("reasons",node(reasons));report.set("sourceReferences",coverage.get("sourceReferences"));report.set("limitations",d.get("limitations"));
        return report;
    }
    private static void validateExclusions(JsonNode definition) {
        var exclusions=definition.get("occurrenceExclusions");
        if(exclusions==null||!exclusions.isArray())throw new IllegalArgumentException("occurrence exclusions required");
        var ids=new HashSet<String>();
        for(var e:exclusions) {
            fields(e,Set.of("id","patternId","patternVersion","query","anchors","hydrogenRoles","rationale","sourceReferences"));
            if(!ids.add(text(e,"id")))throw new IllegalArgumentException("duplicate exclusion ID");
            for(String key:List.of("patternId","patternVersion","query","rationale"))text(e,key);
            if(!e.path("anchors").isObject()||e.path("anchors").isEmpty())throw new IllegalArgumentException("local anchors required");
            e.get("anchors").fields().forEachRemaining(anchor->{
                if(!definition.get("roles").has(anchor.getKey())||indices(anchor.getValue()).size()!=indices(definition.get("roles").get(anchor.getKey())).size())
                    throw new IllegalArgumentException("exclusion anchor role/arity mismatch");
            });
            if(!e.path("hydrogenRoles").isArray())throw new IllegalArgumentException("exclusion H assertions required");
            var hIndices=new HashSet<Integer>();
            for(var h:e.get("hydrogenRoles")) {
                fields(h,Set.of("queryIndex","count"));
                for(String key:List.of("queryIndex","count"))if(!h.path(key).isIntegralNumber()||!h.path(key).canConvertToInt()||h.path(key).intValue()<0)
                    throw new IllegalArgumentException("invalid exclusion H assertion");
                if(!hIndices.add(h.get("queryIndex").intValue()))throw new IllegalArgumentException("duplicate exclusion H assertion");
            }
            references(e.get("sourceReferences"));
        }
    }
    private static List<JsonNode> embeddings(SubstructureMatcher.Result result)throws Exception {
        var vectors=new TreeMap<List<String>,JsonNode>(ATOM_ORDER);
        for(String message:result.evidence().messages())if(message.startsWith("occurrenceEmbeddings="))
            for(var vector:JSON.readTree(message.substring("occurrenceEmbeddings=".length())).get("compiledQueryIndexToTarget"))
                vectors.put(strings(vector),vector);
        return List.copyOf(vectors.values());
    }
    private static List<String> strings(JsonNode array) {
        var out=new ArrayList<String>();array.forEach(n->out.add(n.asText()));return List.copyOf(out);
    }
    private static JsonNode contextDecision(JsonNode exclusion,List<JsonNode> vectors,Map<String,List<String>> roles,
            Set<String> members,Set<String> ids,Map<String,Integer> hCounts,Map<String,Boolean> proof,JsonNode coverage) {
        var applicable=JSON.createArrayNode();
        boolean satisfied=false,unknown=false;
        for(var vector:vectors) {
            boolean anchored=true;
            var anchors=exclusion.get("anchors").fields();
            while(anchors.hasNext()) {
                var anchor=anchors.next();var targets=new ArrayList<String>();
                for(int index:indices(anchor.getValue()))targets.add(mapped(vector,index,ids));
                anchored&=targets.equals(roles.get(anchor.getKey()));
            }
            if(!anchored)continue;
            applicable.add(vector);
            boolean supported=true,missing=false;
            for(var h:exclusion.get("hydrogenRoles")) {
                String atom=mapped(vector,h.get("queryIndex").intValue(),ids);
                if(!hCounts.containsKey(atom)){missing=true;supported=false;}
                else if(hCounts.get(atom)!=h.get("count").intValue())supported=false;
            }
            satisfied|=supported;unknown|=missing;
        }
        // When H-dependent matching finds nothing, inferred OCL H is not proof of
        // absence. Conservatively require authoritative H for the complete scope.
        boolean scope=proof.get("COMPLETE_GRAPH")&&proof.get("COMPLETE_CHARGE_STATE")
                &&proof.get("AROMATICITY_MODEL")&&proof.get("SUPPORTED_DOMAIN");
        if(!exclusion.get("hydrogenRoles").isEmpty()&&!hCounts.keySet().containsAll(ids))unknown=true;
        String status=satisfied&&scope?"EXCLUDED_BY_CONTEXT":!scope||unknown?"UNKNOWN_CONTEXT":"NOT_EXCLUDED";
        var out=JSON.createObjectNode();out.set("memberAtomIds",node(members));out.set("roleCorrespondence",node(roles));
        out.put("exclusionId",text(exclusion,"id"));out.put("status",status);
        out.set("matchedExclusionCorrespondences",applicable);out.set("sourceStateReferences",coverage.get("sourceReferences"));
        out.set("reasons",node(List.of(switch(status){
            case "EXCLUDED_BY_CONTEXT"->"anchored B00 correspondence and required source-state assertions establish exclusion";
            case "UNKNOWN_CONTEXT"->"complete chemical/search/H scope for exclusion is not established";
            default->"no supported anchored exclusion under complete declared scope";
        })));
        return out;
    }
    private static SubstructureMatcher.Result query(String query,MolecularGraph graph,SubstructureMatcher matcher,JsonNode replay,com.fasterxml.jackson.databind.node.ObjectNode receipts)throws Exception {
        String key=SystemStateView.digest(query);
        var result=replay==null?Objects.requireNonNull(matcher,"B00 matcher required").match(query,graph):JSON.treeToValue(replay.required(key),SubstructureMatcher.Result.class);
        var e=result.evidence();
        if(!e.backend().equals("OPEN_CHEM_LIB")||!e.version().equals(MATCHER)||!e.operation().equals("substructure-match")||!e.graphChanges().isEmpty()
                ||!e.messages().contains("query="+query)||!e.messages().contains("sourceStateSha256="+SystemStateView.digest(graph)))throw new IllegalArgumentException("B00 receipt binding mismatch");
        long alternatives=e.messages().stream().filter(x->x.startsWith("occurrenceEmbeddings=")).count();
        if(alternatives!=result.queryToTargetAtomIds().size())throw new IllegalArgumentException("missing occurrence correspondences");
        receipts.set(key,node(result));return result;
    }
    private static String mapped(JsonNode vector,int index,Set<String> ids) {if(index>=vector.size()||!ids.contains(vector.get(index).asText()))throw new IllegalArgumentException("invalid compiled query role mapping");return vector.get(index).asText();}
    private static List<Integer> indices(JsonNode list) {if(list==null||!list.isArray()||list.isEmpty())throw new IllegalArgumentException("nonempty query-index list");var out=new ArrayList<Integer>();for(var n:list){if(!n.isIntegralNumber()||n.intValue()<0||out.contains(n.intValue()))throw new IllegalArgumentException("invalid query index");out.add(n.intValue());}return List.copyOf(out);}
    private static boolean contains(JsonNode list,String value){for(var n:list)if(n.asText().equals(value))return true;return false;}
    private static JsonNode node(Object value){try{return JSON.readTree(SystemStateView.bytes(value));}catch(Exception e){throw new IllegalArgumentException(e);}}
    private static String hash(JsonNode node){return SystemStateView.digest(node);}
    private static String text(JsonNode n,String key){var v=n.get(key);if(v==null||!v.isTextual()||v.asText().isBlank())throw new IllegalArgumentException("required text: "+key);return v.asText();}
    private static void equal(JsonNode n,String key,String expected){if(!text(n,key).equals(expected))throw new IllegalArgumentException("unexpected "+key);}
    private static void fields(JsonNode n,Set<String> expected){if(n==null||!n.isObject())throw new IllegalArgumentException("object required");var actual=new HashSet<String>();n.fieldNames().forEachRemaining(actual::add);if(!actual.equals(expected))throw new IllegalArgumentException("payload fields mismatch: "+actual);}
    private static void references(JsonNode n){if(n==null||!n.isArray()||n.isEmpty())throw new IllegalArgumentException("attributed sources required");for(var ref:n){fields(ref,Set.of("kind","namespace","id","version"));for(String key:List.of("kind","namespace","id","version"))text(ref,key);try{JSON.treeToValue(ref,ScientificReference.class);}catch(Exception e){throw new IllegalArgumentException("valid source reference required",e);}}}
}
