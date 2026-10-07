package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.system.*;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Optional unique-largest source-component view. Never mutates or chemically normalizes a source. */
final class SourceFragmentParentRules {
    static final String ID="ATHENA.A08.UNIQUE_LARGEST_HEAVY_SOURCE_COMPONENT";
    private SourceFragmentParentRules() { }
    static void validate(RuleManifest m) {
        require(Set.of("athena-rule/2","athena-rule/3").contains(m.schema())&&m.ruleId().equals(ID)&&m.version().equals("1.0.0")&&m.profile().equals("ATHENA_A08_SOURCE_PARENT_V1")&&m.implementationVersion().equals("1")&&m.qualification()==SystemGraphCertificate.Status.NOT_EVALUATED&&m.family()==RuleManifest.Family.MOTIF&&m.requiredCapabilities().isEmpty(),"A08 manifest contract");
        try(var in=SourceFragmentParentRules.class.getResourceAsStream("source-fragment-parent-v1/"+ID+".rule.json")) {
            require(in!=null,"A08 definition missing");var definition=JSON.readTree(in);
            require(JSON.valueToTree(m.parameters()).equals(definition.get("parameters"))&&JSON.valueToTree(m.negativeCoverage()).equals(definition.get("negativeCoverage"))&&JSON.valueToTree(m.scientificSources()).equals(definition.get("scientificSources")),"A08 definition changed");
        }catch(java.io.IOException e){throw new IllegalArgumentException("A08 definition unreadable",e);}
    }
    static ScientificReference method(RuleManifest m,boolean evaluate){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.source-fragment-parent",m.key()+(evaluate?"/evaluate":"/collect"),RuleRegistry.digest(m));}
    static SystemGraphAnalyzer analyzer(RuleManifest m,RuleRequest request,boolean evaluate) {
        validate(m);return new SystemGraphAnalyzer(){
            public ScientificReference method(){return SourceFragmentParentRules.method(m,evaluate);}
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return Set.of("athena:group-source-coverage","athena:rule-measurements","athena:event-source","athena:source-artifact","athena:rule-manifest","athena:rule-policy-context","athena:rule-qualification-receipt","athena:rule-research-eligibility","athena:rule-implementation-qualification","athena:system-binding","athena:system-certificate","athena:rule-request","athena:system-state");}
            public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> supplied,Map<String,String> configuration)throws Exception {
                require(s.binding().equals(request.state())&&m.key().equals(request.manifestKey())&&RuleRegistry.digest(m).equals(request.manifestSha256())&&request.atoms().isEmpty()&&request.first().isEmpty()&&request.second().isEmpty(),"A08 exact whole declared state request required");
                var inputs=index(supplied);var values=new TreeMap<String,String>();String raw=canonical(s.snapshot());values.put("payload",raw);values.put("proposition","UNIQUE_LARGEST_HEAVY_SOURCE_COMPONENT_VIEW");values.put("definitionSha256",RuleRegistry.digest(m));
                var components=s.components().stream().sorted(Comparator.comparing(c->canonical(c.identity()))).toList();var inventory=new ArrayList<Object>();
                for(var c:components)inventory.add(Map.of("component",c.identity(),"graphSha256",hash(c.chemistry()),"sourceAtomCount",c.chemistry().atoms().size()));values.put("sourceComponentInventory",canonical(inventory));
                if(!evaluate)return List.of(finding(s,SUPPORTED_PRESENT,values,"Raw immutable component inventory only; no parent selection or chemistry assertion"));
                var measured=inputs.values().stream().filter(e->e.evidenceType().equals("athena:rule-measurements")).toList();require(measured.size()==1&&measured.getFirst().method().equals(SourceFragmentParentRules.method(m,false))&&read(measured.getFirst()).equals(JSON.readTree(raw)),"A08 source snapshot replay mismatch");
                var at=S1Qualification.current(m,s,request,inputs);if(at.isEmpty())return List.of(finding(s,NOT_EVALUATED,values,"Independent current parent-policy authority absent"));
                if(components.isEmpty()||s.atoms().size()>request.maximumNodes()||components.size()>request.maximumCandidates())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Nonempty complete declared universe within budget required"));
                var union=new HashSet<AtomReference>();var identities=new HashSet<ScientificReference>();var coveragePins=new ArrayList<Object>();var counts=new TreeMap<String,Integer>();
                for(var c:components) {
                    if(!identities.add(c.identity())||c.correspondenceAlternatives().size()!=1)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Unique component identity and correspondence required"));
                    try{c.chemistry().validateTopology(true);}catch(IllegalArgumentException e){return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Nonempty connected complete component graph required"));}
                    var map=c.correspondenceAlternatives().getFirst();if(!map.keySet().equals(c.chemistry().atoms().stream().map(x->x.id()).collect(java.util.stream.Collectors.toSet()))||map.values().stream().anyMatch(a->!union.add(a)))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Lossless disjoint source atom universe required"));
                }
                if(!union.equals(s.atoms().keySet()))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Declared component universe omits or invents source atoms"));
                for(var c:components) {
                    for(var a:c.chemistry().atoms())if(a.element().equals("H")||Element.fromSymbol(a.element())==Element.UNKNOWN||!Set.of("UNSPECIFIED","NONE","PARITY_1","PARITY_2","UNKNOWN").contains(a.stereochemistry()))return List.of(finding(s,UNSUPPORTED,values,"Explicit-H, dummy/query or unrepresented stereo outside concrete heavy-source profile"));
                    for(var b:c.chemistry().bonds())if(!Set.of("UNSPECIFIED","NONE").contains(b.stereochemistry()))return List.of(finding(s,UNSUPPORTED,values,"Bond stereo outside unchanged concrete source representation"));
                    var scope=S1SourceScope.check(s,c,inputs);for(var witness:scope.witnesses())if(!S1Qualification.scope(witness,s,inputs,at.orElseThrow()))return List.of(finding(s,NOT_EVALUATED,values,"Independent original-source scope authority absent"));
                    if(scope.conflicting()||scope.witnesses().isEmpty()||!scope.complete()&&!scope.knownOutside())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Source connection/electronic scope incomplete or conflicting"));
                    if(scope.knownOutside())return List.of(finding(s,UNSUPPORTED,values,"Known original nonordinary connection outside fragment-parent profile"));
                    var coverages=new ArrayList<EvidenceEnvelope>();for(var e:inputs.values())if(e.evidenceType().equals("athena:group-source-coverage")&&read(e).path("componentReference").equals(JSON.valueToTree(c.identity())))coverages.add(e);
                    if(coverages.isEmpty())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Complete original source facts required for every component"));var coverage=read(coverages.getFirst());for(var e:coverages)if(!read(e).equals(coverage))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Conflicting source facts cannot select a parent"));
                    ZincCarbonylRules.verifyCoverage(s,c,coverage);var status=HalogenCarbonylRules.sourceFacts(s,c,coverage,inputs.values());if(status!=SUPPORTED_PRESENT)return List.of(finding(s,status,values,"Original source chemistry unresolved or outside closed-shell profile"));
                    if(!coverage.path("completeGraph").asText().equals("SUPPORTED_PRESENT"))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Complete source graph required for every candidate"));
                    coverages.forEach(e->coveragePins.add(pin(e)));counts.put(canonical(c.identity()),c.chemistry().atoms().size());
                }
                values.put("sourceCoveragePins",canonical(coveragePins));values.put("heavyAtomCounts",canonical(counts));int max=counts.values().stream().max(Integer::compareTo).orElseThrow();var largest=components.stream().filter(c->c.chemistry().atoms().size()==max).toList();values.put("maximalComponents",canonical(largest.stream().map(SystemStateView.Component::identity).toList()));
                if(largest.size()!=1)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Equal maximum counts; no mass, organic, canonical-string or input-order tie-break"));
                var selected=largest.getFirst();values.put("selectedComponent",canonical(selected.identity()));values.put("selectedGraph",canonical(selected.chemistry()));values.put("selectedGraphSha256",hash(selected.chemistry()));
                values.put("atomLineage",canonical(selected.correspondenceAlternatives().getFirst()));values.put("bondLineage",canonical(selected.chemistry().bonds().stream().map(b->Map.of("sourceBondId",b.id(),"derivedBondId",b.id(),"unchangedBond",b)).toList()));
                values.put("retainedOtherComponents",canonical(components.stream().filter(c->!c.identity().equals(selected.identity())).map(c->Map.of("component",c.identity(),"graphSha256",hash(c.chemistry()))).toList()));
                return List.of(finding(s,SUPPORTED_PRESENT,values,"Optional source-component parent view only; all original components retained and no chemical edits"));
            }
            private Finding finding(SystemStateView s,EvidenceInterpretation.Status status,Map<String,String> values,String reason){return new Finding(evaluate?"evaluate":"collect",List.of(s.subject()),status,values,List.of(reason),m.limitations());}
        };
    }
}
