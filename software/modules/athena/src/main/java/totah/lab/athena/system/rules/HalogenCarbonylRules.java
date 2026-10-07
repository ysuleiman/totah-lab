package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.system.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Selected I10 tuple; existing source roles, component scope, geometry and current gate. */
final class HalogenCarbonylRules {
    static final String ID="ATHENA.I10.CARBON_BOUND_HALOGEN_CARBONYL_DIRECTIONAL_CANDIDATE";
    private static final String DONOR="ATHENA.GROUP.HALOGENATED", ACCEPTOR="ATHENA.PERCEPTION.ACCEPTOR.CARBONYL_O";
    private HalogenCarbonylRules() { }
    static void validate(RuleManifest m) {
        require(Set.of("athena-rule/2","athena-rule/3").contains(m.schema())&&m.ruleId().equals(ID)&&m.version().equals("1.0.0")&&m.profile().equals("ATHENA_I10_HALOGEN_CARBONYL_V1")&&m.implementationVersion().equals("1")&&m.qualification()==SystemGraphCertificate.Status.NOT_EVALUATED&&m.family()==RuleManifest.Family.INTERACTION&&m.requiredCapabilities().isEmpty(),"I10 manifest contract");
        try(var in=HalogenCarbonylRules.class.getResourceAsStream("halogen-carbonyl-v1/"+ID+".rule.json")) {
            require(in!=null,"I10 definition missing");var definition=JSON.readTree(in);
            require(JSON.valueToTree(m.parameters()).equals(definition.get("parameters"))&&JSON.valueToTree(m.negativeCoverage()).equals(definition.get("negativeCoverage"))&&JSON.valueToTree(m.scientificSources()).equals(definition.get("scientificSources")),"I10 definition changed");
        }catch(java.io.IOException e){throw new IllegalArgumentException("I10 definition unreadable",e);}
    }
    static ScientificReference method(RuleManifest m,boolean evaluate){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.halogen-carbonyl",m.key()+(evaluate?"/evaluate":"/collect"),RuleRegistry.digest(m));}
    static SystemGraphAnalyzer analyzer(RuleManifest m,RuleRequest request,boolean evaluate) {
        validate(m);return new SystemGraphAnalyzer(){
            public ScientificReference method(){return HalogenCarbonylRules.method(m,evaluate);}
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return Set.of("athena:group-identities","athena:group-source-coverage","athena:continuous-geometry-plan","athena:rule-measurements","athena:event-source","athena:source-artifact","athena:rule-manifest","athena:rule-policy-context","athena:rule-qualification-receipt","athena:rule-research-eligibility","athena:rule-implementation-qualification","athena:system-binding","athena:system-certificate","athena:rule-request","athena:system-state");}
            public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> supplied,Map<String,String> configuration)throws Exception {
                var tuple=request.atoms();require(s.binding().equals(request.state())&&m.key().equals(request.manifestKey())&&RuleRegistry.digest(m).equals(request.manifestSha256())&&tuple.size()==4&&new HashSet<>(tuple).size()==4&&request.first().isEmpty()&&request.second().isEmpty(),"I10 exact ordered tuple/state required");
                var inputs=index(supplied);var sources=new HbondCandidateSources(s,m,request,List.copyOf(inputs.values()));
                var plans=inputs.values().stream().filter(e->e.evidenceType().equals("athena:continuous-geometry-plan")).toList();require(plans.size()==1,"one selected I10 geometry plan required");var plan=read(plans.getFirst());verifyPlan(plan,tuple);
                var geometry=RuleRegistry.decode(SystemStateView.bytes(HbondCandidateRules.parameter(m,"geometry")));
                var gr=new RuleRequest(s.binding(),geometry.key(),RuleRegistry.digest(geometry),tuple.stream().sorted().toList(),List.of(),List.of(),request.radiusAngstrom(),0,request.maximumNodes(),request.maximumCandidates());
                var raw=RuleAnalyzers.collector(geometry,gr).analyze(s,plans,Map.of()).getFirst().measurements().get("payload");
                var values=new TreeMap<String,String>();values.put("payload",raw);values.put("proposition","SELECTED_CARBON_HALOGEN_CARBONYL_DIRECTIONAL_CANDIDATE");values.put("sourcePins",canonical(sources.pins()));values.put("tuple",canonical(tuple));values.put("definitionSha256",RuleRegistry.digest(m));
                if(!evaluate)return List.of(finding(s,SUPPORTED_PRESENT,values,"Raw selected I10 geometry; scientific eligibility not evaluated"));
                var measurements=inputs.values().stream().filter(e->e.evidenceType().equals("athena:rule-measurements")).toList();require(measurements.size()==1&&measurements.getFirst().method().equals(HalogenCarbonylRules.method(m,false))&&read(measurements.getFirst()).equals(JSON.readTree(raw)),"I10 measurement replay mismatch");
                var at=S1Qualification.current(m,s,request,inputs);if(at.isEmpty())return List.of(finding(s,NOT_EVALUATED,values,"Independent current I10 qualification absent"));
                var donor=component(s,tuple.get(0),tuple.get(1));var acceptor=component(s,tuple.get(2),tuple.get(3));
                if(donor==null||acceptor==null)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Incomplete or ambiguous source correspondence"));
                if(donor.identity().equals(acceptor.identity()))return List.of(finding(s,UNSUPPORTED,values,"Same-component tuple outside selected intercomponent profile"));
                for(var c:List.of(donor,acceptor)) {
                    var scope=S1SourceScope.check(s,c,inputs);
                    for(var witness:scope.witnesses())if(!S1Qualification.scope(witness,s,inputs,at.orElseThrow()))return List.of(finding(s,NOT_EVALUATED,values,"Independent exact source-scope authority absent"));
                    if(scope.conflicting()||scope.witnesses().isEmpty())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Source-scope evidence missing or conflicting"));
                    if(scope.knownOutside())return List.of(finding(s,UNSUPPORTED,values,"Known original nonordinary connection: "+scope.reasons()));
                    if(!scope.complete())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Source coverage unresolved"));
                }
                var dr=sources.report(donor,DONOR);var ar=sources.report(acceptor,ACCEPTOR);
                if(dr==null||ar==null)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Applicable source role coverage incomplete"));
                for(var report:List.of(dr,ar)) {
                    var status=sourceFacts(s,report,inputs.values());if(status!=SUPPORTED_PRESENT)return List.of(finding(s,status,values,"Source facts incomplete, conflicting or outside closed-shell domain"));
                }
                var d=donor.chemistry().atom(sourceId(donor,tuple.get(0))).orElseThrow();var x=donor.chemistry().atom(sourceId(donor,tuple.get(1))).orElseThrow();
                values.put("halogenElement",x.element());values.put("carbonEnvironment",d.aromatic()?"AROMATIC_C":"NONAROMATIC_C");
                var o=acceptor.chemistry().atom(sourceId(acceptor,tuple.get(2))).orElseThrow();var ac=acceptor.chemistry().atom(sourceId(acceptor,tuple.get(3))).orElseThrow();
                if(!Set.of("Cl","Br","I").contains(x.element())||!d.element().equals("C")||d.formalCharge()!=0||x.formalCharge()!=0||x.aromatic()||!o.element().equals("O")||o.formalCharge()!=0||!ac.element().equals("C")||ac.formalCharge()!=0)return List.of(finding(s,UNSUPPORTED,values,"Selected atoms outside neutral carbon-bound Cl/Br/I and neutral carbonyl core"));
                if(!dr.complete()||!ar.complete())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Applicable source role coverage incomplete"));
                if(!roles(dr,List.of("carbonAttachment","halogen"),tuple.subList(0,2))||!roles(ar,List.of("carbonylOxygen","carbonylCarbon"),tuple.subList(2,4)))return List.of(finding(s,UNSUPPORTED,values,"Complete source roles exclude the selected tuple"));
                var bonds=donor.chemistry().bonds().stream().filter(b->b.firstAtomId().equals(x.id())||b.secondAtomId().equals(x.id())).toList();
                if(bonds.size()!=1||bonds.getFirst().order()!=totah.lab.athena.design.backend.MolecularGraph.BondOrder.SINGLE||bonds.getFirst().aromatic())return List.of(finding(s,UNSUPPORTED,values,"Halogen requires exactly one ordinary nonaromatic single connection"));
                var ops=JSON.readTree(raw).path("operations");Double distance=value(ops,0,"distanceAngstrom"),donorAngle=value(ops,1,"angleDegrees"),acceptorAngle=value(ops,2,"angleDegrees");
                if(distance==null||donorAngle==null||acceptorAngle==null)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Three finite qualified same-frame quantities required"));
                values.put("distanceAngstrom",distance.toString());values.put("donorAngleDegrees",donorAngle.toString());values.put("acceptorAngleDegrees",acceptorAngle.toString());
                return List.of(finding(s,predicate(distance,donorAngle,acceptorAngle)?SUPPORTED_PRESENT:ABSENT_FALSE,values,"Exact eligible selected-tuple operational geometry only; no whole-system absence or energy claim"));
            }
            private Finding finding(SystemStateView s,EvidenceInterpretation.Status status,Map<String,String> values,String reason){return new Finding(evaluate?"evaluate":"collect",List.of(s.subject()),status,values,List.of(reason),m.limitations());}
        };
    }
    static boolean predicate(double d,double a,double b){return Double.isFinite(d)&&Double.isFinite(a)&&Double.isFinite(b)&&d>0&&d<=3.5&&a>=130&&a<=180&&b>=80&&b<=140;}
    private static void verifyPlan(JsonNode plan,List<AtomReference> t) {
        var ops=plan.path("operations");require(plan.path("groups").isArray()&&plan.path("groups").isEmpty()&&plan.path("radiusAssignmentReference").isNull()&&ops.size()==3,"exact I10 geometry operations required");
        require(operation(ops.get(0),"DISTANCE",List.of(t.get(1),t.get(2)))&&operation(ops.get(1),"ANGLE",t.subList(0,3))&&operation(ops.get(2),"ANGLE",List.of(t.get(1),t.get(2),t.get(3))),"I10 tuple/geometry mismatch");
    }
    private static boolean operation(JsonNode n,String kind,List<AtomReference> atoms){return n.path("kind").asText().equals(kind)&&n.path("atoms").equals(JSON.valueToTree(atoms));}
    static Double value(JsonNode ops,int index,String name){var q=ops.path(index).path("quantities").path(name);if(!q.path("status").asText().equals("SUPPORTED_PRESENT")||!q.hasNonNull("value"))return null;double value=Double.parseDouble(q.path("value").asText());return Double.isFinite(value)?value:null;}
    static SystemStateView.Component component(SystemStateView s,AtomReference first,AtomReference second) {
        var found=s.components().stream().filter(c->c.correspondenceAlternatives().size()==1&&c.correspondenceAlternatives().getFirst().containsValue(first)&&c.correspondenceAlternatives().getFirst().containsValue(second)).toList();return found.size()==1?found.getFirst():null;
    }
    static String sourceId(SystemStateView.Component c,AtomReference atom){return c.correspondenceAlternatives().getFirst().entrySet().stream().filter(e->e.getValue().equals(atom)).map(Map.Entry::getKey).findFirst().orElseThrow();}
    static boolean roles(HbondCandidateSources.Report r,List<String> names,List<AtomReference> tuple) {
        var map=r.component().correspondenceAlternatives().getFirst();
        for(var occurrence:r.payload().path("occurrences"))for(var roles:occurrence.path("roleCorrespondenceAlternatives")) {
            var found=new ArrayList<AtomReference>();for(var name:names){var ids=roles.path(name);if(ids.size()==1)found.add(map.get(ids.get(0).asText()));}if(found.equals(tuple))return true;
        }return false;
    }
    private static EvidenceInterpretation.Status sourceFacts(SystemStateView s,HbondCandidateSources.Report r,Collection<EvidenceEnvelope> inputs)throws Exception {
        return sourceFacts(s,r.component(),r.payload().path("sourceCoverage"),inputs);
    }
    // The I14 leaf reuses these exact already-qualified source facts; no new chemistry authority.
    static EvidenceInterpretation.Status sourceFacts(SystemStateView s,SystemStateView.Component c,JsonNode coverage,Collection<EvidenceEnvelope> inputs)throws Exception {
        var map=c.correspondenceAlternatives().getFirst();
        if(map.size()!=c.chemistry().atoms().size()||new HashSet<>(map.values()).size()!=map.size())return UNKNOWN_INCONCLUSIVE;
        for(var e:inputs)if(e.evidenceType().equals("athena:group-source-coverage")){var other=read(e);if(other.path("componentReference").equals(JSON.valueToTree(c.identity()))&&!other.equals(coverage))return UNKNOWN_INCONCLUSIVE;}
        var expected=new HashSet<totah.lab.gaia.structure.Bond>();
        for(var b:c.chemistry().bonds())expected.add(new totah.lab.gaia.structure.Bond(map.get(b.firstAtomId()),map.get(b.secondAtomId()),totah.lab.gaia.chemistry.BondOrder.valueOf(b.order().name())));
        var actualBonds=new HashSet<totah.lab.gaia.structure.Bond>();s.graph().structure().bonds().stream().filter(b->map.containsValue(b.atom1())||map.containsValue(b.atom2())).forEach(actualBonds::add);
        if(!expected.equals(actualBonds)||s.graph().structure().getConnectivityMetadata().provenance()!=totah.lab.gaia.structure.ConnectivityProvenance.EXPLICIT)return UNKNOWN_INCONCLUSIVE;
        boolean outside=false;
        for(var a:c.chemistry().atoms()) {
            var fact=coverage.path("atomState").path(a.id());var charge=fact.path("formalCharge");var h=fact.path("implicitHydrogenCount");
            if(!fact.path("chargeStatus").asText().equals("SUPPORTED_PRESENT")||!charge.isIntegralNumber()||!charge.canConvertToInt()||charge.asInt()!=a.formalCharge()||!fact.path("aromaticityStatus").asText().equals("SUPPORTED_PRESENT")||!fact.path("aromaticityModel").asText().equals("OCL/2026.7.2")||!Set.of("EXPLICIT_GRAPH","AUTHORITATIVE_IMPLICIT").contains(fact.path("hydrogenMode").asText())||!h.isIntegralNumber()||!h.canConvertToInt()||h.asInt()<0)return UNKNOWN_INCONCLUSIVE;
            var ref=map.get(a.id());if(!s.atoms().containsKey(ref)||s.atoms().get(ref).getElement()==null||!s.atoms().get(ref).getElement().name().equalsIgnoreCase(a.element())||!s.charges().charges().containsKey(ref)||s.charges().charge(ref)!=a.formalCharge())return UNKNOWN_INCONCLUSIVE;
            String radical=a.properties().get(S1NitrogenPredicate.RADICAL);if(radical==null||!Set.of("NONE","S","D","T").contains(radical))return UNKNOWN_INCONCLUSIVE;outside |=!radical.equals("NONE");
            var actual=new TreeSet<String>();for(var b:c.chemistry().bonds()){String other=b.firstAtomId().equals(a.id())?b.secondAtomId():b.secondAtomId().equals(a.id())?b.firstAtomId():null;if(other!=null&&c.chemistry().atom(other).orElseThrow().element().equals("H"))actual.add(other);}
            var declared=new TreeSet<String>();if(!fact.path("explicitHydrogenAtomIds").isArray())return UNKNOWN_INCONCLUSIVE;for(var id:fact.path("explicitHydrogenAtomIds"))if(!id.isTextual()||!declared.add(id.asText()))return UNKNOWN_INCONCLUSIVE;if(!actual.equals(declared))return UNKNOWN_INCONCLUSIVE;
        }return outside?UNSUPPORTED:SUPPORTED_PRESENT;
    }
}
