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

/** Governed selected-residue reference validation. No source chemistry is inferred from labels or geometry. */
final class ResidueValidationRules {
    static final String V09="ATHENA.V09.TOP8000_CCTBX_COMPILED_SIX_CLASS",V10="ATHENA.V10.TOP8000_CCTBX_SER_THR_VAL_CHI1";
    private ResidueValidationRules() { }
    static void validate(RuleManifest m) {
        require(Set.of("athena-rule/2","athena-rule/3").contains(m.schema())&&Set.of(V09,V10).contains(m.ruleId())&&m.version().equals("1.0.0")&&m.profile().equals("ATHENA_RESIDUE_REFERENCE_V1")&&m.implementationVersion().equals("1")&&m.qualification()==SystemGraphCertificate.Status.NOT_EVALUATED&&m.family()==RuleManifest.Family.VALIDATOR&&m.requiredCapabilities().isEmpty(),"V09/V10 exact manifest contract");
        try {var baseline=JSON.readTree(ResidueReferenceTables.resource(m.ruleId()+".rule.json"));for(var k:List.of("parameters","negativeCoverage","scientificSources","measurementsProduced","limitations"))require(JSON.valueToTree(m).get(k).equals(baseline.get(k)),"V09/V10 definition changed: "+k);}
        catch(IOException e){throw new IllegalArgumentException("V09/V10 reference definition unavailable",e);}
    }
    static ScientificReference method(RuleManifest m,boolean evaluation){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.residue-reference",m.key()+(evaluation?"/evaluate":"/collect"),RuleRegistry.digest(m));}
    static SystemGraphAnalyzer analyzer(RuleManifest m,RuleRequest r,boolean evaluate) {
        validate(m);boolean rama=m.ruleId().equals(V09);
        return new SystemGraphAnalyzer() {
            public ScientificReference method(){return ResidueValidationRules.method(m,evaluate);}
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return Set.of("athena:residue-context-binding","athena:group-source-coverage","athena:continuous-geometry-plan","athena:rule-measurements","athena:event-source","athena:source-artifact","athena:rule-manifest","athena:rule-policy-context","athena:rule-qualification-receipt","athena:rule-research-eligibility","athena:rule-implementation-qualification","athena:system-binding","athena:system-certificate","athena:rule-request","athena:system-state");}
            public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> supplied,Map<String,String> config)throws Exception {
                require(s.binding().equals(r.state())&&m.key().equals(r.manifestKey())&&RuleRegistry.digest(m).equals(r.manifestSha256())&&r.first().size()==1&&r.second().isEmpty(),"Exact selected-residue request required");
                var inputs=index(supplied);var bindings=inputs.values().stream().filter(e->e.evidenceType().equals("athena:residue-context-binding")).toList();
                if(bindings.isEmpty())return List.of(finding(s,NOT_EVALUATED,null,"Source context and independent exact-state review absent"));
                require(bindings.size()==1,"One exact residue context binding required");var context=bindings.getFirst();var checked=ResidueContextSource.check(s,context,inputs);
                require(ResidueContextSource.residue(checked.binding().path("central").path("residue")).equals(r.first().getFirst()),"Central residue request mismatch");
                var tuples=tuples(checked,rama);var atoms=new TreeSet<AtomReference>();tuples.values().forEach(atoms::addAll);
                require(r.atoms().equals(List.copyOf(atoms)),"Exact sorted union of required torsion tuples required");
                var p=JSON.createObjectNode();p.put("schema",rama?"athena-ramachandran-measurements/1":"athena-rotamer-measurements/1");p.put("definition",m.ruleId()+"/1");p.put("definitionSha256",RuleRegistry.digest(m));p.set("stateBinding",JSON.valueToTree(s.binding()));p.put("requestSha256",hash(r));p.set("contextBinding",JSON.valueToTree(pin(context)));p.set("centralResidue",checked.binding().path("central").path("residue"));
                p.put("sourceAdmissionStatus",NOT_EVALUATED.name());p.set("sourceFactStatuses",JSON.valueToTree(checked.statuses()));p.put("authorityStatus",NOT_EVALUATED.name());
                var consumed=new TreeMap<String,Object>();for(var e:checked.consumed())consumed.put(canonical(pin(e)),pin(e));
                var reasons=new TreeSet<>(checked.reasons());var values=geometry(s,m,r,inputs,tuples,p,consumed);
                ResidueReferenceTables tables=null;boolean referencesComplete=true;JsonNode sources=JSON.readTree(ResidueReferenceTables.resource("SOURCES.json"));
                try {tables=ResidueReferenceTables.load();}catch(IOException badReference){referencesComplete=false;reasons.add("Pinned reference package unavailable: "+badReference.getMessage());}
                var artifacts=new TreeMap<String,EvidenceEnvelope>();
                for(var spec:sources) {
                    var matches=inputs.values().stream().filter(e->e.evidenceType().equals("athena:source-artifact")&&e.payloadSha256().equals(spec.path("sha256").asText())).toList();
                    if(matches.isEmpty()){referencesComplete=false;continue;}
                    var e=matches.getFirst();artifacts.put(spec.path("file").asText(),e);consumed.put(canonical(pin(e)),pin(e));
                }
                var manifests=inputs.values().stream().filter(e->e.evidenceType().equals("athena:source-artifact")&&e.payloadSha256().equals(ResidueReferenceTables.MANIFEST_SHA)).toList();
                if(manifests.isEmpty())referencesComplete=false;
                if(!referencesComplete)reasons.add("Complete attributed reference artifacts missing; no sparse-zero fallback");
                // Resource pins still identify missing reference artifacts exactly; their absence prevents scoring.
                p.set("referenceProfile",profile(rama,sources,artifacts,manifests,consumed));
                var cs=p.putObject("classSelection");cs.put("status",NOT_EVALUATED.name());cs.putNull("canonicalIdentity");
                if(rama){cs.put("nextProline","UNKNOWN");cs.put("prolineCislike","UNKNOWN");cs.putNull("ramaClass");}else cs.putNull("rotamerClass");
                p.putNull("interpolation");score(p,NOT_EVALUATED,null);p.putNull("category");p.put("assessmentStatus",NOT_EVALUATED.name());p.set("sourcePins",JSON.valueToTree(consumed.values()));p.set("reasons",JSON.valueToTree(reasons));p.set("limitations",JSON.valueToTree(m.limitations()));
                if(!evaluate)return List.of(finding(s,SUPPORTED_PRESENT,p,"Raw selected torsions collected; no reference classification authority"));
                var prior=inputs.values().stream().filter(e->e.evidenceType().equals("athena:rule-measurements")&&e.method().equals(ResidueValidationRules.method(m,false))).toList();
                require(prior.size()==1&&canonical(read(prior.getFirst())).equals(canonical(p)),"Exact collected measurement replay required");
                var time=S1Qualification.current(m,s,r,inputs);boolean authorized=time.isPresent()&&ResidueContextSource.authorized(checked,s,inputs,time.get());
                p.put("authorityStatus",authorized?SUPPORTED_PRESENT.name():NOT_EVALUATED.name());
                var admission=authorized?checked.status():NOT_EVALUATED;String identity=checked.identity("central");
                if(authorized&&!rama&&admission==SUPPORTED_PRESENT)admission=rotamerDomain(checked,identity);
                p.put("sourceAdmissionStatus",admission.name());var result=admission;
                if(!authorized)reasons.add("Current exact rule/source-state authority missing, expired or incomplete");
                else if(result==SUPPORTED_PRESENT&&(!referencesComplete||values.size()!=tuples.size())){result=UNKNOWN_INCONCLUSIVE;reasons.add("Required torsion or reference package undefined/unavailable");}
                if(r.maximumNodes()<atoms.size()||r.maximumCandidates()<1){result=NOT_EVALUATED;reasons.add("Operational budget does not cover selected evaluation");}
                if(result==SUPPORTED_PRESENT) {
                    cs.put("status",SUPPORTED_PRESENT.name());cs.put("canonicalIdentity",identity);ResidueReferenceTables.Score q;String grid;
                    if(rama) {
                        String next=checked.facts().get("connections").path("nextProline").asText();require(Set.of("TRUE","FALSE").contains(next),"Eligible source next-PRO fact required");
                        Double omega=values.get("OMEGA_IN");int c=ResidueReferenceTables.ramaClass(identity,next.equals("TRUE"),omega);cs.put("nextProline",next);cs.put("prolineCislike",identity.equals("PRO")?(c==2?"TRUE":"FALSE"):"NOT_APPLICABLE");cs.put("ramaClass",ResidueReferenceTables.CLASSES.get(c));
                        q=tables.rama(c,values.get("PHI"),values.get("PSI"));grid=ResidueReferenceTables.ramaFile(c);((ObjectNode)p.path("referenceProfile").path("thresholds")).put("allowedMinimum",ResidueReferenceTables.allowed(c));
                    } else {
                        cs.put("rotamerClass",identity+"_CHI1");q=tables.rotamer(identity,values.get("CHI1"));grid=ResidueReferenceTables.rotamerFile(identity);((ObjectNode)p.path("referenceProfile").path("thresholds")).put("allowedMinimum",0.003);
                    }
                    ((ObjectNode)p.get("referenceProfile")).set("selectedGrid",JSON.valueToTree(pin(artifacts.get(grid))));p.set("interpolation",q.interpolation());score(p,SUPPORTED_PRESENT,q.value());p.put("category",q.category());
                }else {cs.put("status",result.name());score(p,result,null);}
                p.put("assessmentStatus",result.name());p.set("reasons",JSON.valueToTree(reasons));
                // Governance is an explicitly consumed part of the result, never silently inherited history.
                for(var e:inputs.values())consumed.put(canonical(pin(e)),pin(e));p.set("sourcePins",JSON.valueToTree(consumed.values()));
                return List.of(finding(s,result,p,"Selected reference category only; OUTLIER is a successful evaluated classification"));
            }
            private Finding finding(SystemStateView state,EvidenceInterpretation.Status status,ObjectNode p,String reason){return new Finding(evaluate?"evaluate":"collect",List.of(state.subject()),status,p==null?Map.of():Map.of("payload",canonical(p)),List.of(reason),m.limitations());}
        };
    }
    static EvidenceInterpretation.Status rotamerDomain(ResidueContextSource.Checked c,String id) {
        if(id==null)return UNKNOWN_INCONCLUSIVE;if(!Set.of("SER","THR","VAL").contains(id))return UNSUPPORTED;
        var roles=c.binding().path("central").path("roles");var expected=new TreeMap<String,Integer>();
        expected.put(canonical(roles.path("N")),1);expected.put(canonical(roles.path("CA")),1);expected.put(canonical(roles.path("C")),0);expected.put(canonical(roles.path("O")),0);
        expected.put(canonical(roles.path("CB")),id.equals("SER")?2:1);
        if(id.equals("SER"))expected.put(canonical(roles.path("OG")),1);
        else {expected.put(canonical(roles.path(id.equals("THR")?"OG1":"CG1")),id.equals("THR")?1:3);expected.put(canonical(roles.path("CG2")),3);}
        var found=new HashSet<String>();JsonNode row=null;
        for(var r:c.facts().get("identityState").path("residues"))if(r.path("residue").equals(c.binding().path("central").path("residue")))row=r;
        if(row==null)return UNKNOWN_INCONCLUSIVE;
        for(var a:row.path("atoms")) {
            if(a.path("element").asText().equals("H"))continue;String key=canonical(a.path("source"));found.add(key);
            if(a.path("formalCharge").isNull()||a.path("nonExplicitHydrogenCount").isNull()||a.path("aromatic").isNull())return UNKNOWN_INCONCLUSIVE;
            if(!expected.containsKey(key)||a.path("formalCharge").intValue()!=0||a.path("aromatic").booleanValue()||a.path("nonExplicitHydrogenCount").intValue()+a.path("explicitHydrogens").size()!=expected.get(key))return UNSUPPORTED;
        }
        if(!found.equals(expected.keySet()))return UNSUPPORTED;
        var wanted=new HashSet<String>();
        for(var pair:List.of(List.of("N","CA"),List.of("CA","C"),List.of("C","O"),List.of("CA","CB")))wanted.add(pairKey(roles.path(pair.get(0)),roles.path(pair.get(1)),pair.get(1).equals("O")?"DOUBLE":"SINGLE"));
        for(var role:id.equals("SER")?List.of("OG"):id.equals("THR")?List.of("OG1","CG2"):List.of("CG1","CG2"))wanted.add(pairKey(roles.path("CB"),roles.path(role),"SINGLE"));
        var actual=new HashSet<String>();for(var b:row.path("bonds"))if(found.contains(canonical(b.path("first")))&&found.contains(canonical(b.path("second"))))actual.add(pairKey(b.path("first"),b.path("second"),b.path("order").asText()));
        return actual.equals(wanted)?SUPPORTED_PRESENT:UNSUPPORTED;
    }
    private static String pairKey(JsonNode a,JsonNode b,String order){return List.of(canonical(a),canonical(b)).stream().sorted().reduce((x,y)->x+"|"+y).orElseThrow()+"|"+order;}
    private static Map<String,List<AtomReference>> tuples(ResidueContextSource.Checked c,boolean rama) {
        var out=new LinkedHashMap<String,List<AtomReference>>();
        if(rama){out.put("PHI",tuple(c.coordinate("previous","C"),c.coordinate("central","N"),c.coordinate("central","CA"),c.coordinate("central","C")));out.put("PSI",tuple(c.coordinate("central","N"),c.coordinate("central","CA"),c.coordinate("central","C"),c.coordinate("next","N")));if("PRO".equals(c.identity("central")))out.put("OMEGA_IN",tuple(c.coordinate("previous","CA"),c.coordinate("previous","C"),c.coordinate("central","N"),c.coordinate("central","CA")));}
        else {String id=c.identity("central");String role="SER".equals(id)?"OG":"THR".equals(id)?"OG1":"VAL".equals(id)?"CG1":null;out.put("CHI1",role==null?List.of():tuple(c.coordinate("central","N"),c.coordinate("central","CA"),c.coordinate("central","CB"),c.coordinate("central",role)));}return out;
    }
    private static List<AtomReference> tuple(AtomReference... atoms){if(Arrays.stream(atoms).anyMatch(Objects::isNull))return List.of();require(new HashSet<>(Arrays.asList(atoms)).size()==4,"Distinct torsion atoms required");return List.of(atoms);}
    private static Map<String,Double> geometry(SystemStateView s,RuleManifest m,RuleRequest r,Map<String,EvidenceEnvelope> inputs,Map<String,List<AtomReference>> tuples,ObjectNode payload,Map<String,Object> consumed)throws Exception {
        var result=new HashMap<String,Double>();var out=payload.putObject("geometry");out.putNull("plan");out.putNull("rawMeasurements");var torsions=out.putArray("torsions");
        var complete=tuples.entrySet().stream().filter(e->!e.getValue().isEmpty()).toList();JsonNode raw=null;
        var plans=inputs.values().stream().filter(e->e.evidenceType().equals("athena:continuous-geometry-plan")).toList();
        require(plans.size()<=1,"One selected residue geometry plan");
        if(!plans.isEmpty()) {
            var plan=plans.getFirst();var n=read(plan);require(n.path("groups").isArray()&&n.path("groups").isEmpty()&&n.path("radiusAssignmentReference").isNull()&&n.path("operations").isArray()&&n.path("operations").size()==complete.size(),"Exact residue DIHEDRAL plan required");
            for(int i=0;i<complete.size();i++){var op=n.path("operations").get(i);require(op.path("kind").asText().equals("DIHEDRAL")&&op.path("atoms").equals(JSON.valueToTree(complete.get(i).getValue())),"Exact ordered torsion tuple required");}
            var gm=RuleRegistry.decode(SystemStateView.bytes(HbondCandidateRules.parameter(m,"geometry")));var gr=new RuleRequest(s.binding(),gm.key(),RuleRegistry.digest(gm),r.atoms(),List.of(),List.of(),r.radiusAngstrom(),0,r.maximumNodes(),r.maximumCandidates());
            var collector=RuleAnalyzers.collector(gm,gr);raw=JSON.readTree(collector.analyze(s,List.of(plan),Map.of()).getFirst().measurements().get("payload"));
            var originals=inputs.values().stream().filter(e->e.evidenceType().equals("athena:rule-measurements")&&e.method().equals(collector.method())).toList();require(originals.size()==1&&canonical(read(originals.getFirst())).equals(canonical(raw)),"Original Athena DIHEDRAL measurement replay mismatch");
            out.set("plan",JSON.valueToTree(pin(plan)));out.set("rawMeasurements",JSON.valueToTree(pin(originals.getFirst())));for(var e:List.of(plan,originals.getFirst()))consumed.put(canonical(pin(e)),pin(e));
        }
        int op=0;for(var entry:tuples.entrySet()) {
            Double value=entry.getValue().isEmpty()||raw==null?null:HalogenCarbonylRules.value(raw.path("operations"),op,"torsionDegrees");if(!entry.getValue().isEmpty())op++;
            if(value!=null&&(!Double.isFinite(value)||Math.abs(value)>180))value=null;
            var n=torsions.addObject();n.put("name",entry.getKey());n.set("atoms",JSON.valueToTree(entry.getValue()));n.put("unit","DEGREE");n.put("status",value==null?UNKNOWN_INCONCLUSIVE.name():SUPPORTED_PRESENT.name());
            if(value==null){n.putNull("value");n.putNull("binary64Hex");}else{n.put("value",value);n.put("binary64Hex",ResidueReferenceTables.bits(value));result.put(entry.getKey(),value);}
        }return result;
    }
    private static void score(ObjectNode p,EvidenceInterpretation.Status status,Double value){var n=p.putObject("referenceScore");n.put("status",status.name());if(value==null){n.putNull("value");n.putNull("binary64Hex");}else{n.put("value",value);n.put("binary64Hex",ResidueReferenceTables.bits(value));}}
    private static ObjectNode profile(boolean rama,JsonNode sources,Map<String,EvidenceEnvelope> artifacts,List<EvidenceEnvelope> manifests,Map<String,Object> consumed) {
        var n=JSON.createObjectNode();n.put("dataRepository","rlabduke/reference_data");n.put("dataCommit","5ee6875fc29eccc3c9dc7cbe705ff9fd1b505d7d");n.put("evaluatorRepository","cctbx/cctbx_project");n.put("evaluatorCommit","ed314689c2d2945d7fd136d5f66d33ce1688e7a7");
        Object manifestPin=manifests.isEmpty()?expectedPin("SOURCES.json",ResidueReferenceTables.MANIFEST_SHA):pin(manifests.getFirst());n.set("sourceManifest",JSON.valueToTree(manifestPin));if(!manifests.isEmpty())consumed.put(canonical(manifestPin),manifestPin);
        var data=new TreeMap<String,Object>();var evaluators=new TreeMap<String,Object>();var licenses=new TreeMap<String,Object>();
        for(var spec:sources){String file=spec.path("file").asText();Object p=artifacts.containsKey(file)?pin(artifacts.get(file)):expectedPin(file,spec.path("sha256").asText());String key=canonical(p);
            if(file.endsWith(".data")){if(rama==file.contains("ramachandran_pct"))data.put(key,p);}else if(file.contains("LICENSE"))licenses.put(key,p);else evaluators.put(key,p);
        }
        n.set("datasetArtifacts",JSON.valueToTree(data.values()));n.set("evaluatorArtifacts",JSON.valueToTree(evaluators.values()));n.set("licenseArtifacts",JSON.valueToTree(licenses.values()));n.put("numericProfile",rama?"CCTBX_COMPILED_BINARY64_SIX_SIGNIFICANT_DIGITS":"CCTBX_NDIM_BINARY32_STORAGE_BINARY64_INTERPOLATION");n.putNull("selectedGrid");var thresholds=n.putObject("thresholds");thresholds.put("favoredMinimum",0.02);thresholds.putNull("allowedMinimum");return n;
    }
    private static Object expectedPin(String file,String sha){return Map.of("reference",new ScientificReference(ScientificReference.Kind.EVIDENCE_ENVELOPE,"athena.residue-reference.required",file,sha),"sha256",sha);}
}
