package totah.lab.athena.system.rules;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import totah.lab.athena.system.*;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.structure.*;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Source graph assertions only. No geometry or sulfur chemistry is inferred. */
final class SourceSulfurConnectivityRules {
    private SourceSulfurConnectivityRules() { }
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final List<String> NEGATIVE = List.of("COMPLETE_APPLICABLE_COMPONENT_GRAPH",
            "COMPLETE_UNAMBIGUOUS_MAPPING", "VALID_TOPOLOGY_STATE_BINDING", "NO_CONFLICTING_ASSERTION", "WITHIN_SCOPE_BUDGET");

    static void validate(RuleManifest m) {
        if (!m.schema().equals("athena-rule/2") || !m.ruleId().equals("ATHENA.SULF.SS_CONNECTIVITY")
                || !m.implementationVersion().equals("1") || !m.profile().equals("ATHENA_SOURCE_SS_CONNECTIVITY_V1")
                || m.family()!=RuleManifest.Family.MOTIF || !m.requiredCapabilities().isEmpty()
                || !m.parameters().keySet().equals(Set.of("definitionReference"))
                || !m.negativeCoverage().requirements().equals(NEGATIVE)
                || !m.negativeCoverage().scope().equals("EXPLICIT_SOURCE_SULFUR_PAIR"))
            throw new IllegalArgumentException("source S-S connectivity manifest contract");
        try {
            var ref=JSON.readValue(m.parameters().get("definitionReference").value(),ScientificReference.class);
            ref.require(ScientificReference.Kind.SOURCE);
            if(!ref.version().matches("[0-9a-f]{64}"))throw new IllegalArgumentException("definition digest required");
        } catch(Exception e) { throw new IllegalArgumentException("invalid source definition pin",e); }
    }

