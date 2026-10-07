package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import totah.lab.athena.system.*;
import totah.lab.athena.interaction.*;
import totah.lab.athena.clash.StericClashAnalysis;
import totah.lab.gaia.graph.AtomDistanceCriterion;
import totah.lab.gaia.geometry.Dihedral;
import totah.lab.gaia.structure.*;
import totah.lab.mnemosyne.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;
import static totah.lab.athena.system.SystemGraphCertificate.Capability.*;

/** Separate collection and classification adapters. Definitions do not execute arbitrary code. */
public final class RuleAnalyzers {
    private RuleAnalyzers() { }
    private static final ObjectMapper JSON=new ObjectMapper().registerModule(new Jdk8Module());
    private static final ObjectMapper SCIENTIFIC_JSON=com.fasterxml.jackson.databind.json.JsonMapper.builder()
            .addModule(new Jdk8Module()).enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES).build();
    public static String nativeFamily(String id){return switch(id){
        case "INT.HBOND.001"->"HBOND";case "INT.HYDRO.001"->"HYDROPHOBIC";case "INT.SALT.001"->"SALT_BRIDGE";
        case "INT.PIPI.001"->"PI_STACKING";case "INT.CATPI.001"->"PI_CATION";
        default->throw new IllegalArgumentException("no native interaction implementation for "+id);};}
    public static String collectionKey(RuleManifest m,RuleRequest r) {
        var fields=new TreeMap<String,Object>();fields.put("state",r.state());fields.put("atoms",r.atoms());fields.put("first",r.first());fields.put("second",r.second());
        fields.put("radius",r.radiusAngstrom());fields.put("hops",r.maximumHops());fields.put("nodes",r.maximumNodes());fields.put("candidates",r.maximumCandidates());
        fields.put("rule",m.ruleId());fields.put("implementation",m.implementationId()+"/"+m.implementationVersion());
        // Preparation affects which donors can be measured. Classification cutoffs do not.
        if(m.implementationId().equals("athena.scientific"))fields.put("perceptionPatterns",m.parameters().entrySet().stream().filter(e->e.getKey().startsWith("pattern.")).toList());
        if(m.implementationId().equals("native.interaction")||m.implementationId().equals("athena.scientific"))fields.put("donorBondCutoff",m.parameters().get("donorBondCutoff"));
        if(m.implementationId().equals("native.clash"))fields.put("clashProtocol",m.parameters());
        return SystemStateView.digest(fields);
    }
    public static EvidenceSubject subjects(SystemStateView s,RuleRequest r) {
        var members=new ArrayList<EvidenceSubject>();members.add(s.subject());
        for(var a:r.atoms())members.add(new EvidenceSubject(s.identity(),"atom",a.toString(),List.of()));
        for(var residue:java.util.stream.Stream.concat(r.first().stream(),r.second().stream()).distinct().toList())members.add(new EvidenceSubject(s.identity(),"residue",residue.toString(),List.of()));
        return new EvidenceSubject(s.identity(),"collection","explicit-rule-subjects",members);
    }
    private abstract static class Adapter implements SystemGraphAnalyzer {
        final RuleManifest manifest;final RuleRequest request;final String stage;
        Adapter(RuleManifest m,RuleRequest r,String stage){manifest=m;request=r;this.stage=stage;}
        public ScientificReference method(){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.rules",manifest.key()+"/"+stage,RuleRegistry.digest(manifest));}
        public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
        public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
        void bound(SystemStateView s){if(!s.binding().equals(request.state())||!manifest.key().equals(request.manifestKey())||!RuleRegistry.digest(manifest).equals(request.manifestSha256()))throw new IllegalArgumentException("rule/state/request binding mismatch");}
        Finding finding(SystemStateView s,EvidenceInterpretation.Status status,Map<String,String> values,String reason){return new Finding(stage,List.of(subjects(s,request)),status,values,List.of(reason),manifest.limitations());}
    }
    public static SystemGraphAnalyzer collector(RuleManifest m,RuleRequest r){return collector(m,r,null);}
    public static SystemGraphAnalyzer collector(RuleManifest m,RuleRequest r,totah.lab.athena.design.backend.SubstructureMatcher matcher){
        if(m.implementationId().equals("athena.i03-n-sp3-s1"))return S1NitrogenRules.analyzer(m,r);
        if(m.implementationId().equals("athena.halogen-carbonyl"))return HalogenCarbonylRules.analyzer(m,r,false);
        if(m.implementationId().equals("athena.zinc-carbonyl"))return ZincCarbonylRules.analyzer(m,r,false);
        if(m.implementationId().equals("athena.selected-pharmacophore"))return SelectedPharmacophoreRules.analyzer(m,r,false);
        if(m.implementationId().equals("athena.source-fragment-parent"))return SourceFragmentParentRules.analyzer(m,r,false);
        if(m.implementationId().equals("athena.zn-source-features"))return ZnSourceFeatureRules.analyzer(m,r,false);
        if(m.implementationId().equals("athena.met-phe-survey"))return MetPheSurveyRules.analyzer(m,r,false);
        if(m.implementationId().equals("athena.implicit-h-proxy-s1"))return S1ImplicitHProxyRules.analyzer(m,r,false);
        if(m.implementationId().equals("athena.implicit-h-proxy"))return ImplicitHProxyRules.analyzer(m,r,false);
        if(m.implementationId().equals("athena.water-bridge"))return WaterBridgeRules.analyzer(m,r,false);
        if(m.implementationId().equals("athena.peptide-geometry"))return PeptideGeometryRules.analyzer(m,r,false);
        if(m.implementationId().equals("athena.hbond-candidate"))return HbondCandidateRules.analyzer(m,r,false);
        if(m.implementationId().equals("athena.cysteine-attribution"))return CysteineBackboneAttribution.analyzer(m,r);
        if(m.implementationId().equals("athena.ss-connectivity"))return SourceSulfurConnectivityRules.analyzer(m,r);
        if(m.implementationId().equals("athena.all-members-nonpolar"))return AllMembersNonpolarRules.analyzer(m,r);
        if(m.implementationId().equals("athena.charge-groups"))return ChargeGroupRules.analyzer(m,r,false);
        if(m.implementationId().equals("athena.aromatic-systems"))return AromaticSystemRules.analyzer(m,r,false);
        if(m.implementationId().equals("athena.geometry"))return ContinuousGeometryRules.analyzer(m,r,false);
        if(m.implementationId().equals("athena.group"))return FunctionalGroupRules.analyzer(m,r,matcher,false);
        return new Adapter(m,r,"measure") {
        public Set<SystemGraphCertificate.Capability> requires(){return Set.of(DISTANCE_QUERIES);}
        public Set<String> evidenceTypes(){return Set.of("athena:system-state","athena:rule-request","athena:rule-manifest");}
        public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> inputs,Map<String,String> config)throws Exception {
            bound(s);var payload=new TreeMap<String,Object>();payload.put("collectionKey",collectionKey(m,r));payload.put("state",s.binding());payload.put("collectorVersion","1");
            boolean complete=true;
            if(m.implementationId().equals("athena.scientific")) {
                var measurements=AthenaScientificRules.collect(s,m,r,matcher);
                payload.put("scientific",measurements);
                complete=measurements.negativeCoverage().values().stream().allMatch(Boolean::booleanValue);
            } else if(m.implementationId().equals("external.plip")||m.ruleId().equals("SULF.PI.001")) {
                payload.put("unsupported",true);payload.put("subjects",r.atoms());payload.put("missing",m.requiredChemistry());
            } else if(m.implementationId().equals("native.interaction")) {
                if(r.first().isEmpty()||r.second().isEmpty())throw new IllegalArgumentException("explicit nonempty partners required");
                var result=InteractionMeasurements.collect(nativeFamily(m.ruleId()),s.graph().view(r.first()).toStructure(),s.graph().view(r.second()).toStructure(),s.charges(),RuleRegistry.thresholds(m),r.maximumCandidates());
                payload.put("interaction",result);complete=result.complete();
                if(m.ruleId().equals("INT.HBOND.001")&&!s.protonationQualified()){complete=false;payload.put("missingProtonation",true);}
            } else if(m.ruleId().equals("GEO.PROX.001")) {
                if(r.atoms().size()!=2)throw new IllegalArgumentException("exactly two explicit atoms required");
                payload.put("atoms",r.atoms());payload.put("distanceAngstrom",s.atoms().get(r.atoms().get(0)).getPosition().distance(s.atoms().get(r.atoms().get(1)).getPosition()));
            } else if(Set.of("GEO.SHELL.001","GEO.PATH.001","SULF.ENV.001").contains(m.ruleId())) {
                var traversal=s.neighborhood(r.atoms(),AtomDistanceCriterion.heavyAtomsWithin(r.radiusAngstrom()),m.ruleId().equals("GEO.SHELL.001")?1:r.maximumHops(),r.maximumNodes(),false,List.of());
                payload.put("traversal",traversal);complete=traversal.complete();
            } else if(m.ruleId().equals("VAL.CLASH.001")) {
                double scale=Double.parseDouble(m.parameters().get("radiusScale").value());
                var result=StericClashAnalysis.findClashes(s.graph().structure(),new StericClashAnalysis.Options(scale));
                payload.put("clashes",result);payload.put("radiusScale",scale);
                complete=s.atoms().values().stream().allMatch(a->a.getElement()!=null);
            } else if(Set.of("SULF.SS.001","SULF.VICINAL.001").contains(m.ruleId())) {
                payload.putAll(sulfur(s,r));
            } else throw new IllegalArgumentException("unregistered collector");
            payload.put("complete",complete);
            return List.of(finding(s,SUPPORTED_PRESENT,Map.of("payload",text(payload)),"measurement payload collected; classification is separate"));
        }
    };}
    public static SystemGraphAnalyzer evaluator(RuleManifest m,RuleRequest r){
        if(m.implementationId().equals("athena.i03-n-sp3-s1"))return S1NitrogenRules.analyzer(m,r);
        if(m.implementationId().equals("athena.halogen-carbonyl"))return HalogenCarbonylRules.analyzer(m,r,true);
        if(m.implementationId().equals("athena.zinc-carbonyl"))return ZincCarbonylRules.analyzer(m,r,true);
        if(m.implementationId().equals("athena.selected-pharmacophore"))return SelectedPharmacophoreRules.analyzer(m,r,true);
        if(m.implementationId().equals("athena.source-fragment-parent"))return SourceFragmentParentRules.analyzer(m,r,true);
        if(m.implementationId().equals("athena.zn-source-features"))return ZnSourceFeatureRules.analyzer(m,r,true);
        if(m.implementationId().equals("athena.met-phe-survey"))return MetPheSurveyRules.analyzer(m,r,true);
        if(m.implementationId().equals("athena.implicit-h-proxy-s1"))return S1ImplicitHProxyRules.analyzer(m,r,true);
        if(m.implementationId().equals("athena.implicit-h-proxy"))return ImplicitHProxyRules.analyzer(m,r,true);
        if(m.implementationId().equals("athena.water-bridge"))return WaterBridgeRules.analyzer(m,r,true);
        if(m.implementationId().equals("athena.events"))return EventAnalysisRules.analyzer(m,r);
        if(m.implementationId().equals("athena.peptide-geometry"))return PeptideGeometryRules.analyzer(m,r,true);
        if(m.implementationId().equals("athena.hbond-candidate"))return HbondCandidateRules.analyzer(m,r,true);
        if(m.implementationId().equals("athena.cysteine-attribution"))return CysteineBackboneAttribution.analyzer(m,r);
        if(m.implementationId().equals("athena.ss-connectivity"))return SourceSulfurConnectivityRules.analyzer(m,r);
        if(m.implementationId().equals("athena.all-members-nonpolar"))return AllMembersNonpolarRules.analyzer(m,r);
        if(m.implementationId().equals("athena.charge-groups"))return ChargeGroupRules.analyzer(m,r,true);
        if(m.implementationId().equals("athena.aromatic-systems"))return AromaticSystemRules.analyzer(m,r,true);
        if(m.implementationId().equals("athena.geometry"))return ContinuousGeometryRules.analyzer(m,r,true);
        if(m.implementationId().equals("athena.group"))return FunctionalGroupRules.analyzer(m,r,null,true);
        return new Adapter(m,r,"evaluate") {
        public Set<SystemGraphCertificate.Capability> requires(){return Set.copyOf(m.requiredCapabilities());}
        public Set<String> evidenceTypes(){return Set.of("athena:rule-measurements","athena:rule-manifest","athena:rule-request");}
        public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> inputs,Map<String,String> config)throws Exception {
            bound(s);
            if(m.retired())return List.of(finding(s,NOT_EVALUATED,Map.of(),"rule version retired; measurements preserved"));
            var measurements=inputs.stream().filter(e->e.evidenceType().equals("athena:rule-measurements")).toList();
            if(measurements.size()!=1)throw new IllegalArgumentException("one exact measurement envelope required");
            var root=(m.implementationId().equals("athena.scientific")?SCIENTIFIC_JSON:JSON).readTree(measurements.getFirst().readPayload());
            if(root==null||!root.isObject()||!root.path("complete").isBoolean()
                    ||!root.path("collectorVersion").asText().equals("1")
                    ||!root.path("state").equals(JSON.valueToTree(s.binding())))throw new IllegalArgumentException("invalid measurement envelope structure/state");
            if(!root.path("collectionKey").asText().equals(collectionKey(m,r)))throw new IllegalArgumentException("measurement selection/state/collector mismatch");
            if(m.implementationId().equals("external.plip")||m.ruleId().equals("SULF.PI.001")||m.ruleId().equals("SULF.VICINAL.001"))
                return List.of(finding(s,UNSUPPORTED,Map.of("measurementDigest",measurements.getFirst().payloadSha256()),"classification implementation/prerequisites unqualified; all measurements retained"));
            if((!m.schema().equals("athena-rule/3")&&m.qualification()!=SystemGraphCertificate.Status.QUALIFIED))return List.of(finding(s,NOT_EVALUATED,Map.of(),"manifest is not qualified for execution"));
            if(m.implementationId().equals("athena.scientific")) {
                var measured=SCIENTIFIC_JSON.treeToValue(root.get("scientific"),AthenaScientificRules.Measurements.class);
                if(!measured.coverageVersion().equals(m.negativeCoverage().version())
                        ||!measured.negativeCoverage().keySet().equals(new HashSet<>(m.negativeCoverage().requirements())))
                    throw new IllegalArgumentException("negative coverage proof schema/version mismatch");
                var selected=new ArrayList<Integer>();
                for(int i=0;i<measured.raw().candidates().size();i++) {
                    var candidate=measured.raw().candidates().get(i);
                    if(!candidate.family().equals(AthenaScientificRules.family(m.ruleId())))throw new IllegalArgumentException("scientific family mismatch");
                    if(measured.candidateSupported().get(i)&&AthenaScientificRules.qualifies(candidate,m))selected.add(i);
                }
                boolean negative=measured.negativeCoverage().values().stream().allMatch(Boolean::booleanValue);
                if(root.path("complete").asBoolean()!=negative)throw new IllegalArgumentException("conflicting aggregate negative coverage");
                var status=!selected.isEmpty()?SUPPORTED_PRESENT:negative?ABSENT_FALSE:UNKNOWN_INCONCLUSIVE;
                return List.of(finding(s,status,Map.of("measurementDigest",measurements.getFirst().payloadSha256(),
                        "manifestDigest",RuleRegistry.digest(m),"negativeCoverage",text(measured.negativeCoverage()),
                        "coverageVersion",measured.coverageVersion(),"qualifyingCandidateIndices",text(selected)),
                        !selected.isEmpty()?"supported local candidate; complete absence coverage not implied":
                                negative?"no qualifying candidate in demonstrably complete supported partner scope":"insufficient negative coverage; absence not established"));
            }
            if(!root.path("complete").asBoolean())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,Map.of("measurementDigest",measurements.getFirst().payloadSha256()),"chemistry/geometry coverage incomplete; no absence claim"));
            boolean present=true;var details=new TreeMap<String,String>();details.put("measurementDigest",measurements.getFirst().payloadSha256());details.put("manifestDigest",RuleRegistry.digest(m));
            if(m.implementationId().equals("native.interaction")) {
                var result=JSON.treeToValue(root.get("interaction"),InteractionMeasurements.Result.class);var selected=new ArrayList<Integer>();
                for(var candidate:result.candidates())if(!candidate.family().equals(nativeFamily(m.ruleId())))throw new IllegalArgumentException("measurement family mismatch");
                if(!result.complete())throw new IllegalArgumentException("conflicting measurement coverage");
                for(int i=0;i<result.candidates().size();i++)if(InteractionMeasurements.classify(nativeFamily(m.ruleId()),result.candidates().get(i).values(),RuleRegistry.thresholds(m))!=null)selected.add(i);
                present=!selected.isEmpty();details.put("qualifyingCandidateIndices",text(selected));details.put("scope","raw detector classification before cross-class refinements");
            } else if(m.ruleId().equals("VAL.CLASH.001"))present=!root.path("clashes").isEmpty();
            else if(m.ruleId().equals("SULF.SS.001")) {
                String bond=root.path("bondState").asText();
                if(bond.equals("UNKNOWN"))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,details,"source connectivity cannot establish S-S bond state"));
                if(!Set.of("PRESENT","ABSENT").contains(bond))throw new IllegalArgumentException("invalid source bond state");
                present=bond.equals("PRESENT");
            }
            return List.of(finding(s,present?SUPPORTED_PRESENT:ABSENT_FALSE,details,"completed rule in explicit covered scope; no energy, inhibition or causality assertion"));
        }
    };}
    private static Map<String,Object> sulfur(SystemStateView s,RuleRequest r) {
        if(r.atoms().size()!=2)throw new IllegalArgumentException("two explicit sulfur atoms required");
        var a=r.atoms().get(0);var b=r.atoms().get(1);
        for(var ref:r.atoms())if(s.atoms().get(ref)==null||s.atoms().get(ref).getElement()!=totah.lab.gaia.chemistry.Element.S)throw new IllegalArgumentException("missing/non-sulfur subject");
        var out=new TreeMap<String,Object>();out.put("subjects",r.atoms());out.put("SG_SG_distanceAngstrom",s.atoms().get(a).getPosition().distance(s.atoms().get(b).getPosition()));
        boolean bond=s.graph().structure().bonds().stream().anyMatch(x->Set.of(x.atom1(),x.atom2()).equals(Set.of(a,b)));
        var provenance=s.graph().structure().getConnectivityMetadata().provenance();
        out.put("bondState",provenance==ConnectivityProvenance.EXPLICIT?(bond?"PRESENT":"ABSENT"):"UNKNOWN");out.put("sourceBondListed",bond);out.put("connectivity",s.graph().structure().getConnectivityMetadata());
        out.put("sequenceRelations",s.graph().sequenceEdges().stream().filter(e->Set.of(e.first(),e.second()).equals(Set.of(SystemStateView.residue(a),SystemStateView.residue(b)))).toList());
        var angles=new TreeMap<String,Object>();torsion(s,angles,"CB_SG_SG_CB",named(a,"CB"),a,b,named(b,"CB"));
        for(var ref:r.atoms()) {
            torsion(s,angles,ref+"/chi1",named(ref,"N"),named(ref,"CA"),named(ref,"CB"),ref);
            var chain=s.graph().structure().getChains().stream().filter(c->c.id().equals(ref.chainId())).findFirst().orElseThrow();
            var residues=chain.residues();int index=-1;for(int i=0;i<residues.size();i++)if(residues.get(i).getNumber()==ref.residueNumber()&&Objects.equals(residues.get(i).getInsertionCode(),ref.insertionCode()==' '?null:ref.insertionCode()))index=i;
            if(index>0){var prev=anchor(ref.chainId(),residues.get(index-1));torsion(s,angles,ref+"/phi_chain_order",named(prev,"C"),named(ref,"N"),named(ref,"CA"),named(ref,"C"));torsion(s,angles,ref+"/omega_chain_order",named(prev,"CA"),named(prev,"C"),named(ref,"N"),named(ref,"CA"));}
            else angles.put(ref+"/phi_omega","UNAVAILABLE: no preceding source residue");
            if(index>=0&&index+1<residues.size()){var next=anchor(ref.chainId(),residues.get(index+1));torsion(s,angles,ref+"/psi_chain_order",named(ref,"N"),named(ref,"CA"),named(ref,"C"),named(next,"N"));}
            else angles.put(ref+"/psi","UNAVAILABLE: no following source residue");
        }
        out.put("torsionsDegrees",angles);out.put("limitations",List.of("chain-order torsions do not assert peptide continuity","distance/geometry never establishes oxidation or covalent S-S bond"));return out;
    }
    private static AtomReference anchor(String c,Residue r){return new AtomReference(c,r.getNumber(),r.getInsertionCode()==null?' ':r.getInsertionCode(),"N");}
    private static AtomReference named(AtomReference a,String name){return new AtomReference(a.chainId(),a.residueNumber(),a.insertionCode(),name);}
    private static void torsion(SystemStateView s,Map<String,Object> out,String key,AtomReference... refs){
        if(Arrays.stream(refs).anyMatch(r->!s.atoms().containsKey(r))){out.put(key,Map.of("state","UNAVAILABLE","atoms",List.of(refs)));return;}
        try {out.put(key,Map.of("atoms",List.of(refs),"value",Math.toDegrees(Dihedral.measureRadians(s.atoms().get(refs[0]).getPosition(),s.atoms().get(refs[1]).getPosition(),s.atoms().get(refs[2]).getPosition(),s.atoms().get(refs[3]).getPosition()))));}
        catch(IllegalArgumentException e){out.put(key,Map.of("state","UNDEFINED","reason",e.getMessage(),"atoms",List.of(refs)));}
    }
    private static String text(Object x){return new String(SystemStateView.bytes(x),StandardCharsets.UTF_8);}
}
