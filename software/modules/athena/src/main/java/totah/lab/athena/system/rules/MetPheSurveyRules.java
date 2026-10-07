package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.system.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Bounded source Met/Phe survey contact; no energy or chalcogen-O interpretation. */
final class MetPheSurveyRules {
    static final String ID="ATHENA.G03.SOURCE_MET_PHE_SURVEY_CONTACT";
    private static final String SULFUR="ATHENA.G03.SOURCE.THIOETHER", RING="ATHENA.G03.SOURCE.CARBON_RING";
    private MetPheSurveyRules() { }
    static void validate(RuleManifest m) {
        require(Set.of("athena-rule/2","athena-rule/3").contains(m.schema())&&m.ruleId().equals(ID)&&m.version().equals("1.0.0")&&m.profile().equals("ATHENA_G03_MET_PHE_SURVEY_V1")&&m.implementationVersion().equals("1")&&m.qualification()==SystemGraphCertificate.Status.NOT_EVALUATED&&m.family()==RuleManifest.Family.INTERACTION&&m.requiredCapabilities().isEmpty(),"G03 manifest contract");
        try(var in=MetPheSurveyRules.class.getResourceAsStream("met-phe-survey-v1/"+ID+".rule.json")) {
            require(in!=null,"G03 definition missing");var definition=JSON.readTree(in);
            require(JSON.valueToTree(m.parameters()).equals(definition.get("parameters"))&&JSON.valueToTree(m.negativeCoverage()).equals(definition.get("negativeCoverage"))&&JSON.valueToTree(m.scientificSources()).equals(definition.get("scientificSources")),"G03 definition changed");
        }catch(java.io.IOException e){throw new IllegalArgumentException("G03 definition unreadable",e);}
    }
    static ScientificReference method(RuleManifest m,boolean evaluate){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.met-phe-survey",m.key()+(evaluate?"/evaluate":"/collect"),RuleRegistry.digest(m));}
    static SystemGraphAnalyzer analyzer(RuleManifest m,RuleRequest request,boolean evaluate) {
        validate(m);return new SystemGraphAnalyzer(){
            public ScientificReference method(){return MetPheSurveyRules.method(m,evaluate);}
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return Set.of("athena:group-identities","athena:group-source-coverage","athena:continuous-geometry-plan","athena:rule-measurements","athena:event-source","athena:source-artifact","athena:rule-manifest","athena:rule-policy-context","athena:rule-qualification-receipt","athena:rule-research-eligibility","athena:rule-implementation-qualification","athena:system-binding","athena:system-certificate","athena:rule-request","athena:system-state");}
            public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> supplied,Map<String,String> configuration)throws Exception {
                var tuple=request.atoms();require(s.binding().equals(request.state())&&m.key().equals(request.manifestKey())&&RuleRegistry.digest(m).equals(request.manifestSha256())&&tuple.size()==8&&new HashSet<>(tuple).size()==8&&request.first().isEmpty()&&request.second().isEmpty(),"G03 exact ordered tuple/state required");
                var inputs=index(supplied);var sources=new HbondCandidateSources(s,m,request,List.copyOf(inputs.values()));
                var plans=inputs.values().stream().filter(e->e.evidenceType().equals("athena:continuous-geometry-plan")).toList();require(plans.size()==1,"one selected G03 geometry plan required");var plan=read(plans.getFirst());verifyPlan(plan,tuple);
                var geometry=RuleRegistry.decode(SystemStateView.bytes(HbondCandidateRules.parameter(m,"geometry")));
                var gr=new RuleRequest(s.binding(),geometry.key(),RuleRegistry.digest(geometry),tuple.stream().sorted().toList(),List.of(),List.of(),request.radiusAngstrom(),0,request.maximumNodes(),request.maximumCandidates());
                var raw=RuleAnalyzers.collector(geometry,gr).analyze(s,plans,Map.of()).getFirst().measurements().get("payload");
                var values=new TreeMap<String,String>();values.put("payload",raw);values.put("proposition","SELECTED_SOURCE_MET_PHE_SURVEY_CONTACT");values.put("sourcePins",canonical(sources.pins()));values.put("tuple",canonical(tuple));values.put("definitionSha256",RuleRegistry.digest(m));
                if(!evaluate)return List.of(finding(s,SUPPORTED_PRESENT,values,"Raw selected G03 geometry; scientific eligibility not evaluated"));
                var measurements=inputs.values().stream().filter(e->e.evidenceType().equals("athena:rule-measurements")).toList();require(measurements.size()==1&&measurements.getFirst().method().equals(MetPheSurveyRules.method(m,false))&&read(measurements.getFirst()).equals(JSON.readTree(raw)),"G03 measurement replay mismatch");
                var at=S1Qualification.current(m,s,request,inputs);if(at.isEmpty())return List.of(finding(s,NOT_EVALUATED,values,"Independent current G03 qualification absent"));
                var met=HalogenCarbonylRules.component(s,tuple.get(0),tuple.get(1));var phe=HalogenCarbonylRules.component(s,tuple.get(2),tuple.get(7));
                if(met==null||phe==null||!phe.correspondenceAlternatives().getFirst().values().containsAll(tuple.subList(2,8)))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Incomplete or ambiguous selected source correspondence"));
                var components=new LinkedHashSet<SystemStateView.Component>(List.of(met,phe));var coverages=new HashMap<SystemStateView.Component,JsonNode>();
                for(var c:components) {
                    if(c.chemistry().atoms().size()>request.maximumNodes())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Source component exceeds request budget"));
                    try{c.chemistry().validateTopology(true);}catch(IllegalArgumentException ex){return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Connected complete source component required"));}
                    if(c.chemistry().atoms().stream().anyMatch(a->!Set.of("C","N","O","S").contains(a.element())))return List.of(finding(s,UNSUPPORTED,values,"Only heavy C/N/O/S source graphs admitted"));
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
                var mr=sourceRoles(s,met,tuple.get(0),"MET",List.of("N","CA","C","O","CB","CG","SD","CE"));
                var pr=sourceRoles(s,phe,tuple.get(2),"PHE",List.of("N","CA","C","O","CB","CG","CD1","CE1","CZ","CE2","CD2"));
                if(mr==null||pr==null||sameResidue(tuple.get(0),tuple.get(2)))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Exact unique distinct MET/PHE source residue identities required"));
                var mm=met.correspondenceAlternatives().getFirst();var pm=phe.correspondenceAlternatives().getFirst();
                if(!List.of(mm.get(mr.get("CG")),mm.get(mr.get("SD"))).equals(tuple.subList(0,2))||!List.of("CG","CD1","CE1","CZ","CE2","CD2").stream().map(n->pm.get(pr.get(n))).toList().equals(tuple.subList(2,8)))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Selected tuple does not bind exact source roles"));
                values.put("sourceRoles",canonical(Map.of("MET",mr,"PHE",pr)));values.put("sourceResidueLabels",canonical(List.of("MET","PHE")));
                var mc=new Context(met,coverages.get(met));var pc=new Context(phe,coverages.get(phe));
                if(!mc.backbone(mr)||!pc.backbone(pr)||!mc.met(mr)||!pc.phe(pr))return List.of(finding(s,UNSUPPORTED,values,"Known graph outside exact bounded neutral Met/Phe amide context"));
                var isotopes=tuple.subList(2,8).stream().map(a->phe.chemistry().atom(HalogenCarbonylRules.sourceId(phe,a)).orElseThrow().isotope()).toList();
                if(isotopes.stream().anyMatch(i->!Objects.equals(i,isotopes.getFirst())))return List.of(finding(s,UNSUPPORTED,values,"Mixed source ring isotope descriptors"));
                var sulfur=sources.report(met,SULFUR);var ring=sources.report(phe,RING);
                if(sulfur==null||ring==null||!sulfur.complete()||!ring.complete())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Applicable source-pattern enumeration/domain incomplete"));
                if(!HalogenCarbonylRules.roles(sulfur,List.of("sulfur"),List.of(tuple.get(1)))||!ringMatch(ring,tuple.subList(2,8)))return List.of(finding(s,UNSUPPORTED,values,"Complete source patterns exclude selected sulfur/ring"));
                var ops=JSON.readTree(raw).path("operations");Double distance=HalogenCarbonylRules.value(ops,0,"secondCentroidDistanceAngstrom"),angle=HalogenCarbonylRules.value(ops,0,"secondCentroidNormalAngleDegrees");
                if(distance==null||distance<=0||angle==null||!ops.path(0).path("coverage").path("normalUniquenessStatus").asText().equals("SUPPORTED_PRESENT"))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Finite positive SD-centroid distance and qualified unique ring normal required"));
                values.put("distanceAngstrom",distance.toString());values.put("diagnosticNormalAngleDegrees",angle.toString());
                return List.of(finding(s,distance<=7.0?SUPPORTED_PRESENT:ABSENT_FALSE,values,"Exact selected source survey-distance convention; no energy, angle classification or system-wide absence"));
            }
            private Finding finding(SystemStateView s,EvidenceInterpretation.Status status,Map<String,String> values,String reason){return new Finding(evaluate?"evaluate":"collect",List.of(s.subject()),status,values,List.of(reason),m.limitations());}
        };
    }
    private static void verifyPlan(JsonNode plan,List<AtomReference> t) {
        var ops=plan.path("operations");var groups=plan.path("groups");require(groups.isArray()&&groups.size()==1&&plan.path("radiusAssignmentReference").isNull()&&ops.size()==1,"one exact G03 pair/group operation required");
        require(groups.get(0).path("atoms").equals(JSON.valueToTree(t.subList(2,8).stream().sorted().toList()))&&ops.get(0).path("kind").asText().equals("POINT_PAIR_GROUP")&&ops.get(0).path("atoms").equals(JSON.valueToTree(t.subList(0,2)))&&ops.get(0).path("groupId").equals(groups.get(0).path("id")),"G03 selected geometry mismatch");
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
        Map<String,String> neighbors(String id){var n=new TreeMap<String,String>();for(var b:component.chemistry().bonds()){String other=b.firstAtomId().equals(id)?b.secondAtomId():b.secondAtomId().equals(id)?b.firstAtomId():null;if(other!=null)n.put(other,b.aromatic()?"AROMATIC":b.order().name());}return n;}
        boolean fact(String id,String element,boolean aromatic,int h){var a=atom(id);return a.element().equals(element)&&a.formalCharge()==0&&a.aromatic()==aromatic&&coverage.path("atomState").path(id).path("implicitHydrogenCount").asInt(-1)==h;}
        boolean pattern(String id,String e,boolean aromatic,int h,Map<String,String> neighbors){return fact(id,e,aromatic,h)&&neighbors(id).equals(neighbors);}
        boolean single(String id,String e,int h,String... n){var expected=new TreeMap<String,String>();for(var x:n)expected.put(x,"SINGLE");return pattern(id,e,false,h,expected);}
        boolean met(Map<String,String> r){return single(r.get("SD"),"S",0,r.get("CG"),r.get("CE"))&&single(r.get("CG"),"C",2,r.get("CB"),r.get("SD"))&&single(r.get("CE"),"C",3,r.get("SD"))&&single(r.get("CB"),"C",2,r.get("CA"),r.get("CG"));}
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
