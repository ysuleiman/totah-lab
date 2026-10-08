package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import totah.lab.athena.system.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.time.Instant;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Selected exact SAM / explicit-source-H water geometry only; no transfer or energy interpretation. */
final class SamWaterTetrelRules {
    static final String ID="ATHENA.G06.SAM_WATER_METHYL_TETREL_GEOMETRIC_CANDIDATE";
    private SamWaterTetrelRules() { }
    static void validate(RuleManifest m) {
        require(Set.of("athena-rule/2","athena-rule/3").contains(m.schema())&&m.ruleId().equals(ID)&&m.version().equals("1.0.0")&&m.profile().equals("ATHENA_G06_SAM_WATER_TETREL_V1")&&m.implementationVersion().equals("1")&&m.qualification()==SystemGraphCertificate.Status.NOT_EVALUATED&&m.family()==RuleManifest.Family.INTERACTION&&m.requiredCapabilities().isEmpty(),"G06 manifest contract");
        try(var in=SamWaterTetrelRules.class.getResourceAsStream("sam-water-tetrel-v1/"+ID+".rule.json")) {
            require(in!=null,"G06 definition missing");var n=JSON.readTree(in);
            for(var k:List.of("parameters","negativeCoverage","scientificSources","measurementsProduced","limitations"))require(JSON.valueToTree(m).get(k).equals(n.get(k)),"G06 definition changed: "+k);
        }catch(java.io.IOException ex){throw new IllegalArgumentException("G06 definition unreadable",ex);}
    }
    static ScientificReference method(RuleManifest m,boolean evaluation){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.sam-water-tetrel",m.key()+(evaluation?"/evaluate":"/collect"),RuleRegistry.digest(m));}
    static EvidenceInterpretation.Status predicate(Double distance,Double angle) {
        if(distance==null||angle==null||!Double.isFinite(distance)||!Double.isFinite(angle)||distance<0||angle<0||angle>180)return UNKNOWN_INCONCLUSIVE;
        return distance<=3.25&&angle>=160&&angle<=180?SUPPORTED_PRESENT:ABSENT_FALSE;
    }
    static SystemGraphAnalyzer analyzer(RuleManifest m,RuleRequest r,boolean evaluate) {
        validate(m);
        return new SystemGraphAnalyzer() {
            public ScientificReference method(){return SamWaterTetrelRules.method(m,evaluate);}
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return Set.of("athena:sam-source-binding","athena:group-identities","athena:group-source-coverage","athena:continuous-geometry-plan","athena:rule-measurements","athena:event-source","athena:source-artifact","athena:rule-manifest","athena:rule-policy-context","athena:rule-qualification-receipt","athena:rule-research-eligibility","athena:rule-implementation-qualification","athena:system-binding","athena:system-certificate","athena:rule-request","athena:system-state");}
            public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> supplied,Map<String,String> configuration)throws Exception {
                require(s.binding().equals(r.state())&&m.key().equals(r.manifestKey())&&RuleRegistry.digest(m).equals(r.manifestSha256())&&r.first().isEmpty()&&r.second().isEmpty(),"G06 exact request binding");
                var t=r.atoms();require(t.size()==5&&new HashSet<>(t).size()==5&&s.atoms().keySet().containsAll(t)&&t.get(3).compareTo(t.get(4))<0,"G06 ordered distinct [S,Cm,O,H1,H2] required");
                var inputs=index(supplied);var bs=inputs.values().stream().filter(e->e.evidenceType().equals("athena:sam-source-binding")).toList();
                if(bs.isEmpty())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,null,"SAM source binding absent"));
                require(bs.size()==1,"One exact SAM binding required");var binding=bs.getFirst();var checked=SamSourceFacts.check(s,binding,inputs,t);
                if(checked.component().correspondenceAlternatives().size()==1){var map=checked.component().correspondenceAlternatives().getFirst();for(int i=0;i<2;i++){String id=checked.mapping().get(i==0?"2":"1");if(id!=null&&map.containsKey(id))require(map.get(id).equals(t.get(i)),"G06 SAM anchor mismatch");}}
                var reports=inputs.values().stream().filter(e->e.evidenceType().equals("athena:group-identities")).toList();
                var chemistry=new HbondCandidateSources(s,m,r,reports);var water=WaterIdentity.assess(s,chemistry,t.get(2));
                var ws=water.oriented()?SUPPORTED_PRESENT:water.possible()?UNKNOWN_INCONCLUSIVE:UNSUPPORTED;
                if(water.oriented())require(water.hydrogen().stream().sorted().toList().equals(t.subList(3,5)),"G06 exact water H tuple mismatch");
                var ss=checked.status();if(checked.component().chemistry().atoms().size()>r.maximumNodes())ss=UNKNOWN_INCONCLUSIVE;
                var plans=inputs.values().stream().filter(e->e.evidenceType().equals("athena:continuous-geometry-plan")).toList();require(plans.size()==1,"One exact G06 geometry plan required");var plan=plans.getFirst();verifyPlan(read(plan),t);
                var gm=RuleRegistry.decode(SystemStateView.bytes(HbondCandidateRules.parameter(m,"geometry")));var gr=request(s,gm,t.subList(0,3).stream().sorted().toList(),r);
                var gc=RuleAnalyzers.collector(gm,gr);var raw=JSON.readTree(gc.analyze(s,List.of(plan),Map.of()).getFirst().measurements().get("payload"));
                var original=inputs.values().stream().filter(e->e.evidenceType().equals("athena:rule-measurements")&&e.method().equals(gc.method())).toList();require(original.size()==1&&read(original.getFirst()).equals(raw),"G06 original geometry replay mismatch");
                var p=JSON.createObjectNode();p.put("schema","athena-sam-water-tetrel-measurements/1");p.put("definition",ID+"/1");p.put("definitionSha256",RuleRegistry.digest(m));p.set("stateBinding",JSON.valueToTree(s.binding()));p.put("requestSha256",hash(r));p.set("samSourceBinding",JSON.valueToTree(pin(binding)));
                var tuple=p.putObject("tuple");tuple.set("sulfur",JSON.valueToTree(t.get(0)));tuple.set("methylCarbon",JSON.valueToTree(t.get(1)));tuple.set("waterOxygen",JSON.valueToTree(t.get(2)));tuple.set("waterHydrogens",JSON.valueToTree(t.subList(3,5)));
                p.put("samIdentityStatus",ss==SUPPORTED_PRESENT?NOT_EVALUATED.name():ss.name());p.put("waterIdentityStatus",ws==SUPPORTED_PRESENT?NOT_EVALUATED.name():ws.name());
                var geo=p.putObject("geometry");geo.set("plan",JSON.valueToTree(pin(plan)));geo.set("rawMeasurements",JSON.valueToTree(pin(original.getFirst())));
                Double d=HalogenCarbonylRules.value(raw.path("operations"),0,"distanceAngstrom"),a=HalogenCarbonylRules.value(raw.path("operations"),1,"angleDegrees");
                geo.set("methylOxygenDistance",quantity(d,"ANGSTROM"));geo.set("sulfurMethylOxygenAngle",quantity(a,"DEGREE"));
                var pins=new TreeMap<String,Object>();var consumed=new ArrayList<>(checked.consumed());consumed.addAll(reports);consumed.add(plan);consumed.add(original.getFirst());
                for(var e:inputs.values())if(e.evidenceType().equals("athena:group-source-coverage"))consumed.add(e);
                for(var e:consumed)pins.put(canonical(pin(e)),pin(e));p.set("sourcePins",JSON.valueToTree(pins.values()));
                p.put("assessment",NOT_EVALUATED.name());var reasons=new ArrayList<>(checked.reasons());reasons.addAll(water.reasons());p.set("reasons",JSON.valueToTree(reasons));p.set("limitations",JSON.valueToTree(m.limitations()));
                if(!evaluate)return List.of(finding(s,SUPPORTED_PRESENT,p,"Serialization only; chemistry and candidacy remain unqualified"));
                var prior=inputs.values().stream().filter(e->e.evidenceType().equals("athena:rule-measurements")&&e.method().equals(SamWaterTetrelRules.method(m,false))).toList();require(prior.size()==1&&read(prior.getFirst()).equals(p),"G06 collected payload replay mismatch");
                for(var e:inputs.values())pins.put(canonical(pin(e)),pin(e));p.set("sourcePins",JSON.valueToTree(pins.values()));
                var time=S1Qualification.current(m,s,r,inputs);var result=NOT_EVALUATED;
                if(time.isEmpty())reasons.add("Current G06 authority absent or expired");
                else if(ss!=SUPPORTED_PRESENT)result=ss;
                else if(ws!=SUPPORTED_PRESENT)result=ws;
                else if(!SamSourceFacts.authorized(checked,s,inputs,time.get()))reasons.add("Independent SAM source-fact authority absent");
                else {
                    boolean sam=admittedSam(m,s,r,inputs,time.get()),wat=admittedWater(m,s,r,inputs,water,time.get());
                    p.put("samIdentityStatus",sam?SUPPORTED_PRESENT.name():NOT_EVALUATED.name());p.put("waterIdentityStatus",wat?SUPPORTED_PRESENT.name():NOT_EVALUATED.name());
                    if(sam&&wat)result=predicate(d,a);else reasons.add("Independent current SAM or water admission absent");
                }
                p.put("assessment",result.name());p.set("reasons",JSON.valueToTree(reasons));return List.of(finding(s,result,p,"Selected methyl-tetrel geometric candidate only"));
            }
            private Finding finding(SystemStateView s,EvidenceInterpretation.Status status,ObjectNode p,String reason){return new Finding(evaluate?"evaluate":"collect",List.of(s.subject()),status,p==null?Map.of():Map.of("payload",canonical(p)),List.of(reason),m.limitations());}
        };
    }
    private static RuleRequest request(SystemStateView s,RuleManifest m,List<AtomReference> atoms,RuleRequest r){return new RuleRequest(s.binding(),m.key(),RuleRegistry.digest(m),atoms,List.of(),List.of(),r.radiusAngstrom(),0,r.maximumNodes(),r.maximumCandidates());}
    private static boolean admittedSam(RuleManifest m,SystemStateView s,RuleRequest r,Map<String,EvidenceEnvelope> inputs,Instant at)throws Exception {
        for(var e:inputs.values())if(e.evidenceType().equals("athena:rule-manifest")) {
            var n=read(e);if(!n.path("ruleId").asText().equals(SamG05Rules.IDENTITY))continue;var sm=RuleRegistry.decode(e.readPayload());SamG05Rules.validate(sm);
            var sr=request(s,sm,r.atoms().subList(0,2),r);var time=S1Qualification.current(sm,s,sr,inputs);
            if(time.isPresent()&&time.get().equals(at))return true;
        }return false;
    }
    private static boolean admittedWater(RuleManifest m,SystemStateView s,RuleRequest r,Map<String,EvidenceEnvelope> inputs,WaterIdentity.Result water,Instant at)throws Exception {
        var baseline=RuleRegistry.decode(SystemStateView.bytes(HbondCandidateRules.parameter(m,"sources").get(WaterBridgeInputs.WATER)));
        var report=water.anchor().report();
        var coverage=new ArrayList<EvidenceEnvelope>();for(var e:inputs.values())if(e.evidenceType().equals("athena:group-source-coverage")&&read(e).equals(report.payload().path("sourceCoverage")))coverage.add(e);
        if(coverage.size()!=1)return false;
        for(var e:inputs.values())if(e.evidenceType().equals("athena:rule-manifest")) {
            var n=read(e);if(!n.path("ruleId").asText().equals(WaterBridgeInputs.WATER))continue;var wm=RuleRegistry.decode(e.readPayload());FunctionalGroupRules.validate(wm);
            require(wm.parameters().equals(baseline.parameters())&&wm.negativeCoverage().equals(baseline.negativeCoverage())&&wm.version().equals(baseline.version())&&wm.implementationVersion().equals(baseline.implementationVersion()),"G06 water admission definition mismatch");
            if(wm.scientificSources().stream().noneMatch(p->p.sha256().equals(report.envelope().payloadSha256()))||wm.scientificSources().stream().noneMatch(p->p.sha256().equals(coverage.getFirst().payloadSha256())))continue;
            var time=S1Qualification.current(wm,s,request(s,wm,List.of(),r),inputs);if(time.isPresent()&&time.get().equals(at))return true;
        }return false;
    }
    private static ObjectNode quantity(Double value,String unit){var n=JSON.createObjectNode();boolean defined=value!=null&&Double.isFinite(value);n.put("status",defined?SUPPORTED_PRESENT.name():UNKNOWN_INCONCLUSIVE.name());if(defined)n.put("value",value);else n.putNull("value");n.put("unit",unit);return n;}
    private static void verifyPlan(JsonNode n,List<AtomReference> t){var ops=n.path("operations");require(n.path("groups").isArray()&&n.path("groups").isEmpty()&&n.path("radiusAssignmentReference").isNull()&&ops.isArray()&&ops.size()==2,"G06 exact two-operation plan");require(ops.get(0).path("kind").asText().equals("DISTANCE")&&ops.get(0).path("atoms").equals(JSON.valueToTree(List.of(t.get(1),t.get(2))))&&ops.get(1).path("kind").asText().equals("ANGLE")&&ops.get(1).path("atoms").equals(JSON.valueToTree(t.subList(0,3))),"G06 geometry tuple/order mismatch");}
}
