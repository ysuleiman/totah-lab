package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.system.*;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Literal source-feature identities. These are not zinc-binding or coordination roles. */
final class ZnSourceFeatureRules {
    static final String ID="ATHENA.P07.RDKIT_ZNBINDER_SOURCE_FEATURES_NEUTRAL_HEAVY";
    private ZnSourceFeatureRules() { }
    static void validate(RuleManifest m) {
        require(Set.of("athena-rule/2","athena-rule/3").contains(m.schema())&&m.ruleId().equals(ID)&&m.version().equals("1.0.0")&&m.profile().equals("ATHENA_P07_ZN_SOURCE_FEATURES_V1")&&m.implementationVersion().equals("1")&&m.qualification()==SystemGraphCertificate.Status.NOT_EVALUATED&&m.family()==RuleManifest.Family.MOTIF&&m.requiredCapabilities().isEmpty(),"P07 manifest contract");
        try(var in=ZnSourceFeatureRules.class.getResourceAsStream("zn-source-features-v1/"+ID+".rule.json")) {
            require(in!=null,"P07 definition missing");var definition=JSON.readTree(in);
            require(JSON.valueToTree(m.parameters()).equals(definition.get("parameters"))&&JSON.valueToTree(m.negativeCoverage()).equals(definition.get("negativeCoverage"))&&JSON.valueToTree(m.scientificSources()).equals(definition.get("scientificSources")),"P07 definition changed");
        }catch(java.io.IOException e){throw new IllegalArgumentException("P07 definition unreadable",e);}
    }
    static ScientificReference method(RuleManifest m,boolean evaluate){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.zn-source-features",m.key()+(evaluate?"/evaluate":"/collect"),RuleRegistry.digest(m));}
    static SystemGraphAnalyzer analyzer(RuleManifest m,RuleRequest request,boolean evaluate) {
        validate(m);return new SystemGraphAnalyzer(){
            public ScientificReference method(){return ZnSourceFeatureRules.method(m,evaluate);}
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return Set.of("athena:group-identities","athena:group-source-coverage","athena:rule-measurements","athena:event-source","athena:source-artifact","athena:rule-manifest","athena:rule-policy-context","athena:rule-qualification-receipt","athena:rule-research-eligibility","athena:rule-implementation-qualification","athena:system-binding","athena:system-certificate","athena:rule-request","athena:system-state");}
            public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> supplied,Map<String,String> configuration)throws Exception {
                var selected=new HashSet<>(request.atoms());require(s.binding().equals(request.state())&&m.key().equals(request.manifestKey())&&RuleRegistry.digest(m).equals(request.manifestSha256())&&!selected.isEmpty()&&selected.size()==request.atoms().size()&&s.atoms().keySet().containsAll(selected)&&request.first().isEmpty()&&request.second().isEmpty(),"P07 explicit whole-component selection required");
                var inputs=index(supplied);var sources=new HbondCandidateSources(s,m,request,List.copyOf(inputs.values()));var features=HbondCandidateRules.parameter(m,"features");
                var values=new TreeMap<String,String>();String raw=canonical(s.snapshot());values.put("payload",raw);values.put("proposition","LITERAL_ADVISORY_SOURCE_FEATURE_IDENTITY");values.put("definitionSha256",RuleRegistry.digest(m));values.put("sourcePins",canonical(sources.pins()));values.put("selectedAtoms",canonical(selected.stream().sorted().toList()));
                var components=s.components().stream().filter(c->c.correspondenceAlternatives().size()==1&&new HashSet<>(c.correspondenceAlternatives().getFirst().values()).equals(selected)).toList();
                SystemStateView.Component component=components.size()==1?components.getFirst():null;
                var unions=new TreeMap<String,Map<String,Map<String,Object>>>();var complete=new TreeMap<String,Boolean>();
                for(var names=features.fieldNames();names.hasNext();) {
                    String name=names.next();var union=new TreeMap<String,Map<String,Object>>();boolean all=component!=null;
                    for(var branch:features.path(name)) {
                        var report=component==null?null:sources.report(component,branch.asText());all &=report!=null&&report.complete();if(report==null)continue;
                        for(var occurrence:report.payload().path("occurrences")) {
                            String key=canonical(occurrence.path("memberAtomIds"));var item=union.computeIfAbsent(key,k->{var v=new TreeMap<String,Object>();v.put("memberAtomIds",occurrence.path("memberAtomIds"));v.put("branches",new TreeMap<String,Object>());return v;});
                            @SuppressWarnings("unchecked") var branches=(Map<String,Object>)item.get("branches");branches.put(branch.asText(),Map.of("sourceOccurrence",occurrence,"sourceReportPin",pin(report.envelope())));
                        }
                    }
                    unions.put(name,union);complete.put(name,all);
                }
                values.put("rawSourceFeatureOccurrences",canonical(unions));values.put("branchCoverage",canonical(complete));
                if(!evaluate)return List.of(finding("collect",s,SUPPORTED_PRESENT,values,"Raw source-pattern witnesses only; source eligibility and zinc binding are not established"));
                var measured=inputs.values().stream().filter(e->e.evidenceType().equals("athena:rule-measurements")).toList();require(measured.size()==1&&measured.getFirst().method().equals(ZnSourceFeatureRules.method(m,false))&&read(measured.getFirst()).equals(JSON.readTree(raw)),"P07 source snapshot replay mismatch");
                var at=S1Qualification.current(m,s,request,inputs);if(at.isEmpty())return all(s,NOT_EVALUATED,values,features,"Independent current exact-feature authority absent");
                if(component==null||selected.size()>request.maximumNodes())return all(s,UNKNOWN_INCONCLUSIVE,values,features,"One whole source component with unique correspondence within budget required");
                try{component.chemistry().validateTopology(true);}catch(IllegalArgumentException ex){return all(s,UNKNOWN_INCONCLUSIVE,values,features,"Connected complete source component required");}
                for(var a:component.chemistry().atoms())if(!Set.of("C","N","O","S","P").contains(a.element()))return all(s,UNSUPPORTED,values,features,"Only heavy C/N/O/S/P source representation admitted; no projection");
                var mapping=component.correspondenceAlternatives().getFirst();if(!mapping.keySet().equals(component.chemistry().atoms().stream().map(a->a.id()).collect(java.util.stream.Collectors.toSet()))||new HashSet<>(mapping.values()).size()!=mapping.size())return all(s,UNKNOWN_INCONCLUSIVE,values,features,"Lossless complete source correspondence required");
                var scope=S1SourceScope.check(s,component,inputs);for(var witness:scope.witnesses())if(!S1Qualification.scope(witness,s,inputs,at.orElseThrow()))return all(s,NOT_EVALUATED,values,features,"Independent original-source scope authority absent");
                if(scope.conflicting()||scope.witnesses().isEmpty()||!scope.complete()&&!scope.knownOutside())return all(s,UNKNOWN_INCONCLUSIVE,values,features,"Original source connection/electronic scope unresolved or conflicting");
                if(scope.knownOutside())return all(s,UNSUPPORTED,values,features,"Known nonordinary source connection outside selected feature domain");
                var coverages=new ArrayList<EvidenceEnvelope>();for(var e:inputs.values())if(e.evidenceType().equals("athena:group-source-coverage")&&read(e).path("componentReference").equals(JSON.valueToTree(component.identity())))coverages.add(e);
                if(coverages.isEmpty())return all(s,UNKNOWN_INCONCLUSIVE,values,features,"Explicit complete source facts required");var coverage=read(coverages.getFirst());for(var e:coverages)if(!read(e).equals(coverage))return all(s,UNKNOWN_INCONCLUSIVE,values,features,"Conflicting original source facts");
                ZincCarbonylRules.verifyCoverage(s,component,coverage);var status=HalogenCarbonylRules.sourceFacts(s,component,coverage,inputs.values());if(status!=SUPPORTED_PRESENT)return all(s,status,values,features,"Missing, contradictory or excluded original source chemistry");
                if(!coverage.path("completeGraph").asText().equals("SUPPORTED_PRESENT"))return all(s,UNKNOWN_INCONCLUSIVE,values,features,"Complete original graph required");
                if(component.chemistry().atoms().stream().anyMatch(a->a.formalCharge()!=0))return all(s,UNSUPPORTED,values,features,"Known charged source component excluded; no neutralization");
                var out=new ArrayList<Finding>();for(var entry:unions.entrySet()) {
                    var data=new TreeMap<>(values);data.put("originalFeatureName",entry.getKey());data.put("sourceFeatureOccurrences",canonical(entry.getValue()));boolean exhaustive=complete.get(entry.getKey())&&entry.getValue().size()<=request.maximumCandidates();data.put("countExhaustive",Boolean.toString(exhaustive));
                    out.add(finding(entry.getKey(),s,!exhaustive?UNKNOWN_INCONCLUSIVE:entry.getValue().isEmpty()?ABSENT_FALSE:SUPPORTED_PRESENT,data,exhaustive?"Exact original source-feature identity only; not zinc binding or coordination":"Incomplete original feature branches or enumeration budget; retain raw witnesses only"));
                }return List.copyOf(out);
            }
            private List<Finding> all(SystemStateView s,EvidenceInterpretation.Status status,Map<String,String> values,JsonNode features,String reason){var out=new ArrayList<Finding>();var names=new TreeSet<String>();features.fieldNames().forEachRemaining(names::add);for(var name:names){var v=new TreeMap<>(values);v.put("originalFeatureName",name);v.put("countExhaustive","false");out.add(finding(name,s,status,v,reason));}return List.copyOf(out);}
            private Finding finding(String id,SystemStateView s,EvidenceInterpretation.Status status,Map<String,String> values,String reason){return new Finding(id,List.of(s.subject()),status,values,List.of(reason),m.limitations());}
        };
    }
}
