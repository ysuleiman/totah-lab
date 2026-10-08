package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import totah.lab.athena.system.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Two bounded G05 leaves. Continuous measurements never imply favorable interaction or methyl transfer. */
final class SamG05Rules {
    static final String IDENTITY="ATHENA.G05.SAM_CHEBI_142094_SOURCE_IDENTITY";
    static final String GEOMETRY="ATHENA.G05.SAM_PHE_CONTINUOUS_GEOMETRY";
    private SamG05Rules() { }
    static void validate(RuleManifest m) {
        require(Set.of("athena-rule/2","athena-rule/3").contains(m.schema())&&Set.of(IDENTITY,GEOMETRY).contains(m.ruleId())&&m.version().equals("1.0.0")&&m.profile().equals("ATHENA_G05_SAM_CHEBI142094_V1")&&m.implementationVersion().equals("1")&&m.qualification()==SystemGraphCertificate.Status.NOT_EVALUATED&&m.family()==RuleManifest.Family.INTERACTION&&m.requiredCapabilities().isEmpty(),"G05 manifest contract");
        try(var in=SamG05Rules.class.getResourceAsStream("sam-g05-v1/"+m.ruleId()+".rule.json")) {
            require(in!=null,"G05 definition missing");var definition=JSON.readTree(in);
            require(JSON.valueToTree(m.parameters()).equals(definition.get("parameters"))&&JSON.valueToTree(m.negativeCoverage()).equals(definition.get("negativeCoverage"))&&JSON.valueToTree(m.scientificSources()).equals(definition.get("scientificSources")),"G05 definition changed");
        }catch(java.io.IOException ex){throw new IllegalArgumentException("G05 definition unreadable",ex);}
    }
    static ScientificReference method(RuleManifest m,boolean evaluate){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.sam-g05",m.key()+(evaluate?"/evaluate":"/collect"),RuleRegistry.digest(m));}
    static SystemGraphAnalyzer analyzer(RuleManifest m,RuleRequest request,boolean evaluate) {
        validate(m);boolean geometry=m.ruleId().equals(GEOMETRY);
        return new SystemGraphAnalyzer() {
            public ScientificReference method(){return SamG05Rules.method(m,evaluate);}
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return Set.of("athena:sam-source-binding","athena:group-identities","athena:group-source-coverage","athena:continuous-geometry-plan","athena:rule-measurements","athena:event-source","athena:source-artifact","athena:rule-manifest","athena:rule-policy-context","athena:rule-qualification-receipt","athena:rule-research-eligibility","athena:rule-implementation-qualification","athena:system-binding","athena:system-certificate","athena:rule-request","athena:system-state");}
            public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> supplied,Map<String,String> configuration)throws Exception {
                require(s.binding().equals(request.state())&&m.key().equals(request.manifestKey())&&RuleRegistry.digest(m).equals(request.manifestSha256())&&request.first().isEmpty()&&request.second().isEmpty(),"G05 exact request/state binding");
                require(request.atoms().size()==(geometry?8:2)&&new HashSet<>(request.atoms()).size()==request.atoms().size(),"G05 ordered [S,Cm,(six Phe atoms)] tuple required");
                var inputs=index(supplied);var bindings=inputs.values().stream().filter(e->e.evidenceType().equals("athena:sam-source-binding")).toList();
                if(bindings.isEmpty())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,null,"Selected SAM source binding absent"));
                require(bindings.size()==1,"One exact G05 source binding required");
                var binding=bindings.getFirst();var checked=SamSourceFacts.check(s,binding,inputs,geometry?request.atoms():List.of());
                var payload=JSON.createObjectNode();payload.put("schema","athena-sam-measurements/1");payload.put("definition",m.ruleId()+"/1");payload.put("definitionSha256",RuleRegistry.digest(m));payload.set("stateBinding",JSON.valueToTree(s.binding()));payload.put("requestSha256",hash(request));payload.set("sourceBinding",JSON.valueToTree(pin(binding)));payload.set("referenceAtomMap",JSON.valueToTree(checked.mapping()));
                var anchors=payload.putObject("anchors");AtomReference sulfur=null,methyl=null;
                if(checked.component().correspondenceAlternatives().size()==1){var map=checked.component().correspondenceAlternatives().getFirst();sulfur=checked.mapping().containsKey("2")?map.get(checked.mapping().get("2")):null;methyl=checked.mapping().containsKey("1")?map.get(checked.mapping().get("1")):null;}
                anchors.set("sulfur",JSON.valueToTree(sulfur));anchors.set("methylCarbon",JSON.valueToTree(methyl));
                if(sulfur!=null)require(sulfur.equals(request.atoms().get(0)),"G05 sulfur selection mismatch");if(methyl!=null)require(methyl.equals(request.atoms().get(1)),"G05 methyl selection mismatch");
                var status=checked.status();if(checked.component().chemistry().atoms().size()>request.maximumNodes())status=UNKNOWN_INCONCLUSIVE;
                var pins=new TreeMap<String,Object>();for(var e:checked.consumed())pins.put(canonical(pin(e)),pin(e));
                payload.putNull("geometry");var pheStatus=SUPPORTED_PRESENT;
                if(geometry) {
                    var plans=inputs.values().stream().filter(e->e.evidenceType().equals("athena:continuous-geometry-plan")).toList();require(plans.size()==1,"One G05 continuous plan required");var plan=plans.getFirst();var pn=read(plan);verifyPlan(pn,request.atoms());
                    var gm=RuleRegistry.decode(SystemStateView.bytes(HbondCandidateRules.parameter(m,"geometry")));
                    var gr=new RuleRequest(s.binding(),gm.key(),RuleRegistry.digest(gm),request.atoms().stream().sorted().toList(),List.of(),List.of(),request.radiusAngstrom(),0,request.maximumNodes(),request.maximumCandidates());
                    var collector=RuleAnalyzers.collector(gm,gr);var raw=JSON.readTree(collector.analyze(s,List.of(plan),Map.of()).getFirst().measurements().get("payload"));
                    var originals=inputs.values().stream().filter(e->e.evidenceType().equals("athena:rule-measurements")&&e.method().equals(collector.method())).toList();require(originals.size()==1&&read(originals.getFirst()).equals(raw),"G05 exact original geometry replay required");
                    var out=payload.putObject("geometry");out.set("plan",JSON.valueToTree(pin(plan)));out.set("rawMeasurements",JSON.valueToTree(pin(originals.getFirst())));
                    var ops=raw.path("operations");out.set("sulfurCentroidDistance",quantity(ops,0,"secondCentroidDistanceAngstrom","ANGSTROM"));out.set("methylCentroidDistance",quantity(ops,1,"secondCentroidDistanceAngstrom","ANGSTROM"));out.set("sulfurNormalAngle",quantity(ops,0,"secondCentroidNormalAngleDegrees","DEGREE"));out.set("methylNormalAngle",quantity(ops,1,"secondCentroidNormalAngleDegrees","DEGREE"));
                    pins.put(canonical(pin(plan)),pin(plan));pins.put(canonical(pin(originals.getFirst())),pin(originals.getFirst()));
                    pheStatus=SamPheSupport.check(s,m,request,inputs,request.atoms().subList(2,8),Optional.empty());
                    for(var e:inputs.values())if(Set.of("athena:group-identities","athena:group-source-coverage","athena:event-source").contains(e.evidenceType()))pins.put(canonical(pin(e)),pin(e));
                }
                payload.set("sourcePins",JSON.valueToTree(pins.values()));payload.put("identityStatus",status==SUPPORTED_PRESENT?NOT_EVALUATED.name():status.name());payload.set("reasons",JSON.valueToTree(checked.reasons()));payload.set("limitations",JSON.valueToTree(m.limitations()));
                if(!evaluate)return List.of(finding(s,SUPPORTED_PRESENT,payload,"Raw collection completed only; identityStatus is separate and no current scientific eligibility is asserted"));
                var previous=inputs.values().stream().filter(e->e.evidenceType().equals("athena:rule-measurements")&&e.method().equals(SamG05Rules.method(m,false))).toList();
                require(previous.size()==1&&read(previous.getFirst()).equals(payload),"G05 collected measurement replay mismatch");
                var time=S1Qualification.current(m,s,request,inputs);
                if(time.isEmpty())return List.of(finding(s,NOT_EVALUATED,payload,"Independent current G05 authority absent or expired"));
                if(status!=SUPPORTED_PRESENT)return List.of(finding(s,status,payload,"Source identity unresolved or excluded"));
                if(!SamSourceFacts.authorized(checked,s,inputs,time.get()))return List.of(finding(s,NOT_EVALUATED,payload,"Independent exact SAM source-fact authority absent"));
                payload.put("identityStatus",SUPPORTED_PRESENT.name());
                if(geometry) {
                    if(pheStatus!=SUPPORTED_PRESENT)return List.of(finding(s,pheStatus,payload,"Phe source eligibility incomplete or excluded"));
                    var governed=SamPheSupport.check(s,m,request,inputs,request.atoms().subList(2,8),time);if(governed!=SUPPORTED_PRESENT)return List.of(finding(s,governed,payload,"Independent Phe source authority missing"));
                    for(var q:payload.get("geometry"))if(q.isObject()&&q.has("status")&&!q.path("status").asText().equals("SUPPORTED_PRESENT"))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,payload,"Continuous geometry partially undefined; defined quantities retained separately"));
                }
                return List.of(finding(s,SUPPORTED_PRESENT,payload,geometry?"Exact selected continuous SAM/Phe geometry; no favorable-interaction classification":"Exact selected independently admitted ChEBI142094 source identity"));
            }
            private Finding finding(SystemStateView state,EvidenceInterpretation.Status status,ObjectNode payload,String reason){return new Finding(evaluate?"evaluate":"collect",List.of(state.subject()),status,payload==null?Map.of():Map.of("payload",canonical(payload)),List.of(reason),m.limitations());}
        };
    }
    private static JsonNode quantity(JsonNode operations,int index,String key,String unit) {
        Double v=HalogenCarbonylRules.value(operations,index,key);var n=JSON.createObjectNode();boolean defined=v!=null&&Double.isFinite(v);
        if(unit.equals("DEGREE"))defined&=operations.path(index).path("coverage").path("normalUniquenessStatus").asText().equals("SUPPORTED_PRESENT");
        n.put("status",defined?SUPPORTED_PRESENT.name():UNKNOWN_INCONCLUSIVE.name());if(defined)n.put("value",v);else n.putNull("value");n.put("unit",unit);return n;
    }
    private static void verifyPlan(JsonNode n,List<AtomReference> tuple) {
        var groups=n.path("groups");var ops=n.path("operations");require(groups.isArray()&&groups.size()==1&&ops.isArray()&&ops.size()==2&&n.path("radiusAssignmentReference").isNull(),"G05 exact pair/group plan");
        require(groups.get(0).path("atoms").equals(JSON.valueToTree(tuple.subList(2,8).stream().sorted().toList())),"G05 exact selected Phe ring correspondence");
        for(int i=0;i<2;i++)require(ops.get(i).path("kind").asText().equals("POINT_PAIR_GROUP")&&ops.get(i).path("atoms").equals(JSON.valueToTree(i==0?List.of(tuple.get(1),tuple.get(0)):List.of(tuple.get(0),tuple.get(1))))&&ops.get(i).path("groupId").equals(groups.get(0).path("id")),"G05 separate S/Cm geometry selection");
    }
}
