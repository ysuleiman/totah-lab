package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.system.*;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Selected complete source component, seven independently qualified native catalog entries. */
final class AdvisoryAlertRules {
    static final String ID="ATHENA.A07.OCL_PAINS_ADVISORY_OCCURRENCES";
    static final Set<Integer> ADMITTED=Set.of(113,159,162,169,202,207,840);
    private static final Map<Integer,Integer> COMPILED_ATOMS=Map.of(113,10,159,12,162,8,169,8,202,6,207,8,840,4);
    private static final String MATCHER="2026.7.2/athena-ocl-idcode-occurrences/1";
    private AdvisoryAlertRules() { }
    static void validate(RuleManifest m){
        require(Set.of("athena-rule/2","athena-rule/3").contains(m.schema())&&m.ruleId().equals(ID)&&m.version().equals("1.0.0")&&m.profile().equals("ATHENA_A07_NATIVE_PAINS_V1")&&m.implementationVersion().equals("1")&&m.qualification()==SystemGraphCertificate.Status.NOT_EVALUATED&&m.family()==RuleManifest.Family.MOTIF&&m.requiredCapabilities().isEmpty(),"A07 manifest contract");
        try(var in=AdvisoryAlertRules.class.getResourceAsStream("advisory-alert-v1/"+ID+".rule.json")){
            require(in!=null,"A07 definition missing");var d=JSON.readTree(in);require(JSON.valueToTree(m.parameters()).equals(d.get("parameters"))&&JSON.valueToTree(m.negativeCoverage()).equals(d.get("negativeCoverage"))&&JSON.valueToTree(m.scientificSources()).equals(d.get("scientificSources")),"A07 definition changed");
        }catch(java.io.IOException e){throw new IllegalArgumentException("A07 definition unreadable",e);}
    }
    static ScientificReference method(RuleManifest m,boolean evaluate){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.advisory-alert",m.key()+(evaluate?"/evaluate":"/collect"),RuleRegistry.digest(m));}
    static SystemGraphAnalyzer analyzer(RuleManifest m,RuleRequest request,SubstructureMatcher matcher,boolean evaluate){
        validate(m);return new SystemGraphAnalyzer(){
            public ScientificReference method(){return AdvisoryAlertRules.method(m,evaluate);}
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return Set.of("athena:advisory-alert-catalog","athena:group-source-coverage","athena:rule-measurements","athena:event-source","athena:source-artifact","athena:rule-manifest","athena:rule-policy-context","athena:rule-qualification-receipt","athena:rule-research-eligibility","athena:rule-implementation-qualification","athena:system-binding","athena:system-certificate","athena:rule-request","athena:system-state");}
            public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> supplied,Map<String,String> config)throws Exception {
                require(s.binding().equals(request.state())&&m.key().equals(request.manifestKey())&&RuleRegistry.digest(m).equals(request.manifestSha256())&&request.atoms().isEmpty()&&request.first().isEmpty()&&request.second().isEmpty(),"A07 source state/request binding");
                var inputs=index(supplied);var values=new TreeMap<String,String>();values.put("payload","{}");values.put("proposition","EXACT_ADVISORY_CATALOG_OCCURRENCE_ONLY");values.put("catalogWideAbsenceAvailable","false");values.put("entryDispositionArtifactSha256",m.scientificSources().stream().filter(x->x.locator().endsWith("/entry-dispositions.json")).findFirst().orElseThrow().sha256());
                var cats=inputs.values().stream().filter(e->e.evidenceType().equals("athena:advisory-alert-catalog")).toList();
                if(cats.size()!=1)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"One exact attributed catalog required"));
                var catalog=AdvisoryAlertCatalog.decode(cats.getFirst().readPayload());
                if(s.components().size()!=1||s.components().getFirst().correspondenceAlternatives().size()!=1||s.atoms().size()>request.maximumNodes()||request.maximumCandidates()<890)return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"One complete unambiguous declared source component and full catalog budget required"));
                var c=s.components().getFirst();var coverages=new ArrayList<EvidenceEnvelope>();for(var e:inputs.values())if(e.evidenceType().equals("athena:group-source-coverage")&&read(e).path("componentReference").equals(JSON.valueToTree(c.identity())))coverages.add(e);
                var sourcePins=inputs.values().stream().filter(e->Set.of("athena:advisory-alert-catalog","athena:group-source-coverage","athena:event-source").contains(e.evidenceType())).sorted(Comparator.comparing(e->canonical(e.reference()))).map(EventPayload::pin).toList();
                JsonNode report;
                if(!evaluate){
                    require(matcher!=null,"Native matcher required");var results=new TreeMap<String,Object>();
                    for(int entry:ADMITTED.stream().sorted().toList()){
                        try{results.put(Integer.toString(entry),Map.of("result",matcher.match(AdvisoryAlertCatalog.FORMAT,catalog.entries().get(entry).query(),c.chemistry())));}
                        catch(MolecularBackendException ex){results.put(Integer.toString(entry),Map.of("error",ex.getMessage()));}
                    }
                    var hydrogenChecks=new TreeMap<String,Object>();
                    if(!coverages.isEmpty())for(var a:c.chemistry().atoms()){
                        var fact=read(coverages.getFirst()).path("atomState").path(a.id());var h=fact.path("implicitHydrogenCount");
                        if(h.isIntegralNumber()&&h.canConvertToInt()&&h.asInt()>=0&&h.asInt()<=4){int total=h.asInt();for(var b:c.chemistry().bonds()){String other=b.firstAtomId().equals(a.id())?b.secondAtomId():b.secondAtomId().equals(a.id())?b.firstAtomId():null;if(other!=null&&c.chemistry().atom(other).orElseThrow().element().equals("H"))total++;}if(total<=4)try{hydrogenChecks.put(a.id(),Map.of("total",total,"result",matcher.match("[*;H"+total+"]",c.chemistry())));}catch(MolecularBackendException ex){hydrogenChecks.put(a.id(),Map.of("error",ex.getMessage()));}}
                    }
                    report=JSON.valueToTree(Map.of("stateBinding",s.binding(),"definitionSha256",RuleRegistry.digest(m),"catalogPin",pin(cats.getFirst()),"sourcePins",sourcePins,"results",results,"hydrogenChecks",hydrogenChecks));
                }else{
                    var measurements=inputs.values().stream().filter(e->e.evidenceType().equals("athena:rule-measurements")).toList();require(measurements.size()==1&&measurements.getFirst().method().equals(AdvisoryAlertRules.method(m,false)),"A07 raw collector provenance");report=read(measurements.getFirst());
                    fields(report,"stateBinding","definitionSha256","catalogPin","sourcePins","results","hydrogenChecks");require(report.path("stateBinding").equals(JSON.valueToTree(s.binding()))&&text(report,"definitionSha256").equals(RuleRegistry.digest(m))&&report.path("catalogPin").equals(JSON.valueToTree(pin(cats.getFirst())))&&report.path("sourcePins").equals(JSON.valueToTree(sourcePins)),"A07 report/state/catalog/source binding");
                }
                values.put("payload",canonical(report));
                if(!evaluate)return List.of(finding(s,SUPPORTED_PRESENT,values,"Raw native query execution only; no scientific eligibility asserted"));
                var at=S1Qualification.current(m,s,request,inputs);if(at.isEmpty())return List.of(finding(s,NOT_EVALUATED,values,"Independent current advisory contract authority absent"));
                if(coverages.isEmpty())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Complete attributed source facts required"));var coverage=read(coverages.getFirst());for(var e:coverages)if(!read(e).equals(coverage))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Conflicting source facts"));
                try{c.chemistry().validateTopology(true);}catch(IllegalArgumentException ex){return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Complete connected source graph required"));}
                if(!new HashSet<>(c.correspondenceAlternatives().getFirst().values()).equals(s.atoms().keySet()))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Lossless complete selected component required"));
                for(var e:inputs.values())if(e.evidenceType().equals("athena:event-source")){var record=new EvidenceExchange().decodeRecord(e.readPayload());if(record instanceof EvidenceInterpretation i&&Set.of("ATHENA.I03.SP3_SOURCE_SCOPE/1","ATHENA.I03.SP3_SOURCE_SCOPE/2").contains(i.measurements().getOrDefault("proposition","")))for(var dependency:i.inputs())if(!inputs.containsKey(canonical(dependency.reference())))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Missing independently bound original source/preparation evidence"));}
                var scope=S1SourceScope.check(s,c,inputs);for(var w:scope.witnesses())if(!S1Qualification.scope(w,s,inputs,at.orElseThrow()))return List.of(finding(s,NOT_EVALUATED,values,"Independent source-scope authority absent"));
                if(scope.conflicting()||scope.witnesses().isEmpty()||!scope.complete()&&!scope.knownOutside())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Missing or conflicting source scope"));
                if(scope.knownOutside())return List.of(finding(s,UNSUPPORTED,values,"Known nonordinary source connection"));
                ZincCarbonylRules.verifyCoverage(s,c,coverage);var status=HalogenCarbonylRules.sourceFacts(s,c,coverage,inputs.values());if(status!=SUPPORTED_PRESENT)return List.of(finding(s,status,values,"Source chemistry incomplete or outside closed-shell domain"));
                if(!coverage.path("completeGraph").asText().equals("SUPPORTED_PRESENT"))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Complete source coverage required"));
                for(var n:report.path("results"))if(n.path("error").asText().startsWith("NATIVE_TARGET_UNSUPPORTED"))return List.of(finding(s,UNSUPPORTED,values,"Source representation assertions outside the reviewed native profile"));
                var ids=c.chemistry().atoms().stream().map(MolecularGraph.Atom::id).collect(java.util.stream.Collectors.toSet());
                for(var a:c.chemistry().atoms()){
                    if(coverage.path("atomState").path(a.id()).path("implicitHydrogenCount").asInt(-1)!=a.explicitHydrogens())return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Authoritative source H count must also constrain the concrete matcher input; unspecified legacy zero cannot prove positive H count"));
                    var h=report.path("hydrogenChecks").path(a.id());if(h.has("error"))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Source hydrogen consistency unresolved"));
                    if(!h.has("result"))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Complete source H consistency coverage required"));
                    int total=coverage.path("atomState").path(a.id()).path("implicitHydrogenCount").asInt();for(var b:c.chemistry().bonds()){String other=b.firstAtomId().equals(a.id())?b.secondAtomId():b.secondAtomId().equals(a.id())?b.firstAtomId():null;if(other!=null&&c.chemistry().atom(other).orElseThrow().element().equals("H"))total++;}
                    require(h.path("total").asInt(-1)==total,"A07 source H report mismatch");var hr=JSON.treeToValue(h.get("result"),SubstructureMatcher.Result.class);require(hr.evidence().version().equals("2026.7.2/athena-ocl-occurrences/2")&&hr.evidence().messages().contains("query=[*;H"+total+"]")&&hr.evidence().messages().contains("sourceStateSha256="+hash(c.chemistry())),"A07 H matcher provenance");
                    if(hr.queryToTargetAtomIds().stream().noneMatch(x->x.containsValue(a.id())))return List.of(finding(s,UNKNOWN_INCONCLUSIVE,values,"Source H contradicts independently checked OCL H state"));
                }
                var assessment=new TreeMap<String,String>();boolean positive=false;var actual=new HashSet<String>();report.path("results").fieldNames().forEachRemaining(actual::add);require(actual.equals(ADMITTED.stream().map(Object::toString).collect(java.util.stream.Collectors.toSet())),"A07 admitted entry set");
                for(int entry:ADMITTED.stream().sorted().toList()){
                    var n=report.path("results").path(Integer.toString(entry));if(n.has("error")){assessment.put(Integer.toString(entry),UNSUPPORTED.name());continue;}
                    fields(n,"result");var result=JSON.treeToValue(n.get("result"),SubstructureMatcher.Result.class);var definition=catalog.entries().get(entry);
                    require(result.evidence().backend().equals("OPEN_CHEM_LIB")&&result.evidence().version().equals(MATCHER)&&result.evidence().operation().equals("substructure-match")&&result.evidence().messages().contains("query="+definition.query())&&result.evidence().messages().contains("queryFormat="+AdvisoryAlertCatalog.FORMAT)&&result.evidence().messages().contains("querySha256="+definition.querySha256())&&result.evidence().messages().contains("oclArtifactSha256="+AdvisoryAlertCatalog.JAR)&&result.evidence().messages().contains("sourceStateSha256="+hash(c.chemistry())),"A07 exact native result binding");
                    for(var mapping:result.queryToTargetAtomIds()){require(mapping.size()==COMPILED_ATOMS.get(entry)&&new HashSet<>(mapping.values()).size()==mapping.size()&&ids.containsAll(mapping.values()),"A07 total injective source correspondence");for(int i=0;i<mapping.size();i++)require(mapping.containsKey("query:"+i),"A07 compiled native query indices");}
                    verifyCorrespondences(result,ids);
                    boolean found=!result.queryToTargetAtomIds().isEmpty();positive|=found;assessment.put(Integer.toString(entry),(found?SUPPORTED_PRESENT:ABSENT_FALSE).name());
                }
                values.put("entryAssessments",canonical(assessment));
                return List.of(finding(s,positive?SUPPORTED_PRESENT:UNKNOWN_INCONCLUSIVE,values,"Advisory exact qualified-entry occurrence only; catalog-wide absence unavailable because other entries are unqualified"));
            }
            private Finding finding(SystemStateView s,EvidenceInterpretation.Status status,Map<String,String> values,String reason){return new Finding(evaluate?"evaluate":"collect",List.of(s.subject()),status,values,List.of(reason),m.limitations());}
        };
    }
    private static void verifyCorrespondences(SubstructureMatcher.Result result,Set<String> sourceIds)throws Exception {
        var expectedLineage=new TreeMap<String,String>();sourceIds.forEach(id->expectedLineage.put(id,id));
        require(result.evidence().atomLineage().equals(expectedLineage)&&result.evidence().graphChanges().isEmpty(),"A07 unchanged source lineage required");
        var groups=result.evidence().messages().stream().filter(x->x.startsWith("occurrenceEmbeddings=")).toList();
        require(groups.size()==result.queryToTargetAtomIds().size(),"A07 complete correspondence groups");var seen=new HashSet<List<String>>();
        Comparator<List<String>> order=(a,b)->{for(int i=0;i<Math.min(a.size(),b.size());i++){int c=a.get(i).compareTo(b.get(i));if(c!=0)return c;}return Integer.compare(a.size(),b.size());};
        for(int g=0;g<groups.size();g++){
            var n=JSON.readTree(groups.get(g).substring("occurrenceEmbeddings=".length()));fields(n,"targetAtomSet","compiledQueryIndexToTarget");
            var target=new ArrayList<String>();for(var id:array(n,"targetAtomSet")){require(id.isTextual(),"A07 source ID required");target.add(id.asText());}
            require(!target.isEmpty()&&target.equals(target.stream().distinct().sorted().toList())&&sourceIds.containsAll(target)&&seen.add(target),"A07 distinct canonical source atom set");
            var vectors=new TreeSet<List<String>>(order);for(var vector:array(n,"compiledQueryIndexToTarget")){require(vector.isArray(),"A07 native correspondence vector");var ids=new ArrayList<String>();for(var id:vector){require(id.isTextual(),"A07 native correspondence ID");ids.add(id.asText());}require(ids.size()==target.size()&&ids.stream().sorted().toList().equals(target)&&vectors.add(List.copyOf(ids)),"A07 injective unique correspondence alternative");}
            require(!vectors.isEmpty(),"A07 missing correspondence alternatives");var representative=result.queryToTargetAtomIds().get(g);require(representative.size()==target.size(),"A07 representative size");for(int i=0;i<target.size();i++)require(vectors.first().get(i).equals(representative.get("query:"+i)),"A07 stable native-index representative");
        }
    }

}
