package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import totah.lab.athena.design.grammar.FeatureTemplateAlignmentEvaluator;
import totah.lab.athena.system.*;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** One approved selected correspondence. Query geometry never becomes observed molecular geometry. */
final class SelectedPharmacophoreRules {
    static final String ID="ATHENA.N08.SELECTED_THREE_CARBONYL_TEMPLATE_RMSD";
    static final String TEMPLATE="athena:selected-pharmacophore-template";
    private static final String ROLE="ATHENA.PERCEPTION.ACCEPTOR.CARBONYL_O";
    private SelectedPharmacophoreRules() { }
    static void validate(RuleManifest m) {
        require(Set.of("athena-rule/2","athena-rule/3").contains(m.schema())&&m.ruleId().equals(ID)&&m.version().equals("1.0.0")&&m.profile().equals("ATHENA_N08_SELECTED_TEMPLATE_V1")&&m.implementationVersion().equals("1")&&m.qualification()==SystemGraphCertificate.Status.NOT_EVALUATED&&m.family()==RuleManifest.Family.MOTIF&&m.requiredCapabilities().isEmpty(),"N08 manifest contract");
        try(var in=SelectedPharmacophoreRules.class.getResourceAsStream("selected-pharmacophore-v1/"+ID+".rule.json")) {
            require(in!=null,"N08 definition missing");var definition=JSON.readTree(in);var actual=(ObjectNode)JSON.valueToTree(m.parameters());
            require(actual.has("templateSha256"),"explicit template pin parameter required");String pin=text(actual.path("templateSha256"),"value");require(pin.equals("UNBOUND")||pin.matches("[0-9a-f]{64}"),"exact template pin required");
            ((ObjectNode)actual.path("templateSha256")).put("value","UNBOUND");
            require(actual.equals(definition.get("parameters"))&&JSON.valueToTree(m.negativeCoverage()).equals(definition.get("negativeCoverage"))&&JSON.valueToTree(m.scientificSources()).equals(definition.get("scientificSources")),"N08 definition changed");
        }catch(java.io.IOException e){throw new IllegalArgumentException("N08 definition unreadable",e);}
    }
    static ScientificReference method(RuleManifest m,boolean evaluate){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.selected-pharmacophore",m.key()+(evaluate?"/evaluate":"/collect"),RuleRegistry.digest(m));}
    static SystemGraphAnalyzer analyzer(RuleManifest m,RuleRequest request,boolean evaluate) {
        validate(m);return new SystemGraphAnalyzer(){
            public ScientificReference method(){return SelectedPharmacophoreRules.method(m,evaluate);}
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return Set.of(TEMPLATE,"athena:group-identities","athena:group-source-coverage","athena:continuous-geometry-plan","athena:rule-measurements","athena:event-source","athena:source-artifact","athena:rule-manifest","athena:rule-policy-context","athena:rule-qualification-receipt","athena:rule-research-eligibility","athena:rule-implementation-qualification","athena:system-binding","athena:system-certificate","athena:rule-request","athena:system-state");}
            public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> supplied,Map<String,String> configuration)throws Exception {
                var t=request.atoms();require(s.binding().equals(request.state())&&m.key().equals(request.manifestKey())&&RuleRegistry.digest(m).equals(request.manifestSha256())&&t.size()==6&&new HashSet<>(t).size()==6&&s.atoms().keySet().containsAll(t)&&request.first().isEmpty()&&request.second().isEmpty(),"N08 exact ordered six-anchor tuple/state required");
                var inputs=index(supplied);var sources=new HbondCandidateSources(s,m,request,List.copyOf(inputs.values()));
                var templates=inputs.values().stream().filter(e->e.evidenceType().equals(TEMPLATE)).toList();require(templates.size()<=1,"ambiguous selected template");
                JsonNode template=templates.isEmpty()?null:read(templates.getFirst());if(template!=null)validateTemplate(template);
                var plans=inputs.values().stream().filter(e->e.evidenceType().equals("athena:continuous-geometry-plan")).toList();require(plans.size()==1,"one selected N08 geometry plan required");var plan=read(plans.getFirst());var ops=plan.path("operations");
                require(plan.path("groups").isArray()&&plan.path("groups").isEmpty()&&plan.path("radiusAssignmentReference").isNull()&&ops.size()==3,"N08 three exact source distances required");
                for(int i=0;i<3;i++)require(ops.get(i).path("kind").asText().equals("DISTANCE")&&ops.get(i).path("atoms").equals(JSON.valueToTree(List.of(t.get(2*i),t.get(2*((i+1)%3))))),"N08 source triplet/geometry mismatch");
                var geometry=RuleRegistry.decode(SystemStateView.bytes(HbondCandidateRules.parameter(m,"geometry")));
                var oxygen=List.of(t.get(0),t.get(2),t.get(4));var gr=new RuleRequest(s.binding(),geometry.key(),RuleRegistry.digest(geometry),oxygen.stream().sorted().toList(),List.of(),List.of(),request.radiusAngstrom(),0,request.maximumNodes(),request.maximumCandidates());
                var raw=RuleAnalyzers.collector(geometry,gr).analyze(s,plans,Map.of()).getFirst().measurements().get("payload");
                var values=new TreeMap<String,String>();values.put("payload",raw);values.put("proposition","SELECTED_THREE_CARBONYL_TEMPLATE_QUERY");values.put("sourcePins",canonical(sources.pins()));values.put("tuple",canonical(t));values.put("definitionSha256",RuleRegistry.digest(m));values.put("templateGeometryKind","QUERY_TEMPLATE");values.put("sourceGeometryKind","OBSERVED_SOURCE_COORDINATES");
                if(template!=null){values.put("templatePin",canonical(pin(templates.getFirst())));values.put("template",canonical(template));}
                if(!evaluate)return List.of(finding(s,SUPPORTED_PRESENT,values,"Raw source geometry and attributed query; source chemistry and query satisfaction not evaluated"));
                var measured=inputs.values().stream().filter(e->e.evidenceType().equals("athena:rule-measurements")).toList();require(measured.size()==1&&measured.getFirst().method().equals(SelectedPharmacophoreRules.method(m,false))&&read(measured.getFirst()).equals(JSON.readTree(raw)),"N08 measurement replay mismatch");
                if(template==null||m.parameters().get("templateSha256").value().equals("UNBOUND"))return List.of(finding(s,NOT_EVALUATED,values,"No exact bound reviewed query; no default template or tolerance"));
                require(m.parameters().get("templateSha256").value().equals(templates.getFirst().payloadSha256()),"N08 exact reviewed template bytes mismatch");
                var at=S1Qualification.current(m,s,request,inputs);if(at.isEmpty())return List.of(finding(s,NOT_EVALUATED,values,"Independent current exact-query qualification absent"));
                var component=HalogenCarbonylRules.component(s,t.get(0),t.get(1));if(component==null)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Incomplete or ambiguous source correspondence"));
                for(int i=1;i<3;i++){var c=HalogenCarbonylRules.component(s,t.get(2*i),t.get(2*i+1));if(c==null)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Incomplete or ambiguous source correspondence"));if(!component.identity().equals(c.identity()))return List.of(finding(s,UNSUPPORTED,values,"Selected features must coexist in one complete component"));}
                var scope=S1SourceScope.check(s,component,inputs);
                for(var witness:scope.witnesses())if(!S1Qualification.scope(witness,s,inputs,at.orElseThrow()))return List.of(finding(s,NOT_EVALUATED,values,"Independent exact source-scope authority absent"));
                if(scope.conflicting()||scope.witnesses().isEmpty()||!scope.complete()&&!scope.knownOutside())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Source scope unresolved"));
                if(scope.knownOutside())return List.of(finding(s,UNSUPPORTED,values,"Known nonordinary source connection outside this bounded profile"));
                var report=sources.report(component,ROLE);if(report==null)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Exact source carbonyl evidence absent"));
                var status=HalogenCarbonylRules.sourceFacts(s,component,report.payload().path("sourceCoverage"),inputs.values());if(status!=SUPPORTED_PRESENT)return List.of(finding(s,status,values,"Source facts incomplete, conflicting or outside domain"));
                if(!report.complete())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Complete source role coverage required"));
                for(int i=0;i<3;i++)if(!HalogenCarbonylRules.roles(report,List.of("carbonylOxygen","carbonylCarbon"),t.subList(2*i,2*i+2)))return List.of(finding(s,UNSUPPORTED,values,"Selected anchors do not identify three exact neutral carbonyl occurrences"));
                var rawOps=JSON.readTree(raw).path("operations");for(int i=0;i<3;i++){Double distance=HalogenCarbonylRules.value(rawOps,i,"distanceAngstrom");if(distance==null||distance<=0)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Qualified distinct finite source coordinates required"));}
                var sourcePoints=new ArrayList<Point3D>();for(var atom:oxygen)sourcePoints.add(s.atoms().get(atom).getPosition());var queryPoints=new ArrayList<Point3D>();for(var point:template.path("points"))queryPoints.add(point(point.path("coordinates")));
                if(!noncollinear(sourcePoints)||!noncollinear(queryPoints))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Degenerate or nonfinite triplet; no meaningful selected fit"));
                var sourceMap=new TreeMap<String,Point3D>();var targetMap=new TreeMap<String,Point3D>();for(int i=0;i<3;i++){String id=text(template.path("points").get(i),"featureId");sourceMap.put(id,sourcePoints.get(i));targetMap.put(id,queryPoints.get(i));}
                require(sourceMap.size()==3&&sourceMap.keySet().equals(targetMap.keySet()),"N08 exact three-to-three coverage");
                double bound=number(template.get("maximumRmsdAngstrom"));FeatureTemplateAlignmentEvaluator.AlignmentEvidence alignment;try{alignment=new FeatureTemplateAlignmentEvaluator().evaluate(text(template,"templateId"),sourceMap,targetMap,bound);}catch(IllegalArgumentException ex){return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Numerically unresolved finite rigid alignment"));}
                if(!alignment.evaluated()||alignment.rmsd()==null||!Double.isFinite(alignment.rmsd()))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Finite evaluated rigid alignment required"));
                values.put("rmsdAngstrom",alignment.rmsd().toString());values.put("maximumRmsdAngstrom",Double.toString(bound));values.put("correspondingFeatureIds",canonical(alignment.correspondingFeatureIds()));values.put("sourcePoints",canonical(sourcePoints));
                return List.of(finding(s,alignment.passed()?SUPPORTED_PRESENT:ABSENT_FALSE,values,"Exact selected template correspondence only; no whole-molecule negative"));
            }
            private Finding finding(SystemStateView s,EvidenceInterpretation.Status status,Map<String,String> values,String reason){return new Finding(evaluate?"evaluate":"collect",List.of(s.subject()),status,values,List.of(reason),m.limitations());}
        };
    }
    static void validateTemplate(JsonNode n)throws java.io.IOException {
        fields(n,"schema","templateId","points","coordinateUnit","maximumRmsdAngstrom","sourceReferences","sourceProtocol","limitations");require(text(n,"schema").equals("athena-selected-pharmacophore-template/1")&&text(n,"coordinateUnit").equals("angstrom"),"N08 query schema/unit");require(text(n,"templateId").matches(".+/[^/\\s]+"),"versioned template identifier required");
        require(number(n.get("maximumRmsdAngstrom"))>=0,"nonnegative explicit query bound required");var points=array(n,"points");require(points.size()==3,"exactly three query points required");var ids=new HashSet<String>();
        for(var p:points){fields(p,"featureId","requiredRole","coordinates");require(ids.add(text(p,"featureId"))&&text(p,"requiredRole").equals(ROLE),"unique exact carbonyl query features required");point(p.get("coordinates"));}
        var refs=array(n,"sourceReferences");require(!refs.isEmpty(),"attributed query sources required");for(var r:refs){var ref=reference(r);require(!ref.version().isBlank(),"immutable query source version required");}
        var protocol=reference(n.get("sourceProtocol"));protocol.require(ScientificReference.Kind.METHOD);require(!protocol.version().isBlank(),"versioned query protocol required");for(var l:array(n,"limitations"))require(l.isTextual(),"query limitation must be text");
    }
    private static double number(JsonNode n){require(n!=null&&n.isNumber()&&Double.isFinite(n.doubleValue()),"finite numeric query value required");return n.doubleValue();}
    private static Point3D point(JsonNode n){fields(n,"x","y","z");return new Point3D(number(n.get("x")),number(n.get("y")),number(n.get("z")));}
    static boolean noncollinear(List<Point3D> p){if(p.size()!=3)return false;for(var a:p)if(a==null||!Double.isFinite(a.x())||!Double.isFinite(a.y())||!Double.isFinite(a.z()))return false;double ax=p.get(1).x()-p.get(0).x(),ay=p.get(1).y()-p.get(0).y(),az=p.get(1).z()-p.get(0).z(),bx=p.get(2).x()-p.get(0).x(),by=p.get(2).y()-p.get(0).y(),bz=p.get(2).z()-p.get(0).z();double x=ay*bz-az*by,y=az*bx-ax*bz,z=ax*by-ay*bx;return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&(x!=0||y!=0||z!=0);}
}