    static SystemGraphAnalyzer analyzer(RuleManifest m,RuleRequest r) {
        validate(m);
        return new SystemGraphAnalyzer() {
            public ScientificReference method(){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.ss-connectivity",m.key()+"/evaluate",RuleRegistry.digest(m));}
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return Set.of("athena:group-source-coverage","athena:system-state");}
            public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> inputs,Map<String,String> configuration)throws Exception {
                if(!s.binding().equals(r.state())||!m.key().equals(r.manifestKey())||!RuleRegistry.digest(m).equals(r.manifestSha256())
                        ||r.atoms().size()!=2||r.atoms().get(0).equals(r.atoms().get(1))||!r.first().isEmpty()||!r.second().isEmpty())
                    throw new IllegalArgumentException("two distinct state-bound sulfur subjects required");
                var pair=new TreeSet<>(r.atoms());
                for(var a:pair)if(!s.atoms().containsKey(a)||s.atoms().get(a).getElement()!=Element.S)
                    throw new IllegalArgumentException("missing/non-sulfur subject");
                var coverage=new TreeMap<String,List<JsonNode>>();var pins=new TreeMap<String,Object>();
                var identities=new HashSet<ScientificReference>();
                for(var component:s.components())if(!identities.add(component.identity()))throw new IllegalArgumentException("duplicate component identity");
                for(var e:inputs)if(e.evidenceType().equals("athena:group-source-coverage")) {
                    byte[] bytes=e.readPayload();
                    if(!EvidenceExchange.sha256(bytes).equals(e.payloadSha256()))throw new IllegalArgumentException("coverage hash mismatch");
                    var n=JSON.readTree(bytes);
                    fields(n,Set.of("schema","stateBinding","componentReference","completeGraph","atomState","sourceReferences","limitations"));
                    if(!n.path("schema").asText().equals("athena-group-source-coverage/1")||!n.get("stateBinding").equals(node(s.binding())))
                        throw new IllegalArgumentException("coverage schema/state mismatch");
                    var component=s.components().stream().filter(c->node(c.identity()).equals(n.get("componentReference"))).findFirst()
                            .orElseThrow(()->new IllegalArgumentException("coverage component mismatch"));
                    EvidenceInterpretation.Status.valueOf(n.path("completeGraph").asText());
                    references(n.get("sourceReferences"));
                    if(!n.get("atomState").isObject()||!n.get("limitations").isArray())throw new IllegalArgumentException("coverage containers required");
                    n.get("limitations").forEach(v->{if(!v.isTextual())throw new IllegalArgumentException("text limitation required");});
                    var graphIds=new HashSet<String>();component.chemistry().atoms().forEach(a->graphIds.add(a.id()));
                    n.get("atomState").fieldNames().forEachRemaining(id->{if(!graphIds.contains(id))throw new IllegalArgumentException("unknown coverage atom");});
                    for (var annotation:n.get("atomState")) {
                        fields(annotation,Set.of("chargeStatus","formalCharge","hydrogenMode","explicitHydrogenAtomIds","implicitHydrogenCount","aromaticityStatus","aromaticityModel","evidenceReferences"));
                        references(annotation.get("evidenceReferences"));
                        EvidenceInterpretation.Status.valueOf(annotation.path("chargeStatus").asText());
                        EvidenceInterpretation.Status.valueOf(annotation.path("aromaticityStatus").asText());
                        if(!Set.of("UNKNOWN","EXPLICIT_GRAPH","AUTHORITATIVE_IMPLICIT").contains(annotation.path("hydrogenMode").asText())
                                ||!annotation.get("explicitHydrogenAtomIds").isArray()||!annotation.get("aromaticityModel").isTextual())
                            throw new IllegalArgumentException("malformed atom-state annotation");
                        var hydrogenIds=new HashSet<String>();
                        for(var id:annotation.get("explicitHydrogenAtomIds"))if(!id.isTextual()||!hydrogenIds.add(id.asText())||!graphIds.contains(id.asText()))
                            throw new IllegalArgumentException("invalid hydrogen identity annotation");
                        if(annotation.path("chargeStatus").asText().equals(SUPPORTED_PRESENT.name())&&!annotation.get("formalCharge").isIntegralNumber())
                            throw new IllegalArgumentException("integer source charge required");
                        if(!annotation.path("hydrogenMode").asText().equals("UNKNOWN")&&(!annotation.get("implicitHydrogenCount").isIntegralNumber()||annotation.get("implicitHydrogenCount").asInt()<0))
                            throw new IllegalArgumentException("nonnegative source H annotation required");
                    }
                    // H/charge/aromaticity annotations do not define this topology-only proposition.
                    String key=text(e.reference());var pin=Map.of("reference",e.reference(),"payloadSha256",e.payloadSha256(),"payload",n,"method",e.method(),"provenance",e.provenance());
                    var old=pins.putIfAbsent(key,pin);
                    if(old!=null&&!old.equals(pin))throw new IllegalArgumentException("conflicting payload for same evidence identity");
                    if(old==null)coverage.computeIfAbsent(text(component.identity()),ignored->new ArrayList<>()).add(n);
                }
                boolean budget=r.maximumNodes()>=2&&r.maximumCandidates()>=1;
                var assertions=new TreeMap<String,Map<String,Object>>();var reasons=new TreeSet<String>();
                boolean positive=false,completeNoEdge=false,mappingComplete=true;
                var provenance=s.graph().structure().getConnectivityMetadata().provenance();
                for(var b:s.graph().structure().bonds())if(Set.of(b.atom1(),b.atom2()).equals(pair)) {
                    boolean authoritative=provenance==ConnectivityProvenance.EXPLICIT||provenance==ConnectivityProvenance.PARTIAL;
                    assertions.put("structure/"+text(b),Map.of("kind","STRUCTURE_LISTED_EDGE","bond",b,"authoritative",authoritative,
                            "connectivity",s.graph().structure().getConnectivityMetadata(),"sourceReferences",s.sources()));
                    positive |= authoritative&&budget;
                }
                for(var component:s.components().stream().sorted(Comparator.comparing(c->text(c.identity()))).toList()) {
                    var graph=component.chemistry();graph.validateTopology(false);
                    var ids=new TreeSet<String>();graph.atoms().forEach(a->ids.add(a.id()));
                    if(component.correspondenceAlternatives().size()!=1) {mappingComplete=false;reasons.add("ambiguous/incomplete component mapping");continue;}
                    var map=component.correspondenceAlternatives().getFirst();
                    if(!ids.containsAll(map.keySet())||new HashSet<>(map.values()).size()!=map.size())throw new IllegalArgumentException("invalid correspondence keys/duplicate targets");
                    for(var entry:map.entrySet()) {
                        var target=s.atoms().get(entry.getValue());
                        if(target==null||target.getElement()==null||!target.getElement().name().equalsIgnoreCase(graph.atom(entry.getKey()).orElseThrow().element()))
                            throw new IllegalArgumentException("invalid correspondence target/element");
                    }
                    boolean completeMap=map.keySet().equals(ids);mappingComplete &= completeMap;
                    if(!map.values().containsAll(pair))continue;
                    boolean bounded=budget&&graph.atoms().size()<=r.maximumNodes();
                    var selectedIds=new TreeSet<String>();map.forEach((id,ref)->{if(pair.contains(ref))selectedIds.add(id);});
                    var bonds=graph.bonds().stream().filter(b->Set.of(b.firstAtomId(),b.secondAtomId()).equals(selectedIds)).toList();
                    var proofs=coverage.getOrDefault(text(component.identity()),List.of());
                    boolean exhaustive=!proofs.isEmpty()&&proofs.stream().allMatch(n->n.path("completeGraph").asText().equals(SUPPORTED_PRESENT.name()));
                    var assertion=new TreeMap<String,Object>();assertion.put("kind","COMPONENT_GRAPH");assertion.put("component",component.identity());
                    assertion.put("chemicalGraphSha256",EvidenceExchange.sha256(SystemStateView.bytes(graph)));assertion.put("selectedSourceAtomIds",selectedIds);
                    assertion.put("listedBonds",bonds);assertion.put("mapping",map);assertion.put("completeMapping",completeMap);
                    assertion.put("completeGraphCoverage",exhaustive);assertion.put("withinBudget",bounded);assertion.put("sourceReferences",s.sources());
                    assertions.put("component/"+text(component.identity()),assertion);
                    positive |= !bonds.isEmpty()&&bounded;
                    completeNoEdge |= bonds.isEmpty()&&exhaustive&&completeMap&&bounded;
                    if(!bounded)reasons.add("component scope exceeds node budget");
                }
                // Any listed assertion blocks a resolved absence, including unqualified/inferred edges.
                boolean listed=assertions.values().stream().anyMatch(a->a.get("kind").equals("STRUCTURE_LISTED_EDGE")
                        ||a.get("listedBonds") instanceof List<?> bonds&&!bonds.isEmpty());
                boolean conflict=completeNoEdge&&listed;
                boolean negative=completeNoEdge&&mappingComplete&&!listed&&budget;
                var assessment=conflict?UNKNOWN_INCONCLUSIVE:positive?SUPPORTED_PRESENT:negative?ABSENT_FALSE:UNKNOWN_INCONCLUSIVE;
                if(conflict)reasons.add("listed edge and complete no-edge assertions coexist; no resolved absence");
                if(assessment==UNKNOWN_INCONCLUSIVE&&!conflict)reasons.add("no sufficient applicable complete source connectivity proof");
                var values=new TreeMap<String,String>();values.put("assessment",assessment.name());values.put("positiveConnectivityEvidence",Boolean.toString(positive));
                values.put("completeNegativeCoverage",Boolean.toString(negative));values.put("conflictingAssertions",Boolean.toString(conflict));
                values.put("sourceAssertions",text(assertions.values()));values.put("coverageSources",text(pins.values()));
                values.put("stateBinding",text(s.binding()));values.put("subjects",text(pair));values.put("definitionReference",m.parameters().get("definitionReference").value());
                var proof=new TreeMap<String,Boolean>();proof.put(NEGATIVE.get(0),completeNoEdge);proof.put(NEGATIVE.get(1),mappingComplete);
                proof.put(NEGATIVE.get(2),true);proof.put(NEGATIVE.get(3),!listed);proof.put(NEGATIVE.get(4),budget);values.put("negativeCoverage",text(proof));
                if(reasons.isEmpty())reasons.add(positive?"authoritative source lists selected covalent edge":"verified complete applicable source graph lists no selected edge");
                var members=new ArrayList<EvidenceSubject>();members.add(s.subject());
                pair.forEach(a->members.add(new EvidenceSubject(s.identity(),"atom",a.toString(),List.of())));
                var subject=new EvidenceSubject(s.identity(),"collection","explicit-source-ss-pair",members);var results=new ArrayList<Finding>();
                if(conflict&&positive)results.add(new Finding("listed-positive",List.of(subject),status(m,SUPPORTED_PRESENT),
                        Map.of("assessment",SUPPORTED_PRESENT.name(),"sourceAssertions",values.get("sourceAssertions"),"stateBinding",values.get("stateBinding")),
                        List.of("listed positive retained independently of conflicting absence assertion"),m.limitations()));
                results.add(new Finding("source-connectivity",List.of(subject),status(m,assessment),values,List.copyOf(reasons),m.limitations()));
                return List.copyOf(results);
            }
        };
    }
    private static EvidenceInterpretation.Status status(RuleManifest m,EvidenceInterpretation.Status value){return m.retired()||m.qualification()!=SystemGraphCertificate.Status.QUALIFIED?NOT_EVALUATED:value;}
    private static void fields(JsonNode n,Set<String> expected){if(n==null||!n.isObject())throw new IllegalArgumentException("coverage object required");var actual=new HashSet<String>();n.fieldNames().forEachRemaining(actual::add);if(!actual.equals(expected))throw new IllegalArgumentException("coverage fields");}
    private static void references(JsonNode n)throws Exception {if(n==null||!n.isArray()||n.isEmpty())throw new IllegalArgumentException("coverage source references required");for(var v:n)JSON.treeToValue(v,ScientificReference.class);}
    private static JsonNode node(Object value){return JSON.valueToTree(value);}
    private static String text(Object value){return new String(SystemStateView.bytes(value),java.nio.charset.StandardCharsets.UTF_8);}
}
