package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.system.*;
import totah.lab.gaia.structure.*;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Direct attributed S1 assignment in existing EvidenceInterpretation findings. */
final class S1NitrogenRules {
    static final String IMPLEMENTATION="athena.i03-n-sp3-s1";
    private S1NitrogenRules() { }
    static void validate(RuleManifest m) {
        require(Set.of("athena-rule/2","athena-rule/3").contains(m.schema())&&m.ruleId().equals("ATHENA.I03.N_SP3.SATURATED_CARBON_ATTACHMENTS")&&m.version().equals("1.0.0")&&m.profile().equals("ATHENA_I03_N_SP3_S1_V1")&&m.implementationVersion().equals("1")&&m.family()==RuleManifest.Family.VALIDATOR&&m.requiredCapabilities().isEmpty()&&m.qualification()==SystemGraphCertificate.Status.NOT_EVALUATED,"S1 manifest contract");
        try(var in=S1NitrogenRules.class.getResourceAsStream("i03-n-sp3-s1-v1/"+m.ruleId()+".rule.json")) {
            require(in!=null,"S1 definition missing");var p=JSON.readTree(in);
            require(JSON.valueToTree(m.parameters()).equals(p.get("parameters"))&&JSON.valueToTree(m.negativeCoverage()).equals(p.get("negativeCoverage"))&&JSON.valueToTree(m.scientificSources()).equals(p.get("scientificSources")),"S1 scientific definition changed");
        }catch(java.io.IOException e){throw new IllegalArgumentException("S1 definition unreadable",e);}
    }
    static ScientificReference method(RuleManifest m){return new ScientificReference(ScientificReference.Kind.METHOD,IMPLEMENTATION,m.key()+"/evaluate",RuleRegistry.digest(m));}
    static SystemGraphAnalyzer analyzer(RuleManifest m,RuleRequest request) {
        validate(m);return new SystemGraphAnalyzer(){
            public ScientificReference method(){return S1NitrogenRules.method(m);}
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return Set.of("athena:event-source","athena:group-source-coverage","athena:group-identities","athena:rule-manifest","athena:rule-request","athena:rule-policy-context","athena:rule-qualification-receipt","athena:rule-research-eligibility","athena:rule-implementation-qualification","athena:system-binding","athena:system-certificate","athena:system-state","athena:source-artifact");}
            public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> inputs,Map<String,String> config)throws Exception {
                require(request.state().equals(s.binding())&&request.manifestKey().equals(m.key())&&request.manifestSha256().equals(RuleRegistry.digest(m))&&!request.atoms().isEmpty()&&request.first().isEmpty()&&request.second().isEmpty(),"S1 request binding/selection");
                require(new HashSet<>(request.atoms()).size()==request.atoms().size(),"duplicate S1 selected atom");
                var artifacts=index(inputs);var time=S1Qualification.current(m,s,request,artifacts);
                var protocol=artifacts.values().stream().filter(e->e.payloadSha256().equals(m.scientificSources().getFirst().sha256())).toList();
                require(!protocol.isEmpty(),"exact approved S1 protocol artifact required");
                var chemistry=new HbondCandidateSources(s,m,request,inputs);var descriptors=new ArrayList<Object>();
                var sources=HbondCandidateRules.parameter(m,"sources");sources.fieldNames().forEachRemaining(id->descriptors.add(Map.of("id",id,"role",id.contains(".DONOR.")?"donor":"acceptor")));
                var result=new ArrayList<Finding>();int count=0;
                for(var atom:request.atoms().stream().sorted(Comparator.comparing(EventPayload::canonical)).toList()) {
                    require(s.atoms().containsKey(atom),"selected S1 atom absent");var reasons=new TreeSet<String>();var decision=S1NitrogenPredicate.Decision.UNKNOWN;boolean scopeQualified=false;
                    var components=s.components().stream().filter(c->c.correspondenceAlternatives().stream().anyMatch(x->x.containsValue(atom))).toList();
                    if(count++>=request.maximumCandidates())reasons.add("selected atom not evaluated: candidate enumeration budget; no complete negative");
                    else if(components.size()!=1||components.getFirst().correspondenceAlternatives().size()!=1)reasons.add("unique source component/correspondence unresolved");
                    else {
                        var c=components.getFirst();var map=c.correspondenceAlternatives().getFirst();var graph=c.chemistry();
                        if(map.size()!=graph.atoms().size()||new HashSet<>(map.values()).size()!=map.size()||graph.atoms().size()>request.maximumNodes())reasons.add("source mapping/topology or node coverage incomplete");
                        else {
                            var id=map.entrySet().stream().filter(e->e.getValue().equals(atom)).findFirst().orElseThrow().getKey();
                            var coverage=new ArrayList<JsonNode>();
                            for(var e:artifacts.values())if(e.evidenceType().equals("athena:group-source-coverage")) {
                                var n=read(e);fields(n,"schema","stateBinding","componentReference","completeGraph","sourceReferences","atomState","limitations");
                                require(n.path("sourceReferences").isArray()&&!n.path("sourceReferences").isEmpty()&&n.path("atomState").isObject(),"S1 source provenance/state missing");
                                for(var source:n.path("sourceReferences"))reference(source);
                                for(var facts:n.path("atomState"))fields(facts,"chargeStatus","formalCharge","hydrogenMode","explicitHydrogenAtomIds","implicitHydrogenCount","aromaticityStatus","aromaticityModel","evidenceReferences");
                                require(n.path("schema").asText().equals("athena-group-source-coverage/1")&&n.path("stateBinding").equals(JSON.valueToTree(s.binding())),"S1 source coverage schema/state mismatch");
                                if(n.path("componentReference").equals(JSON.valueToTree(c.identity()))&&!coverage.contains(n))coverage.add(n);
                            }
                            var scope=S1SourceScope.check(s,c,artifacts);reasons.addAll(scope.reasons());
                            scopeQualified=time.isPresent()&&!scope.witnesses().isEmpty();
                            if(scopeQualified)for(var e:scope.witnesses())scopeQualified &=S1Qualification.scope(e,s,artifacts,time.orElseThrow());
                            boolean stateAgrees=topology(s,c);
                            if(scope.knownOutside()&&stateAgrees){decision=S1NitrogenPredicate.Decision.UNSUPPORTED;reasons.add("explicit source nonordinary connection excludes S1; no bond conversion or valence inference");}
                            else if(coverage.size()!=1||scope.conflicting()||!stateAgrees)reasons.add("source coverage missing/conflicting or source/system chemistry mismatch");
                            else {
                                var cov=coverage.getFirst();boolean rolesAgree=true;
                                for(var it=sources.fieldNames();it.hasNext();) {var report=chemistry.report(c,it.next());if(report!=null&&!report.payload().path("sourceCoverage").equals(cov))rolesAgree=false;}
                                if(!rolesAgree)reasons.add("role/source coverage conflict");
                                else {
                                    boolean admitted=chemistry.anchors(c,JSON.valueToTree(descriptors)).stream().anyMatch(a->a.atom().equals(atom)&&a.report().complete());
                                    var raw=S1NitrogenPredicate.evaluate(graph,id,cov,admitted,scope.complete());decision=raw.decision();reasons.addAll(raw.reasons());

                                }
                            }
                        }
                    }
                    var status=NOT_EVALUATED;
                    if(time.isPresent()&&scopeQualified)status=switch(decision){case SP3->SUPPORTED_PRESENT;case NOT_SP3->ABSENT_FALSE;case UNSUPPORTED->UNSUPPORTED;case UNKNOWN->UNKNOWN_INCONCLUSIVE;};
                    else reasons.add("S1 invocation or independent source-scope authority unqualified; raw model label is not scientific eligibility");
                    var values=Map.of("proposition","SOURCE_ATOM_HYBRIDIZATION","stateBinding",canonical(s.binding()),"atom",canonical(atom),"hybridization",decision.name(),"assignmentProtocol",canonical(pin(protocol.getFirst())));
                    result.add(new Finding("assignment-"+hash(atom),List.of(new EvidenceSubject(s.identity(),"atom",atom.toString(),List.of())),status,values,List.copyOf(reasons),m.limitations()));
                }
                return List.copyOf(result);
            }
        };
    }
    private static boolean topology(SystemStateView s,SystemStateView.Component c) {
        var map=c.correspondenceAlternatives().getFirst();var expected=new HashSet<Bond>();
        for(var b:c.chemistry().bonds())expected.add(new Bond(map.get(b.firstAtomId()),map.get(b.secondAtomId()),totah.lab.gaia.chemistry.BondOrder.valueOf(b.order().name())));
        var actual=new HashSet<Bond>();s.graph().structure().bonds().stream().filter(b->map.containsValue(b.atom1())||map.containsValue(b.atom2())).forEach(actual::add);
        if(!expected.equals(actual)||s.graph().structure().getConnectivityMetadata().provenance()!=ConnectivityProvenance.EXPLICIT)return false;
        for(var a:c.chemistry().atoms()) {var ref=map.get(a.id());var atom=s.atoms().get(ref);if(atom==null||atom.getElement()==null||!atom.getElement().name().equalsIgnoreCase(a.element())||!s.charges().charges().containsKey(ref)||s.charges().charge(ref)!=a.formalCharge())return false;}
        return true;
    }
}
