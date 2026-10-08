package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.system.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Approved Option B: exact source glycine H/carbonyl tuple, without H inference. */
final class GlycineHCarbonylRules {
    static final String ID="ATHENA.I04.EXPLICIT_SOURCE_H_GLYCINE_BACKBONE_CARBONYL_CANDIDATE";
    private static final String ACCEPTOR="ATHENA.PERCEPTION.ACCEPTOR.CARBONYL_O";
    private GlycineHCarbonylRules() { }
    static void validate(RuleManifest m) {
        require(Set.of("athena-rule/2","athena-rule/3").contains(m.schema())&&m.ruleId().equals(ID)&&m.version().equals("1.0.0")&&m.profile().equals("ATHENA_I04_GLYCINE_EXPLICIT_H_V1")&&m.implementationVersion().equals("1")&&m.qualification()==SystemGraphCertificate.Status.NOT_EVALUATED&&m.family()==RuleManifest.Family.INTERACTION&&m.requiredCapabilities().isEmpty(),"I04 manifest contract");
        try(var in=GlycineHCarbonylRules.class.getResourceAsStream("glycine-h-carbonyl-v1/"+ID+".rule.json")) {
            require(in!=null,"I04 definition missing");var definition=JSON.readTree(in);
            require(JSON.valueToTree(m.parameters()).equals(definition.get("parameters"))&&JSON.valueToTree(m.negativeCoverage()).equals(definition.get("negativeCoverage"))&&JSON.valueToTree(m.scientificSources()).equals(definition.get("scientificSources")),"I04 definition changed");
        }catch(java.io.IOException e){throw new IllegalArgumentException("I04 definition unreadable",e);}
    }
    static ScientificReference method(RuleManifest m,boolean evaluate){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.glycine-h-carbonyl",m.key()+(evaluate?"/evaluate":"/collect"),RuleRegistry.digest(m));}
    static SystemGraphAnalyzer analyzer(RuleManifest m,RuleRequest request,boolean evaluate) {
        validate(m);return new SystemGraphAnalyzer(){
            public ScientificReference method(){return GlycineHCarbonylRules.method(m,evaluate);}
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return Set.of("athena:group-identities","athena:group-source-coverage","athena:continuous-geometry-plan","athena:rule-measurements","athena:event-source","athena:source-artifact","athena:rule-manifest","athena:rule-policy-context","athena:rule-qualification-receipt","athena:rule-research-eligibility","athena:rule-implementation-qualification","athena:system-binding","athena:system-certificate","athena:rule-request","athena:system-state");}
            public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> supplied,Map<String,String> configuration)throws Exception {
                var tuple=request.atoms();require(s.binding().equals(request.state())&&m.key().equals(request.manifestKey())&&RuleRegistry.digest(m).equals(request.manifestSha256())&&tuple.size()==4&&new HashSet<>(tuple).size()==4&&request.first().isEmpty()&&request.second().isEmpty(),"I04 exact ordered tuple/state required");
                var inputs=index(supplied);var sources=new HbondCandidateSources(s,m,request,List.copyOf(inputs.values()));
                var plans=inputs.values().stream().filter(e->e.evidenceType().equals("athena:continuous-geometry-plan")).toList();require(plans.size()==1,"one selected I04 geometry plan required");var plan=read(plans.getFirst());verifyPlan(plan,tuple);
                var geometry=RuleRegistry.decode(SystemStateView.bytes(HbondCandidateRules.parameter(m,"geometry")));
                var gr=new RuleRequest(s.binding(),geometry.key(),RuleRegistry.digest(geometry),tuple.subList(0,3).stream().sorted().toList(),List.of(),List.of(),request.radiusAngstrom(),0,request.maximumNodes(),request.maximumCandidates());
                var raw=RuleAnalyzers.collector(geometry,gr).analyze(s,plans,Map.of()).getFirst().measurements().get("payload");
                var values=new TreeMap<String,String>();values.put("payload",raw);values.put("proposition","SELECTED_EXPLICIT_SOURCE_H_GLYCINE_CARBONYL_CANDIDATE");values.put("sourcePins",canonical(sources.pins()));values.put("tuple",canonical(tuple));values.put("definitionSha256",RuleRegistry.digest(m));
                if(!evaluate)return List.of(finding(s,SUPPORTED_PRESENT,values,"Raw selected I04 geometry; scientific eligibility not evaluated"));
                var measurements=inputs.values().stream().filter(e->e.evidenceType().equals("athena:rule-measurements")).toList();require(measurements.size()==1&&measurements.getFirst().method().equals(GlycineHCarbonylRules.method(m,false))&&read(measurements.getFirst()).equals(JSON.readTree(raw)),"I04 measurement replay mismatch");
                var at=S1Qualification.current(m,s,request,inputs);if(at.isEmpty())return List.of(finding(s,NOT_EVALUATED,values,"Independent current I04 qualification absent"));
                var donor=HalogenCarbonylRules.component(s,tuple.get(0),tuple.get(1));var acceptor=HalogenCarbonylRules.component(s,tuple.get(2),tuple.get(3));
                if(donor==null||acceptor==null)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Incomplete or ambiguous selected source correspondence"));
                if(donor.identity().equals(acceptor.identity()))return List.of(finding(s,UNSUPPORTED,values,"Distinct complete source components required"));
                var components=List.of(donor,acceptor);var coverages=new HashMap<SystemStateView.Component,JsonNode>();
                for(var c:components) {
                    if(c.chemistry().atoms().size()>request.maximumNodes())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Source component exceeds request budget"));
                    try{c.chemistry().validateTopology(true);}catch(IllegalArgumentException ex){return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Connected complete source component required"));}
                    if(c.chemistry().atoms().stream().anyMatch(a->!Set.of("C","N","O","H").contains(a.element())))return List.of(finding(s,UNSUPPORTED,values,"Only C/N/O/H source components admitted"));
                    var mapping=c.correspondenceAlternatives().getFirst();
                    for(var other:s.components())if(!other.identity().equals(c.identity()))for(var alternative:other.correspondenceAlternatives())if(alternative.values().stream().anyMatch(mapping.values()::contains))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Overlapping source component correspondence"));
                    for(var e:inputs.values())if(e.evidenceType().equals("athena:event-source")){
                        var record=new EvidenceExchange().decodeRecord(e.readPayload());
                        if(record instanceof EvidenceInterpretation i&&Set.of("ATHENA.I03.SP3_SOURCE_SCOPE/1","ATHENA.I03.SP3_SOURCE_SCOPE/2").contains(i.measurements().getOrDefault("proposition",""))&&canonical(c.identity()).equals(i.measurements().get("componentReference")))
                            for(var dependency:i.inputs())if(!inputs.containsKey(canonical(dependency.reference())))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Missing explicitly selected original source/preparation provenance"));
                    }
                    var scope=S1SourceScope.check(s,c,inputs);
                    for(var witness:scope.witnesses())if(!S1Qualification.scope(witness,s,inputs,at.orElseThrow()))return List.of(finding(s,NOT_EVALUATED,values,"Independent source-scope authority absent"));
                    if(scope.conflicting()||scope.witnesses().isEmpty())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Source scope missing or conflicting"));
                    if(scope.knownOutside())return List.of(finding(s,UNSUPPORTED,values,"Known nonordinary source connection"));
                    if(!scope.complete())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Source connection/electronic coverage unresolved"));
                    JsonNode coverage=null;for(var e:inputs.values())if(e.evidenceType().equals("athena:group-source-coverage")){var n=read(e);if(n.path("componentReference").equals(JSON.valueToTree(c.identity()))){if(coverage!=null&&!coverage.equals(n))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Conflicting source facts"));coverage=n;}}
                    if(coverage==null)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Missing source facts"));
                    ZincCarbonylRules.verifyCoverage(s,c,coverage);var status=HalogenCarbonylRules.sourceFacts(s,c,coverage,inputs.values());
                    if(status!=SUPPORTED_PRESENT)return List.of(finding(s,status,values,"Missing, conflicting or excluded source facts"));
                    if(!coverage.path("completeGraph").asText().equals("SUPPORTED_PRESENT"))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Complete original source graph required"));
                    coverages.put(c,coverage);
                }
                values.put("geometryEvidenceKind","EXPLICIT_SOURCE_H");
                values.put("sourceCoordinateAttribution",canonical(s.sources()));
                values.put("hydrogenInterpretation","Supplied source coordinates; preparation provenance retained; no experimental-observation inference");
                if(s.sources().isEmpty())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Source coordinate/preparation attribution required"));
                var dr=sourceRoles(s,donor,tuple.get(0),"GLY",List.of("N","CA","C","O"));
                var ar=sourceRoles(s,acceptor,tuple.get(2),"GLY",List.of("N","CA","C","O"));
                if(dr==null||ar==null){
                    boolean excluded=knownOtherResidue(s,tuple.get(0))||knownOtherResidue(s,tuple.get(2));
                    return List.of(finding(s,excluded?UNSUPPORTED:UNKNOWN_INCONCLUSIVE,values,"Exact unique GLY source roles required"));
                }
                var dm=donor.correspondenceAlternatives().getFirst();var am=acceptor.correspondenceAlternatives().getFirst();
                if(!dm.get(dr.get("CA")).equals(tuple.get(0))||!am.get(ar.get("O")).equals(tuple.get(2))||!am.get(ar.get("C")).equals(tuple.get(3)))return List.of(finding(s,UNSUPPORTED,values,"Tuple outside exact glycine CA/H/O/C roles"));
                var dc=new Context(donor,coverages.get(donor));var ac=new Context(acceptor,coverages.get(acceptor));
                var hs=dc.hydrogens(dr.get("CA"));
                if(hs.size()!=2||dc.implicit(dr.get("CA"))!=0)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Two explicit donor CA H occurrences and no implicit donor H required"));
                if(!hs.stream().map(dm::get).toList().contains(tuple.get(1)))return List.of(finding(s,UNSUPPORTED,values,"Selected H is not covalently attached to donor CA"));
                for(var h:hs){var atom=dc.atom(h);if(!dc.hydrogen(h,dr.get("CA"))||atom.isotope()!=null&&atom.isotope()!=1)return List.of(finding(s,UNSUPPORTED,values,"Excluded source hydrogen state/connectivity/isotope"));}
                if(!dc.backbone(dr)||!ac.backbone(ar))return List.of(finding(s,UNSUPPORTED,values,"Outside exact finite neutral N-acylated/C-amidated glycine context"));
                var report=sources.report(acceptor,ACCEPTOR);
                if(report==null||!report.complete())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Unchanged carbonyl role coverage incomplete"));
                if(!HalogenCarbonylRules.roles(report,List.of("carbonylOxygen","carbonylCarbon"),tuple.subList(2,4)))return List.of(finding(s,UNSUPPORTED,values,"Selected acceptor is not unchanged carbonyl role"));
                values.put("sourceRoles",canonical(Map.of("donor",dr,"acceptor",ar)));values.put("explicitDonorHydrogens",canonical(hs.stream().map(dm::get).sorted().toList()));
                var ops=JSON.readTree(raw).path("operations");Double distance=HalogenCarbonylRules.value(ops,0,"distanceAngstrom"),angle=HalogenCarbonylRules.value(ops,1,"angleDegrees");
                if(distance==null||distance<=0||angle==null)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Finite positive source H-O distance and defined CA-H-O angle required"));
                values.put("distanceAngstrom",distance.toString());values.put("angleDegrees",angle.toString());
                return List.of(finding(s,predicate(distance,angle)?SUPPORTED_PRESENT:ABSENT_FALSE,values,"Exact selected Option B geometry only; no energy or whole-system absence"));
            }
            private Finding finding(SystemStateView s,EvidenceInterpretation.Status status,Map<String,String> values,String reason){return new Finding(evaluate?"evaluate":"collect",List.of(s.subject()),status,values,List.of(reason),m.limitations());}
        };
    }
    static boolean predicate(double d,double z){return Double.isFinite(d)&&Double.isFinite(z)&&d>0&&d<3.5&&(z>120||(d<3.0&&z>90));}
    private static void verifyPlan(JsonNode plan,List<AtomReference> t) {
        var ops=plan.path("operations");require(plan.path("groups").isArray()&&plan.path("groups").isEmpty()&&plan.path("radiusAssignmentReference").isNull()&&ops.size()==2,"Two exact I04 geometry operations required");
        require(ops.get(0).path("kind").asText().equals("DISTANCE")&&ops.get(0).path("atoms").equals(JSON.valueToTree(t.subList(1,3)))&&ops.get(1).path("kind").asText().equals("ANGLE")&&ops.get(1).path("atoms").equals(JSON.valueToTree(t.subList(0,3))),"Exact H-O and CA-H-O plan required");
    }
    private static boolean knownOtherResidue(SystemStateView s,AtomReference selected){
        for(var chain:s.graph().structure().getChains())for(var r:chain.residues())if(chain.id().equals(selected.chainId())&&r.getNumber()==selected.residueNumber()&&Objects.equals(r.getInsertionCode(),selected.insertionCode()==' '?null:selected.insertionCode()))return r.getName()!=null&&!r.getName().isBlank()&&!r.getName().equals("GLY");return false;
    }
    private static boolean sameResidue(AtomReference a,AtomReference b){return a.chainId().equals(b.chainId())&&a.residueNumber()==b.residueNumber()&&a.insertionCode()==b.insertionCode();}
    private static Map<String,String> sourceRoles(SystemStateView s,SystemStateView.Component c,AtomReference selected,String label,List<String> names) {
        var roles=new TreeMap<String,String>();int count=0;
        for(var chain:s.graph().structure().getChains())for(var residue:chain.residues())if(chain.id().equals(selected.chainId())&&residue.getNumber()==selected.residueNumber()&&Objects.equals(residue.getInsertionCode(),selected.insertionCode()==' '?null:selected.insertionCode())) {
            count++;if(!label.equals(residue.getName()))return null;
            for(var name:names){var atoms=residue.getAtoms().stream().filter(a->a.getName().equals(name)).toList();if(atoms.size()!=1)return null;
                var ids=c.correspondenceAlternatives().getFirst().entrySet().stream().filter(e->sameResidue(e.getValue(),selected)&&e.getValue().atomName().equals(name)).map(Map.Entry::getKey).toList();if(ids.size()!=1)return null;roles.put(name,ids.getFirst());}
        }return count==1?roles:null;
    }
    /** Exact finite source-graph admission, without valence, peptide-sequence or geometry inference. */
    private record Context(SystemStateView.Component component,JsonNode coverage) {
        totah.lab.athena.design.backend.MolecularGraph.Atom atom(String id){return component.chemistry().atom(id).orElseThrow();}
        Map<String,String> neighbors(String id){var n=new TreeMap<String,String>();for(var b:component.chemistry().bonds()){String other=b.firstAtomId().equals(id)?b.secondAtomId():b.secondAtomId().equals(id)?b.firstAtomId():null;if(other!=null)n.put(other,b.aromatic()&&!b.order().name().equals("AROMATIC")?"NONCANONICAL_AROMATIC_SOURCE":b.order().name());}return n;}
        int implicit(String id){return coverage.path("atomState").path(id).path("implicitHydrogenCount").asInt(-1);}
        List<String> hydrogens(String id){return neighbors(id).keySet().stream().filter(n->atom(n).element().equals("H")).toList();}
        boolean hydrogen(String id,String parent){var a=atom(id);return a.element().equals("H")&&a.formalCharge()==0&&!a.aromatic()&&implicit(id)==0&&a.explicitHydrogens()==0&&neighbors(id).equals(Map.of(parent,"SINGLE"));}
        Map<String,String> heavy(String id){var n=new TreeMap<>(neighbors(id));n.keySet().removeIf(x->atom(x).element().equals("H"));return n;}
        boolean fact(String id,String element,boolean aromatic,int h){var a=atom(id);var hs=hydrogens(id);return a.element().equals(element)&&a.formalCharge()==0&&a.aromatic()==aromatic&&implicit(id)>=0&&implicit(id)+hs.size()==h&&(a.explicitHydrogens()==0||a.explicitHydrogens()==implicit(id))&&hs.stream().allMatch(x->hydrogen(x,id));}
        boolean pattern(String id,String e,boolean aromatic,int h,Map<String,String> expected){return fact(id,e,aromatic,h)&&heavy(id).equals(expected);}
        boolean single(String id,String e,int h,String... n){var expected=new TreeMap<String,String>();for(var x:n)expected.put(x,"SINGLE");return pattern(id,e,false,h,expected);}
        boolean backbone(Map<String,String> r){String n=r.get("N"),ca=r.get("CA"),c=r.get("C"),o=r.get("O");
            if(!single(ca,"C",2,n,c)||!pattern(o,"O",false,0,Map.of(c,"DOUBLE"))||!fact(n,"N",false,1)||!fact(c,"C",false,0))return false;
            var nn=heavy(n);var cn=heavy(c);if(nn.size()!=2||!"SINGLE".equals(nn.get(ca))||cn.size()!=3||!"SINGLE".equals(cn.get(ca))||!"DOUBLE".equals(cn.get(o)))return false;
            String previous=nn.keySet().stream().filter(x->!x.equals(ca)).findFirst().orElseThrow();String following=cn.keySet().stream().filter(x->!x.equals(ca)&&!x.equals(o)).findFirst().orElseThrow();
            var map=component.correspondenceAlternatives().getFirst();if(sameResidue(map.get(previous),map.get(n))||sameResidue(map.get(following),map.get(c))||!"SINGLE".equals(nn.get(previous))||!"SINGLE".equals(cn.get(following))||!fact(previous,"C",false,0))return false;
            var pn=heavy(previous);if(pn.size()!=3||!"SINGLE".equals(pn.get(n)))return false;var oxygen=pn.entrySet().stream().filter(e->e.getValue().equals("DOUBLE")&&atom(e.getKey()).element().equals("O")).map(Map.Entry::getKey).toList();if(oxygen.size()!=1)return false;String po=oxygen.getFirst();
            if(!sameResidue(map.get(previous),map.get(po))||!pattern(po,"O",false,0,Map.of(previous,"DOUBLE")))return false;
            String pc=pn.keySet().stream().filter(x->!x.equals(n)&&!x.equals(po)).findFirst().orElseThrow();var a=atom(pc);if(!a.element().equals("C")||a.aromatic()||a.formalCharge()!=0||!"SINGLE".equals(pn.get(pc)))return false;
            var fn=heavy(following);if(fn.size()<1||fn.size()>3||!fact(following,"N",false,3-fn.size()))return false;for(var e:fn.entrySet())if(!e.getValue().equals("SINGLE")||!atom(e.getKey()).element().equals("C"))return false;return true;
        }
    }
}
