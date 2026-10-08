package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import totah.lab.athena.system.*;
import totah.lab.gaia.structure.*;
import totah.lab.mnemosyne.*;
import java.io.IOException;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Governed V11 selected-residue evaluation. Source geometry never becomes an inferred source state. */
final class CbetaDeviationRules {
    static final String ID="ATHENA.V11.L_SER_THR_VAL_UNCORRECTED_CB_DEVIATION";
    static final String RESOURCE="/totah/lab/athena/system/rules/cbeta-deviation-v1/";
    static final String SOURCES_SHA="74da7d22341a36fa79d0a8ec1f0b72415384d8668902f7ac9ad493b9bdf706d7";
    private CbetaDeviationRules() { }
    static byte[] resource(String name)throws IOException {try(var in=CbetaDeviationRules.class.getResourceAsStream(RESOURCE+name)){if(in==null)throw new IOException("Missing V11 reference artifact "+name);return in.readAllBytes();}}
    static void validate(RuleManifest m) {
        require(Set.of("athena-rule/2","athena-rule/3").contains(m.schema())&&m.ruleId().equals(ID)&&m.version().equals("1.0.0")&&m.profile().equals("ATHENA_CBETA_DEVIATION_V1")&&m.implementationVersion().equals("1")&&m.qualification()==SystemGraphCertificate.Status.NOT_EVALUATED&&m.family()==RuleManifest.Family.VALIDATOR&&m.requiredCapabilities().isEmpty(),"Exact bounded V11 manifest required");
        try{var original=JSON.readTree(resource(ID+".rule.json"));for(var k:List.of("parameters","requiredChemistry","requiredGeometry","measurementsProduced","negativeCoverage","scientificSources","limitations"))require(JSON.valueToTree(m).get(k).equals(original.get(k)),"V11 definition altered: "+k);}catch(IOException e){throw new IllegalArgumentException("V11 definition unavailable",e);}
    }
    static ScientificReference method(RuleManifest m,boolean evaluate){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.cbeta-deviation",m.key()+(evaluate?"/evaluate":"/collect"),RuleRegistry.digest(m));}
    static SystemGraphAnalyzer analyzer(RuleManifest m,RuleRequest r,boolean evaluate) {
        validate(m);
        return new SystemGraphAnalyzer() {
            public ScientificReference method(){return CbetaDeviationRules.method(m,evaluate);}
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return Set.of("athena:residue-context-binding","athena:group-source-coverage","athena:rule-measurements","athena:event-source","athena:source-artifact","athena:rule-manifest","athena:rule-policy-context","athena:rule-qualification-receipt","athena:rule-research-eligibility","athena:rule-implementation-qualification","athena:system-binding","athena:system-certificate","athena:rule-request","athena:system-state");}
            public List<Finding> analyze(SystemStateView state,List<EvidenceEnvelope> supplied,Map<String,String> config)throws Exception {
                require(state.binding().equals(r.state())&&r.manifestKey().equals(m.key())&&r.manifestSha256().equals(RuleRegistry.digest(m))&&r.first().size()==1&&r.second().isEmpty(),"Exact V11 selected-residue request");
                var inputs=index(supplied);var bs=inputs.values().stream().filter(e->e.evidenceType().equals("athena:residue-context-binding")).toList();
                if(bs.isEmpty())return List.of(finding(state,NOT_EVALUATED,null,"Exact source context absent"));require(bs.size()==1,"One selected source context");
                var binding=bs.getFirst();var c=ResidueContextSource.check(state,binding,inputs);
                require(ResidueContextSource.residue(c.binding().path("central").path("residue")).equals(r.first().getFirst()),"Selected central residue mismatch");
                var tuple=new ArrayList<AtomReference>();for(var role:List.of("N","CA","C","CB")){var a=c.coordinate("central",role);if(a!=null)tuple.add(a);}
                // An incomplete correspondence has no well-formed four-atom measurement payload.
                if(tuple.size()!=4){var time=S1Qualification.current(m,state,r,inputs);boolean auth=time.isPresent()&&ResidueContextSource.authorized(c,state,inputs,time.get());var status=auth?c.status():NOT_EVALUATED;if(status==SUPPORTED_PRESENT)status=Set.of("SER","THR","VAL").contains(c.identity("central") == null?"":c.identity("central"))?UNKNOWN_INCONCLUSIVE:UNSUPPORTED;return List.of(finding(state,status,null,"Incomplete or excluded source tuple; no synthetic atom correspondence"));}
                require(new HashSet<>(tuple).size()==4&&r.atoms().equals(tuple.stream().sorted().toList()),"Exact source N/CA/C/CB tuple request");
                var p=JSON.createObjectNode();p.put("schema","athena-cbeta-deviation-measurements/1");p.put("definition",ID+"/1");p.put("definitionSha256",RuleRegistry.digest(m));p.set("stateBinding",JSON.valueToTree(state.binding()));p.put("requestSha256",hash(r));p.set("contextBinding",JSON.valueToTree(pin(binding)));p.set("centralResidue",c.binding().path("central").path("residue"));p.set("atomTuple",JSON.valueToTree(tuple));
                p.put("sourceAdmissionStatus",NOT_EVALUATED.name());p.set("sourceFactStatuses",JSON.valueToTree(c.statuses()));p.put("authorityStatus",NOT_EVALUATED.name());
                var consumed=new TreeMap<String,Object>();for(var e:c.consumed())consumed.put(canonical(pin(e)),pin(e));var reasons=new TreeSet<>(c.reasons());
                var profile=p.putObject("referenceProfile");profile.put("repository","cctbx/cctbx_project");profile.put("commit","ed314689c2d2945d7fd136d5f66d33ce1688e7a7");profile.put("numericProfile",CbetaReferenceGeometry.PROFILE);profile.put("phiPsiCorrection",false);profile.putNull("parameterClass");profile.putNull("parameters");
                var artifacts=new TreeMap<String,Object>();boolean complete=true;byte[] bytes=resource("SOURCES.json");require(EvidenceExchange.sha256(bytes).equals(SOURCES_SHA),"V11 reference manifest changed");
                var specifications=JSON.readTree(bytes);
                for(var spec:specifications){String file=spec.path("file").asText(),sha=spec.path("sha256").asText();require(EvidenceExchange.sha256(resource(file)).equals(sha),"V11 reference bytes changed");var matches=inputs.values().stream().filter(e->e.evidenceType().equals("athena:source-artifact")&&e.payloadSha256().equals(sha)).toList();Object pin;
                    if(matches.isEmpty()){complete=false;pin=Map.of("reference",new ScientificReference(ScientificReference.Kind.EVIDENCE_ENVELOPE,"athena.cbeta-reference.required",file,sha),"sha256",sha);}else pin=pin(matches.getFirst());artifacts.put(canonical(pin),pin);if(!matches.isEmpty())consumed.put(canonical(pin),pin);
                }
                profile.set("artifacts",JSON.valueToTree(artifacts.values()));var guard=profile.putObject("normalizationGuard");guard.put("operator","GT");guard.put("value",CbetaReferenceGeometry.GUARD);guard.put("binary64Hex",ResidueReferenceTables.bits(CbetaReferenceGeometry.GUARD));var boundary=profile.putObject("outlierBoundary");boundary.put("operator","GE");boundary.put("value",.25);boundary.put("binary64Hex",ResidueReferenceTables.bits(.25));boundary.put("unit","ANGSTROM");
                reconstruction(p,NOT_EVALUATED,null,"NOT_EVALUATED");p.putNull("category");p.put("assessmentStatus",NOT_EVALUATED.name());p.set("sourcePins",JSON.valueToTree(consumed.values()));p.set("reasons",JSON.valueToTree(reasons));p.set("limitations",JSON.valueToTree(m.limitations()));
                if(!evaluate)return List.of(finding(state,SUPPORTED_PRESENT,p,"Bound source geometry identity only; no reference construction or category authority"));
                var previous=inputs.values().stream().filter(e->e.evidenceType().equals("athena:rule-measurements")&&e.method().equals(CbetaDeviationRules.method(m,false))).toList();require(previous.size()==1&&canonical(read(previous.getFirst())).equals(canonical(p)),"Exact V11 collected payload replay required");
                if(r.maximumNodes()<4||r.maximumCandidates()<1){reasons.add("Operational budget cannot cover the selected tuple");p.set("reasons",JSON.valueToTree(reasons));return List.of(finding(state,NOT_EVALUATED,p,"Selected tuple budget incomplete"));}
                var time=S1Qualification.current(m,state,r,inputs);boolean auth=time.isPresent()&&ResidueContextSource.authorized(c,state,inputs,time.get());
                var admission=auth?c.status():NOT_EVALUATED;if(admission==SUPPORTED_PRESENT)admission=ResidueValidationRules.rotamerDomain(c,c.identity("central"));var status=admission;
                p.put("authorityStatus",auth?SUPPORTED_PRESENT.name():NOT_EVALUATED.name());p.put("sourceAdmissionStatus",admission.name());
                if(!auth)reasons.add("Independent current V11/source authority unavailable");
                if(status==SUPPORTED_PRESENT&&!complete){status=UNKNOWN_INCONCLUSIVE;reasons.add("Exact reference artifacts unavailable");}
                if(r.maximumNodes()<4||r.maximumCandidates()<1){status=NOT_EVALUATED;reasons.add("Operational budget cannot cover the selected tuple");}
                if(status==SUPPORTED_PRESENT){String id=c.identity("central");profile.put("parameterClass",id.equals("SER")?"SER":"THR_VAL");var params=profile.putArray("parameters");for(double v:CbetaReferenceGeometry.parameters(id)){var q=params.addObject();q.put("value",v);q.put("binary64Hex",ResidueReferenceTables.bits(v));}
                    var points=new ArrayList<CbetaReferenceGeometry.V>();for(var a:tuple){var v=state.atoms().get(a).getPosition();points.add(new CbetaReferenceGeometry.V(v.x(),v.y(),v.z()));}
                    var result=CbetaReferenceGeometry.calculate(id,points.get(0),points.get(1),points.get(2),points.get(3));status=result.deviation()==null?UNKNOWN_INCONCLUSIVE:SUPPORTED_PRESENT;reconstruction(p,status,result,result.degeneracy());
                    if(result.deviation()!=null)p.put("category",CbetaReferenceGeometry.category(result.deviation()));else reasons.add("Undefined approved reconstruction: "+result.degeneracy());
                }else reconstruction(p,status,null,"NOT_EVALUATED");
                p.put("assessmentStatus",status.name());for(var e:inputs.values())consumed.put(canonical(pin(e)),pin(e));p.set("sourcePins",JSON.valueToTree(consumed.values()));p.set("reasons",JSON.valueToTree(reasons));return List.of(finding(state,status,p,"Selected uncorrected reference-geometry classification only"));
            }
            private Finding finding(SystemStateView state,EvidenceInterpretation.Status status,ObjectNode p,String reason){return new Finding(evaluate?"evaluate":"collect",List.of(state.subject()),status,p==null?Map.of():Map.of("payload",canonical(p)),List.of(reason),m.limitations());}
        };
    }
    private static void reconstruction(ObjectNode p,EvidenceInterpretation.Status status,CbetaReferenceGeometry.Result r,String why){var n=p.putObject("reconstruction");n.put("status",status.name());n.put("kind","COMPUTED_REFERENCE_POSITION");point(n,"firstConstruction",r==null?null:r.first());point(n,"secondConstruction",r==null?null:r.second());point(n,"normalizedReference",r==null?null:r.ideal());n.put("degeneracy",why);var q=p.putObject("deviation");q.put("status",status.name());q.put("unit","ANGSTROM");if(r==null||r.deviation()==null){q.putNull("value");q.putNull("binary64Hex");}else{q.put("value",r.deviation());q.put("binary64Hex",ResidueReferenceTables.bits(r.deviation()));}}
    private static void point(ObjectNode n,String key,CbetaReferenceGeometry.V p){if(p==null){n.putNull(key);return;}var v=n.putObject(key);v.set("value",JSON.valueToTree(List.of(p.x(),p.y(),p.z())));v.set("binary64Hex",JSON.valueToTree(List.of(ResidueReferenceTables.bits(p.x()),ResidueReferenceTables.bits(p.y()),ResidueReferenceTables.bits(p.z()))));v.put("unit","ANGSTROM");}
}
