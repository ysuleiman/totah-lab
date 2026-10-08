package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.system.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Bounded carbon-bound Cl / attributed protein Phe geometric candidate only. */
final class ClPheCandidateRules {
    static final String ID="ATHENA.I11.CARBON_BOUND_CL_PHE_GEOMETRIC_CANDIDATE";
    private static final String RING="ATHENA.G03.SOURCE.CARBON_RING";
    private ClPheCandidateRules() { }
    static void validate(RuleManifest m) {
        require(Set.of("athena-rule/2","athena-rule/3").contains(m.schema())&&m.ruleId().equals(ID)&&m.version().equals("1.0.0")&&m.profile().equals("ATHENA_I11_CL_PHE_V1")&&m.implementationVersion().equals("1")&&m.qualification()==SystemGraphCertificate.Status.NOT_EVALUATED&&m.family()==RuleManifest.Family.INTERACTION&&m.requiredCapabilities().isEmpty(),"I11 manifest contract");
        try(var in=ClPheCandidateRules.class.getResourceAsStream("cl-phe-candidate-v1/"+ID+".rule.json")) {
            require(in!=null,"I11 definition missing");var definition=JSON.readTree(in);
            require(JSON.valueToTree(m.parameters()).equals(definition.get("parameters"))&&JSON.valueToTree(m.negativeCoverage()).equals(definition.get("negativeCoverage"))&&JSON.valueToTree(m.scientificSources()).equals(definition.get("scientificSources")),"I11 definition changed");
        }catch(java.io.IOException e){throw new IllegalArgumentException("I11 definition unreadable",e);}
    }
    static ScientificReference method(RuleManifest m,boolean evaluate){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.cl-phe-candidate",m.key()+(evaluate?"/evaluate":"/collect"),RuleRegistry.digest(m));}
    static SystemGraphAnalyzer analyzer(RuleManifest m,RuleRequest request,boolean evaluate) {
        validate(m);return new SystemGraphAnalyzer(){
            public ScientificReference method(){return ClPheCandidateRules.method(m,evaluate);}
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return Set.of("athena:group-identities","athena:group-source-coverage","athena:continuous-geometry-plan","athena:rule-measurements","athena:event-source","athena:source-artifact","athena:rule-manifest","athena:rule-policy-context","athena:rule-qualification-receipt","athena:rule-research-eligibility","athena:rule-implementation-qualification","athena:system-binding","athena:system-certificate","athena:rule-request","athena:system-state");}
            public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> supplied,Map<String,String> configuration)throws Exception {
                var tuple=request.atoms();require(s.binding().equals(request.state())&&m.key().equals(request.manifestKey())&&RuleRegistry.digest(m).equals(request.manifestSha256())&&tuple.size()==8&&new HashSet<>(tuple).size()==8&&request.first().isEmpty()&&request.second().isEmpty(),"I11 exact ordered tuple/state required");
                var inputs=index(supplied);var sources=new HbondCandidateSources(s,m,request,List.copyOf(inputs.values()));
                var plans=inputs.values().stream().filter(e->e.evidenceType().equals("athena:continuous-geometry-plan")).toList();require(plans.size()==1,"one selected I11 geometry plan required");var plan=read(plans.getFirst());verifyPlan(plan,tuple);
                var geometry=RuleRegistry.decode(SystemStateView.bytes(HbondCandidateRules.parameter(m,"geometry")));
                var gr=new RuleRequest(s.binding(),geometry.key(),RuleRegistry.digest(geometry),tuple.subList(1,8).stream().sorted().toList(),List.of(),List.of(),request.radiusAngstrom(),0,request.maximumNodes(),request.maximumCandidates());
                var raw=RuleAnalyzers.collector(geometry,gr).analyze(s,plans,Map.of()).getFirst().measurements().get("payload");
                var values=new TreeMap<String,String>();values.put("payload",raw);values.put("proposition","SELECTED_CARBON_BOUND_CL_PHE_GEOMETRIC_CANDIDATE");values.put("sourcePins",canonical(sources.pins()));values.put("tuple",canonical(tuple));values.put("definitionSha256",RuleRegistry.digest(m));
                if(!evaluate)return List.of(finding(s,SUPPORTED_PRESENT,values,"Raw selected I11 geometry; scientific eligibility not evaluated"));
                var measurements=inputs.values().stream().filter(e->e.evidenceType().equals("athena:rule-measurements")).toList();require(measurements.size()==1&&measurements.getFirst().method().equals(ClPheCandidateRules.method(m,false))&&read(measurements.getFirst()).equals(JSON.readTree(raw)),"I11 measurement replay mismatch");
                var at=S1Qualification.current(m,s,request,inputs);if(at.isEmpty())return List.of(finding(s,NOT_EVALUATED,values,"Independent current I11 qualification absent"));
                var met=HalogenCarbonylRules.component(s,tuple.get(0),tuple.get(1));var phe=HalogenCarbonylRules.component(s,tuple.get(2),tuple.get(7));
                if(met==null||phe==null||!phe.correspondenceAlternatives().getFirst().values().containsAll(tuple.subList(2,8)))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Incomplete or ambiguous selected source correspondence"));
                if(met.identity().equals(phe.identity()))return List.of(finding(s,UNSUPPORTED,values,"Distinct attributed ligand/protein components required"));
                var partner=I11PartnerAttribution.check(s,met,phe,inputs,at.orElseThrow());if(partner!=SUPPORTED_PRESENT)return List.of(finding(s,partner,values,"Explicit independent ligand/protein attribution required"));
                var components=new LinkedHashSet<SystemStateView.Component>(List.of(met,phe));var coverages=new HashMap<SystemStateView.Component,JsonNode>();
                for(var c:components) {
                    if(c.chemistry().atoms().size()>request.maximumNodes())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Source component exceeds request budget"));
                    try{c.chemistry().validateTopology(true);}catch(IllegalArgumentException ex){return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Connected complete source component required"));}
                    if(c.equals(phe)&&c.chemistry().atoms().stream().anyMatch(a->!Set.of("C","N","O","S").contains(a.element())))return List.of(finding(s,UNSUPPORTED,values,"Protein component outside unchanged heavy C/N/O/S PHE source profile"));
                    var mapping=c.correspondenceAlternatives().getFirst();
                    for(var other:s.components())if(!other.identity().equals(c.identity()))for(var alternative:other.correspondenceAlternatives())if(alternative.values().stream().anyMatch(mapping.values()::contains))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Overlapping source component correspondence"));
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
                var selectedResidues=new ArrayList<totah.lab.gaia.structure.Residue>();for(var chain:s.graph().structure().getChains())if(chain.id().equals(tuple.get(2).chainId()))for(var residue:chain.residues())if(residue.getNumber()==tuple.get(2).residueNumber()&&Objects.equals(residue.getInsertionCode(),tuple.get(2).insertionCode()==' '?null:tuple.get(2).insertionCode()))selectedResidues.add(residue);
                if(selectedResidues.size()==1&&!selectedResidues.getFirst().getName().equals("PHE"))return List.of(finding(s,UNSUPPORTED,values,"Known non-PHE residue outside bounded protein PHE domain"));
                var pr=sourceRoles(s,phe,tuple.get(2),"PHE",List.of("N","CA","C","O","CB","CG","CD1","CE1","CZ","CE2","CD2"));
                if(pr==null)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Exact unique source PHE context required"));
                var pm=phe.correspondenceAlternatives().getFirst();
                if(!List.of("CG","CD1","CE1","CZ","CE2","CD2").stream().map(n->pm.get(pr.get(n))).toList().equals(tuple.subList(2,8)))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Selected ring does not bind source roles"));
                var pc=new Context(phe,coverages.get(phe));
                if(!pc.backbone(pr)||!pc.phe(pr))return List.of(finding(s,UNSUPPORTED,values,"Known source outside unchanged finite PHE context"));
                String cid=HalogenCarbonylRules.sourceId(met,tuple.get(0)),xid=HalogenCarbonylRules.sourceId(met,tuple.get(1));
                var carbon=met.chemistry().atom(cid).orElseThrow();var cl=met.chemistry().atom(xid).orElseThrow();
                var bonds=met.chemistry().bonds().stream().filter(b->b.firstAtomId().equals(xid)||b.secondAtomId().equals(xid)).toList();
                if(!carbon.element().equals("C")||carbon.formalCharge()!=0||!cl.element().equals("Cl")||cl.formalCharge()!=0||cl.aromatic()||coverages.get(met).path("atomState").path(xid).path("implicitHydrogenCount").asInt(-1)!=0||bonds.size()!=1||!new HashSet<>(List.of(bonds.getFirst().firstAtomId(),bonds.getFirst().secondAtomId())).equals(Set.of(cid,xid))||!bonds.getFirst().order().name().equals("SINGLE")||bonds.getFirst().aromatic())return List.of(finding(s,UNSUPPORTED,values,"Exact neutral carbon-bound Cl H0 with one ordinary single bond required"));
                values.put("sourceRoles",canonical(pr));
                var isotopes=tuple.subList(2,8).stream().map(a->phe.chemistry().atom(HalogenCarbonylRules.sourceId(phe,a)).orElseThrow().isotope()).toList();
                if(isotopes.stream().anyMatch(i->!Objects.equals(i,isotopes.getFirst())))return List.of(finding(s,UNSUPPORTED,values,"Mixed source ring isotope descriptors"));
                var ring=sources.report(phe,RING);
                if(ring==null||!ring.complete())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Complete qualified ring-pattern coverage required"));
                if(!ringMatch(ring,tuple.subList(2,8)))return List.of(finding(s,UNSUPPORTED,values,"Source ring pattern excludes selected ring"));
                var ops=JSON.readTree(raw).path("operations");var distances=new ArrayList<Double>();
                for(int i=0;i<6;i++){Double d=HalogenCarbonylRules.value(ops,i,"distanceAngstrom");if(d==null||d<=0)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Six finite positive same-frame distances required"));distances.add(d);}
                double minimum=Collections.min(distances);var nearest=new ArrayList<AtomReference>();for(int i=0;i<6;i++)if(distances.get(i)==minimum)nearest.add(tuple.get(i+2));
                values.put("ringMemberDistancesAngstrom",canonical(distances));values.put("nearestRingAtoms",canonical(nearest));
                if(nearest.size()!=1)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Exact nearest-ring ties are inconclusive"));
                require(plan.path("operations").get(6).path("atoms").get(1).equals(JSON.valueToTree(nearest.getFirst())),"I11 declared nearest ring atom mismatch");
                Double distance=HalogenCarbonylRules.value(ops,6,"firstCentroidDistanceAngstrom"),theta=HalogenCarbonylRules.value(ops,6,"firstSecondCentroidAngleDegrees");
                if(distance==null||distance<=0||theta==null||!ops.path(6).path("coverage").path("normalUniquenessStatus").asText().equals("SUPPORTED_PRESENT"))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Finite eligible centroid geometry and unique normal required"));
                double delta=Math.abs(distance-minimum);values.put("distanceAngstrom",distance.toString());values.put("thetaDegrees",theta.toString());values.put("distanceDifferenceAngstrom",Double.toString(delta));values.put("orientationCharacterization",characterization(delta));
                return List.of(finding(s,predicate(distance,theta)?SUPPORTED_PRESENT:ABSENT_FALSE,values,"Eligible selected Cl/Phe geometric candidate only; no energetic or whole-system absence claim"));
            }
            private Finding finding(SystemStateView s,EvidenceInterpretation.Status status,Map<String,String> values,String reason){return new Finding(evaluate?"evaluate":"collect",List.of(s.subject()),status,values,List.of(reason),m.limitations());}
        };
    }
    static String characterization(double delta){require(Double.isFinite(delta)&&delta>=0,"finite nonnegative distance difference required");return delta<=0.3?"FACE_ON":"EDGE_ON";}
    static boolean predicate(double distance,double theta){return Double.isFinite(distance)&&Double.isFinite(theta)&&distance>0&&distance<4.5&&theta>=0&&theta<140;}
    private static void verifyPlan(JsonNode plan,List<AtomReference> t) {
        var ops=plan.path("operations");var groups=plan.path("groups");require(groups.isArray()&&groups.size()==1&&plan.path("radiusAssignmentReference").isNull()&&ops.size()==7,"I11 requires six distances and one pair/group operation");
        require(groups.get(0).path("atoms").equals(JSON.valueToTree(t.subList(2,8).stream().sorted().toList())),"I11 ring selection mismatch");
        for(int i=0;i<6;i++)require(ops.get(i).path("kind").asText().equals("DISTANCE")&&ops.get(i).path("atoms").equals(JSON.valueToTree(List.of(t.get(1),t.get(i+2)))),"I11 all six exact ring distances required");
        var last=ops.get(6);require(last.path("kind").asText().equals("POINT_PAIR_GROUP")&&last.path("atoms").size()==2&&last.path("atoms").get(0).equals(JSON.valueToTree(t.get(1)))&&t.subList(2,8).stream().anyMatch(a->JSON.valueToTree(a).equals(last.path("atoms").get(1)))&&last.path("groupId").equals(groups.get(0).path("id")),"I11 selected pair/group mismatch");
    }
    private static boolean ringMatch(HbondCandidateSources.Report r,List<AtomReference> selected){var map=r.component().correspondenceAlternatives().getFirst();for(var occurrence:r.payload().path("occurrences"))for(var roles:occurrence.path("roleCorrespondenceAlternatives")){var found=new HashSet<AtomReference>();for(var id:roles.path("ringMembers"))found.add(map.get(id.asText()));if(found.equals(new HashSet<>(selected)))return true;}return false;}
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
        boolean fact(String id,String element,boolean aromatic,int h){var a=atom(id);return a.element().equals(element)&&a.formalCharge()==0&&a.aromatic()==aromatic&&coverage.path("atomState").path(id).path("implicitHydrogenCount").asInt(-1)==h;}
        boolean pattern(String id,String e,boolean aromatic,int h,Map<String,String> neighbors){return fact(id,e,aromatic,h)&&neighbors(id).equals(neighbors);}
        boolean single(String id,String e,int h,String... n){var expected=new TreeMap<String,String>();for(var x:n)expected.put(x,"SINGLE");return pattern(id,e,false,h,expected);}
        boolean phe(Map<String,String> r){if(!single(r.get("CB"),"C",2,r.get("CA"),r.get("CG")))return false;var names=List.of("CG","CD1","CE1","CZ","CE2","CD2");for(int i=0;i<6;i++){var n=new TreeMap<String,String>();n.put(r.get(names.get((i+5)%6)),"AROMATIC");n.put(r.get(names.get((i+1)%6)),"AROMATIC");if(i==0)n.put(r.get("CB"),"SINGLE");if(!pattern(r.get(names.get(i)),"C",true,i==0?0:1,n))return false;}return true;}
        boolean backbone(Map<String,String> r){String n=r.get("N"),ca=r.get("CA"),c=r.get("C"),o=r.get("O");
            if(!single(ca,"C",1,n,r.get("CB"),c)||!pattern(o,"O",false,0,Map.of(c,"DOUBLE"))||!fact(n,"N",false,1)||!fact(c,"C",false,0))return false;
            var nn=neighbors(n);var cn=neighbors(c);if(nn.size()!=2||!"SINGLE".equals(nn.get(ca))||cn.size()!=3||!"SINGLE".equals(cn.get(ca))||!"DOUBLE".equals(cn.get(o)))return false;
            String previous=nn.keySet().stream().filter(x->!x.equals(ca)).findFirst().orElseThrow();String following=cn.keySet().stream().filter(x->!x.equals(ca)&&!x.equals(o)).findFirst().orElseThrow();
            var map=component.correspondenceAlternatives().getFirst();if(sameResidue(map.get(previous),map.get(n))||sameResidue(map.get(following),map.get(c))||!"SINGLE".equals(nn.get(previous))||!"SINGLE".equals(cn.get(following))||!fact(previous,"C",false,0))return false;
            var pn=neighbors(previous);if(pn.size()!=3||!"SINGLE".equals(pn.get(n)))return false;var oxygen=pn.entrySet().stream().filter(e->e.getValue().equals("DOUBLE")&&atom(e.getKey()).element().equals("O")).map(Map.Entry::getKey).toList();if(oxygen.size()!=1)return false;String po=oxygen.getFirst();
            if(!sameResidue(map.get(previous),map.get(po))||!pattern(po,"O",false,0,Map.of(previous,"DOUBLE")))return false;
            String pc=pn.keySet().stream().filter(x->!x.equals(n)&&!x.equals(po)).findFirst().orElseThrow();var a=atom(pc);if(!a.element().equals("C")||a.aromatic()||a.formalCharge()!=0||!"SINGLE".equals(pn.get(pc)))return false;
            var fn=neighbors(following);if(fn.size()<1||fn.size()>3||!fact(following,"N",false,3-fn.size()))return false;for(var e:fn.entrySet())if(!e.getValue().equals("SINGLE")||!atom(e.getKey()).element().equals("C"))return false;return true;
        }
    }
}
