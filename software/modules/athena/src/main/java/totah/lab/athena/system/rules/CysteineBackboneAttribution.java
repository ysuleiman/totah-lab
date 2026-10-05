package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import totah.lab.athena.system.*;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.gaia.structure.*;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Attribution of verified source roles and explicit linkage; no residue-name or redox classifier. */
final class CysteineBackboneAttribution {
    private static final ObjectMapper JSON=new ObjectMapper();
    private CysteineBackboneAttribution() { }
    static void validate(RuleManifest m) {
        if(!m.schema().equals("athena-rule/2")||!m.ruleId().equals("ATHENA.SULF.CYSTEINE_BACKBONE_ATTRIBUTION")
                ||!m.profile().equals("ATHENA_CYSTEINE_ATTRIBUTION_V1")||!m.implementationVersion().equals("1")
                ||m.qualification()!=SystemGraphCertificate.Status.NOT_EVALUATED
                ||!m.parameters().keySet().equals(Set.of("sourceManifest","geometryManifest"))||!m.requiredCapabilities().isEmpty())
            throw new IllegalArgumentException("cysteine attribution contract");
        try {
            var group=source(m,"sourceManifest");var geometry=source(m,"geometryManifest");
            if(!group.ruleId().equals("ATHENA.GROUP.CYSTEINE_BACKBONE")||!group.implementationId().equals("athena.group")
                    ||!group.implementationVersion().equals("3")||!geometry.implementationId().equals("athena.geometry")||!geometry.implementationVersion().equals("3"))
                throw new IllegalArgumentException("pinned backbone/V3 geometry required");
        }catch(Exception e){throw new IllegalArgumentException("invalid source pins",e);}
    }
    private static RuleManifest source(RuleManifest m,String key)throws Exception{return RuleRegistry.decode(m.parameters().get(key).value().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static SystemGraphAnalyzer analyzer(RuleManifest m,RuleRequest r) {
        validate(m);
        return new SystemGraphAnalyzer() {
            public ScientificReference method(){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.cysteine-attribution",m.key()+"/evaluate",RuleRegistry.digest(m));}
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return Set.of("athena:group-identities","athena:system-state");}
            public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> inputs,Map<String,String> config)throws Exception {
                if(!s.binding().equals(r.state())||!m.key().equals(r.manifestKey())||!RuleRegistry.digest(m).equals(r.manifestSha256())
                        ||r.atoms().size()!=2||r.atoms().get(0).equals(r.atoms().get(1))||!r.first().isEmpty()||!r.second().isEmpty())throw new IllegalArgumentException("explicit ordered sulfur pair required");
                for(var atom:r.atoms())if(!s.atoms().containsKey(atom)||s.atoms().get(atom).getElement()!=totah.lab.gaia.chemistry.Element.S)throw new IllegalArgumentException("source sulfur required");
                var selected=inputs.stream().filter(e->e.evidenceType().equals("athena:group-identities")).toList();
                if(selected.size()!=1)throw new IllegalArgumentException("one explicitly selected component identity report; no conflict resolution");
                var e=selected.getFirst();var bytes=e.readPayload();if(!EvidenceExchange.sha256(bytes).equals(e.payloadSha256()))throw new IllegalArgumentException("identity hash mismatch");
                var group=source(m,"sourceManifest");
                var request=new RuleRequest(s.binding(),group.key(),RuleRegistry.digest(group),List.of(),List.of(),List.of(),r.radiusAngstrom(),r.maximumHops(),r.maximumNodes(),r.maximumCandidates());
                var verified=RuleAnalyzers.evaluator(group,request).analyze(s,List.of(e),Map.of()).getFirst();
                var report=JSON.readTree(verified.measurements().get("payload"));
                var component=s.components().stream().filter(c->node(c.identity()).equals(report.path("componentReference"))).findFirst().orElseThrow();
                var provenance=new TreeMap<String,String>();provenance.put("groupReportReference",text(e.reference()));provenance.put("groupReportSha256",e.payloadSha256());
                provenance.put("stateBinding",text(s.binding()));provenance.put("sourceCorrespondenceAlternatives",text(component.correspondenceAlternatives()));
                var labels=new TreeMap<String,Object>();
                for(var chain:s.graph().structure().getChains())for(var residue:chain.residues()) {
                    var refs=s.atoms().keySet().stream().filter(a->a.chainId().equals(chain.id())&&a.residueNumber()==residue.getNumber()
                            &&Objects.equals(a.insertionCode()==' '?null:a.insertionCode(),residue.getInsertionCode())).toList();
                    labels.put(chain.id()+"/"+residue.getNumber()+"/"+residue.getInsertionCode(),Map.of("sourceResidue",residue.getName(),"atoms",refs));
                }
                var out=new ArrayList<Finding>();var identity=new TreeMap<>(provenance);identity.put("sourceLabels",text(labels));identity.put("groupReport",report.toString());
                out.add(finding("backbones",s,EvidenceInterpretation.Status.valueOf(report.path("assessment").asText()),identity,"source labels and verified graph identities retained independently"));
                var pairs=new TreeMap<String,List<Map<String,AtomReference>>>();
                if(report.path("assessment").asText().equals(SUPPORTED_PRESENT.name()))for(var correspondence:component.correspondenceAlternatives()) {
                    var alternatives=new ArrayList<Map<String,AtomReference>>();
                    for(var occurrence:report.path("occurrences"))for(var roles:occurrence.path("roleCorrespondenceAlternatives")) {
                        var binding=new TreeMap<String,AtomReference>();roles.fields().forEachRemaining(entry->{
                            if(entry.getValue().size()!=1)throw new IllegalArgumentException("one atom per backbone role");
                            var ref=correspondence.get(entry.getValue().get(0).asText());if(ref==null)throw new IllegalArgumentException("unmapped backbone role");binding.put(entry.getKey(),ref);
                        });alternatives.add(Collections.unmodifiableMap(binding));
                    }
                    for(var first:alternatives)for(var second:alternatives)if(first.get("SG").equals(r.atoms().get(0))&&second.get("SG").equals(r.atoms().get(1))) {
                        var pair=List.of(first,second);pairs.putIfAbsent(text(pair),pair);
                    }
                }
                if(pairs.isEmpty()||pairs.size()>r.maximumCandidates()) {
                    out.add(finding("pair-unavailable",s,UNKNOWN_INCONCLUSIVE,provenance,"complete requested backbone pair/correspondence unavailable or over budget"));return List.copyOf(out);
                }
                int serial=0;
                for(var pair:pairs.values()) {
                    var first=pair.get(0);var second=pair.get(1);var map=component.correspondenceAlternatives().getFirst();
                    var reverse=new HashMap<AtomReference,String>();map.forEach((id,atom)->reverse.put(atom,id));
                    var c=reverse.get(first.get("C"));var n=reverse.get(second.get("N"));
                    var listed=component.chemistry().bonds().stream().filter(b->Set.of(b.firstAtomId(),b.secondAtomId()).equals(Set.of(c,n))).toList();
                    boolean peptide=listed.stream().anyMatch(b->b.order()==MolecularGraph.BondOrder.SINGLE);
                    var data=new TreeMap<>(provenance);data.put("orderedRoles",text(pair));data.put("sourcePeptideBonds",text(listed));
                    data.put("sourceSulfurBonds",text(component.chemistry().bonds().stream().filter(b->Set.of(b.firstAtomId(),b.secondAtomId()).equals(Set.of(reverse.get(first.get("SG")),reverse.get(second.get("SG"))))).toList()));
                    out.add(finding("peptide-"+serial,s,peptide?SUPPORTED_PRESENT:ABSENT_FALSE,data,"listed C(first)-N(second) single edge in verified complete component; no peptide conformation or S-S chemistry inferred"));
                    var plan=JSON.createObjectNode();plan.put("schema","athena-continuous-geometry-plan/1");plan.set("stateBinding",node(s.binding()));plan.put("coordinateUnit","ANGSTROM");
                    plan.set("coordinateSourceReferences",node(s.sources()));plan.putArray("groups");plan.putArray("operations");plan.putNull("radiusAssignmentReference");
                    plan.set("sourceReferences",node(List.of(e.reference())));plan.putArray("limitations").add("Raw ordered tuples only; no disulfide/redox/chirality/conformation classification");
                    add(plan,"SG1_SG2","DISTANCE",first.get("SG"),second.get("SG"));
                    add(plan,"N1_CA1_CB1_SG1","DIHEDRAL",first.get("N"),first.get("CA"),first.get("CB"),first.get("SG"));
                    add(plan,"N2_CA2_CB2_SG2","DIHEDRAL",second.get("N"),second.get("CA"),second.get("CB"),second.get("SG"));
                    add(plan,"CA1_CB1_SG1_SG2","DIHEDRAL",first.get("CA"),first.get("CB"),first.get("SG"),second.get("SG"));
                    add(plan,"CA2_CB2_SG2_SG1","DIHEDRAL",second.get("CA"),second.get("CB"),second.get("SG"),first.get("SG"));
                    add(plan,"CB1_SG1_SG2_CB2","DIHEDRAL",first.get("CB"),first.get("SG"),second.get("SG"),second.get("CB"));
                    if(peptide) {
                        add(plan,"CA1_C1_N2_CA2","DIHEDRAL",first.get("CA"),first.get("C"),second.get("N"),second.get("CA"));
                        add(plan,"C1_N2_CA2_C2","DIHEDRAL",first.get("C"),second.get("N"),second.get("CA"),second.get("C"));
                        add(plan,"N1_CA1_C1_N2","DIHEDRAL",first.get("N"),first.get("CA"),first.get("C"),second.get("N"));
                        var g=((ArrayNode)plan.get("groups")).addObject();g.put("id","eight-point-scope");
                        g.set("atoms",node(new TreeSet<>(List.of(first.get("CA"),first.get("CB"),first.get("SG"),second.get("SG"),second.get("CB"),second.get("CA"),second.get("N"),first.get("C")))));g.set("sourceReferences",node(List.of(e.reference())));
                        var op=((ArrayNode)plan.get("operations")).addObject();op.put("id","eight-point-plane");op.put("kind","PLANE");op.put("groupId","eight-point-scope");
                    }
                    var geometry=source(m,"geometryManifest");var atoms=new TreeSet<AtomReference>();
                    for(var op:plan.get("operations"))for(var a:op.path("atoms"))atoms.add(JSON.treeToValue(a,AtomReference.class));
                    for(var g:plan.get("groups"))for(var a:g.path("atoms"))atoms.add(JSON.treeToValue(a,AtomReference.class));
                    var gr=new RuleRequest(s.binding(),geometry.key(),RuleRegistry.digest(geometry),List.copyOf(atoms),List.of(),List.of(),r.radiusAngstrom(),0,r.maximumNodes(),r.maximumCandidates());
                    var ge=planEnvelope(s,plan,method(),e);
                    var measured=RuleAnalyzers.collector(geometry,gr).analyze(s,List.of(ge),Map.of()).getFirst();
                    data.put("measurementPlan",plan.toString());data.put("measurementPlanEnvelope",text(ge));data.put("geometryReport",measured.measurements().get("payload"));
                    data.put("unavailable",text(List.of("external preceding/following backbone roles not supplied; no chain-order substitution","absolute chirality/conformation class not evaluated")));
                    out.add(finding("geometry-"+serial++,s,UNKNOWN_INCONCLUSIVE,data,"raw geometry independently qualified by its per-quantity statuses; this Finding grants no system capability"));
                }
                return List.copyOf(out);
            }
            private Finding finding(String id,SystemStateView s,EvidenceInterpretation.Status assessment,Map<String,String> measurements,String reason){
                var values=new TreeMap<>(measurements);values.put("assessment",assessment.name());
                return new Finding(id,List.of(s.subject()),NOT_EVALUATED,values,List.of(reason),m.limitations());
            }
        };
    }
    private static EvidenceEnvelope planEnvelope(SystemStateView s,JsonNode plan,ScientificReference method,EvidenceEnvelope source) {
        byte[] bytes=SystemStateView.bytes(plan);String hash=EvidenceExchange.sha256(bytes);String ns="athena.cysteine-attribution";
        return new EvidenceEnvelope(new ScientificReference(ScientificReference.Kind.EVIDENCE_ENVELOPE,ns,hash,"1"),"athena:continuous-geometry-plan","application/json","1",
                Optional.of(Base64.getEncoder().encodeToString(bytes)),Optional.empty(),hash,
                new Observation.Provenance(new ScientificReference(ScientificReference.Kind.SOURCE,ns,hash,"1"),new ScientificReference(ScientificReference.Kind.ARTIFACT,"sha256",hash,"1"),
                        new ScientificReference(ScientificReference.Kind.RECEIPT,ns,hash,"1"),method,"inline derived explicit geometry plan",List.of(source.reference())),
                method,s.identity(),List.of(s.subject()),List.of(),List.of("geometry plan; no chemistry inferred"),source.recordedAt());
    }
    private static void add(ObjectNode plan,String id,String kind,AtomReference... refs){var op=((ArrayNode)plan.get("operations")).addObject();op.put("id",id);op.put("kind",kind);op.set("atoms",node(List.of(refs)));}
    private static JsonNode node(Object v){return JSON.valueToTree(v);}
    private static String text(Object v){return new String(SystemStateView.bytes(v),java.nio.charset.StandardCharsets.UTF_8);}
}
