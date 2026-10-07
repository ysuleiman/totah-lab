package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.system.*;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Source-declared monatomic Zn2+/carbonyl proximity; never a coordination assignment. */
final class ZincCarbonylRules {
    static final String ID="ATHENA.I14.ZN2_CARBONYL_PROXIMITY";
    private static final String ACCEPTOR="ATHENA.PERCEPTION.ACCEPTOR.CARBONYL_O";
    private ZincCarbonylRules() { }
    static void validate(RuleManifest m) {
        require(Set.of("athena-rule/2","athena-rule/3").contains(m.schema())&&m.ruleId().equals(ID)&&m.version().equals("1.0.0")&&m.profile().equals("ATHENA_I14_ZN2_CARBONYL_V1")&&m.implementationVersion().equals("1")&&m.qualification()==SystemGraphCertificate.Status.NOT_EVALUATED&&m.family()==RuleManifest.Family.INTERACTION&&m.requiredCapabilities().isEmpty(),"I14 manifest contract");
        try(var in=ZincCarbonylRules.class.getResourceAsStream("zinc-carbonyl-v1/"+ID+".rule.json")) {
            require(in!=null,"I14 definition missing");var definition=JSON.readTree(in);
            require(JSON.valueToTree(m.parameters()).equals(definition.get("parameters"))&&JSON.valueToTree(m.negativeCoverage()).equals(definition.get("negativeCoverage"))&&JSON.valueToTree(m.scientificSources()).equals(definition.get("scientificSources")),"I14 definition changed");
        }catch(java.io.IOException e){throw new IllegalArgumentException("I14 definition unreadable",e);}
    }
    static ScientificReference method(RuleManifest m,boolean evaluate){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.zinc-carbonyl",m.key()+(evaluate?"/evaluate":"/collect"),RuleRegistry.digest(m));}
    static SystemGraphAnalyzer analyzer(RuleManifest m,RuleRequest request,boolean evaluate) {
        validate(m);return new SystemGraphAnalyzer(){
            public ScientificReference method(){return ZincCarbonylRules.method(m,evaluate);}
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return Set.of("athena:group-identities","athena:group-source-coverage","athena:continuous-geometry-plan","athena:rule-measurements","athena:event-source","athena:source-artifact","athena:rule-manifest","athena:rule-policy-context","athena:rule-qualification-receipt","athena:rule-research-eligibility","athena:rule-implementation-qualification","athena:system-binding","athena:system-certificate","athena:rule-request","athena:system-state");}
            public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> supplied,Map<String,String> configuration)throws Exception {
                var t=request.atoms();require(s.binding().equals(request.state())&&m.key().equals(request.manifestKey())&&RuleRegistry.digest(m).equals(request.manifestSha256())&&t.size()==3&&new HashSet<>(t).size()==3&&request.first().isEmpty()&&request.second().isEmpty(),"I14 exact ordered tuple/state required");
                var inputs=index(supplied);var sources=new HbondCandidateSources(s,m,request,List.copyOf(inputs.values()));
                var plans=inputs.values().stream().filter(e->e.evidenceType().equals("athena:continuous-geometry-plan")).toList();require(plans.size()==1,"one selected I14 plan required");var plan=read(plans.getFirst());var ops=plan.path("operations");
                require(plan.path("groups").isArray()&&plan.path("groups").isEmpty()&&plan.path("radiusAssignmentReference").isNull()&&ops.size()==1&&ops.get(0).path("kind").asText().equals("DISTANCE")&&ops.get(0).path("atoms").equals(JSON.valueToTree(t.subList(0,2))),"I14 exact selected distance plan");
                var geometry=RuleRegistry.decode(SystemStateView.bytes(HbondCandidateRules.parameter(m,"geometry")));
                var gr=new RuleRequest(s.binding(),geometry.key(),RuleRegistry.digest(geometry),t.subList(0,2).stream().sorted().toList(),List.of(),List.of(),request.radiusAngstrom(),0,request.maximumNodes(),request.maximumCandidates());
                var raw=RuleAnalyzers.collector(geometry,gr).analyze(s,plans,Map.of()).getFirst().measurements().get("payload");
                var values=new TreeMap<String,String>();values.put("payload",raw);values.put("proposition","SELECTED_ZN2_CARBONYL_PROLIF_WINDOW_PROXIMITY");values.put("sourcePins",canonical(sources.pins()));values.put("tuple",canonical(t));values.put("definitionSha256",RuleRegistry.digest(m));
                if(!evaluate)return List.of(finding(s,SUPPORTED_PRESENT,values,"Raw selected distance; source chemistry and scientific eligibility not evaluated"));
                var measured=inputs.values().stream().filter(e->e.evidenceType().equals("athena:rule-measurements")).toList();require(measured.size()==1&&measured.getFirst().method().equals(ZincCarbonylRules.method(m,false))&&read(measured.getFirst()).equals(JSON.readTree(raw)),"I14 measurement replay mismatch");
                var at=S1Qualification.current(m,s,request,inputs);if(at.isEmpty())return List.of(finding(s,NOT_EVALUATED,values,"Independent current I14 qualification absent"));
                var metal=HalogenCarbonylRules.component(s,t.get(0),t.get(0));var partner=HalogenCarbonylRules.component(s,t.get(1),t.get(2));
                if(metal==null||partner==null)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Incomplete or ambiguous source correspondence"));
                if(metal.identity().equals(partner.identity()))return List.of(finding(s,UNSUPPORTED,values,"Same-component pair outside this unconnected profile"));
                for(var c:List.of(metal,partner)) {
                    var scope=S1SourceScope.check(s,c,inputs);
                    for(var witness:scope.witnesses())if(!S1Qualification.scope(witness,s,inputs,at.orElseThrow()))return List.of(finding(s,NOT_EVALUATED,values,"Independent exact source-scope authority absent"));
                    if(scope.conflicting()||scope.witnesses().isEmpty()||!scope.complete()&&!scope.knownOutside())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Source scope missing, conflicting or unresolved"));
                    if(scope.knownOutside())return List.of(finding(s,UNSUPPORTED,values,"Known original nonordinary connection outside unconnected pair profile"));
                }
                var coverages=new ArrayList<JsonNode>();
                for(var e:inputs.values())if(e.evidenceType().equals("athena:group-source-coverage")){var c=read(e);if(c.path("componentReference").equals(JSON.valueToTree(metal.identity())))coverages.add(c);}
                if(coverages.isEmpty()||coverages.stream().distinct().count()!=1)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Unique metal source coverage required"));
                var coverage=coverages.getFirst();verifyCoverage(s,metal,coverage);
                var report=sources.report(partner,ACCEPTOR);if(report==null)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Carbonyl source role evidence absent"));
                for(var entry:List.of(Map.entry(metal,coverage),Map.entry(partner,report.payload().path("sourceCoverage")))) {
                    var status=HalogenCarbonylRules.sourceFacts(s,entry.getKey(),entry.getValue(),inputs.values());if(status!=SUPPORTED_PRESENT)return List.of(finding(s,status,values,"Source facts incomplete, conflicting or outside closed-shell domain"));
                }
                var z=metal.chemistry().atom(HalogenCarbonylRules.sourceId(metal,t.get(0))).orElseThrow();var h=coverage.path("atomState").path(z.id());
                var o=partner.chemistry().atom(HalogenCarbonylRules.sourceId(partner,t.get(1))).orElseThrow();var carbon=partner.chemistry().atom(HalogenCarbonylRules.sourceId(partner,t.get(2))).orElseThrow();
                if(!z.element().equals("Zn")||z.formalCharge()!=2||z.aromatic()||metal.chemistry().atoms().size()!=1||!metal.chemistry().bonds().isEmpty()||h.path("implicitHydrogenCount").asInt()!=0||!h.path("explicitHydrogenAtomIds").isEmpty()||z.explicitHydrogens()!=0||!o.element().equals("O")||o.formalCharge()!=0||!carbon.element().equals("C")||carbon.formalCharge()!=0)return List.of(finding(s,UNSUPPORTED,values,"Outside monatomic source Zn2+ / neutral carbonyl domain"));
                if(!coverage.path("completeGraph").asText().equals("SUPPORTED_PRESENT")||!report.complete())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Applicable graph/role coverage incomplete"));
                if(!HalogenCarbonylRules.roles(report,List.of("carbonylOxygen","carbonylCarbon"),t.subList(1,3)))return List.of(finding(s,UNSUPPORTED,values,"Selected partner is not the exact carbonyl source occurrence"));
                Double distance=HalogenCarbonylRules.value(JSON.readTree(raw).path("operations"),0,"distanceAngstrom");
                if(distance==null||distance<=0)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Finite nonzero qualified distance required"));
                values.put("distanceAngstrom",distance.toString());values.put("sourceMetalState","MONATOMIC_ZN_FORMAL_CHARGE_PLUS_2");values.put("coordinationAssignment","NOT_EVALUATED");
                return List.of(finding(s,predicate(distance)?SUPPORTED_PRESENT:ABSENT_FALSE,values,"Exact selected source-specific proximity only; no coordination or whole-system absence"));
            }
            private Finding finding(SystemStateView s,EvidenceInterpretation.Status status,Map<String,String> values,String reason){return new Finding(evaluate?"evaluate":"collect",List.of(s.subject()),status,values,List.of(reason),m.limitations());}
        };
    }
    static boolean predicate(double distance){return Double.isFinite(distance)&&distance>0&&distance<=2.8;}
    private static void verifyCoverage(SystemStateView s,SystemStateView.Component component,JsonNode n)throws Exception {
        fields(n,"schema","stateBinding","componentReference","completeGraph","atomState","sourceReferences","limitations");
        require(text(n,"schema").equals("athena-group-source-coverage/1")&&binding(n.get("stateBinding")).equals(s.binding()),"Metal source coverage binding");
        require(n.path("atomState").isObject(),"Metal atom source facts required");
        for(var ref:array(n,"sourceReferences"))reference(ref);
        var ids=new HashSet<String>();component.chemistry().atoms().forEach(a->ids.add(a.id()));n.path("atomState").fieldNames().forEachRemaining(id->require(ids.contains(id),"Unknown metal coverage atom"));
        for(var fact:n.path("atomState")) {
            fields(fact,"chargeStatus","formalCharge","hydrogenMode","explicitHydrogenAtomIds","implicitHydrogenCount","aromaticityStatus","aromaticityModel","evidenceReferences");
            for(var ref:array(fact,"evidenceReferences"))reference(ref);
        }
    }
}
