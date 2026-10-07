package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.research.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.time.Instant;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Explicit S1 composition. Historical I03-A keeps its unconditional block and original digest. */
final class S1ImplicitHProxyRules {
    private S1ImplicitHProxyRules() { }
    static void validate(RuleManifest m) {
        require(Set.of("athena-rule/2","athena-rule/3").contains(m.schema())&&m.ruleId().equals("ATHENA.I03.HEAVY_ATOM_DIRECTIONAL_PROXY_SP3_AMINES")&&m.version().equals("1.1.0")&&m.implementationVersion().equals("1")&&m.profile().equals("ATHENA_IMPLICIT_H_PROXY_S1_V1")&&m.family()==RuleManifest.Family.INTERACTION&&m.requiredCapabilities().isEmpty()&&m.qualification()==SystemGraphCertificate.Status.NOT_EVALUATED,"S1 composition manifest contract");
        try(var in=S1ImplicitHProxyRules.class.getResourceAsStream("implicit-h-proxy-s1-v1/"+m.ruleId()+".rule.json")) {
            require(in!=null,"S1 composition definition missing");var p=JSON.readTree(in);
            require(JSON.valueToTree(m.parameters()).equals(p.get("parameters"))&&JSON.valueToTree(m.negativeCoverage()).equals(p.get("negativeCoverage"))&&JSON.valueToTree(m.scientificSources()).equals(p.get("scientificSources")),"S1 composition definition changed");
        }catch(java.io.IOException e){throw new IllegalArgumentException("S1 composition definition unreadable",e);}
    }
    static ScientificReference method(RuleManifest m,boolean evaluate){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.implicit-h-proxy-s1",m.key()+(evaluate?"/evaluate":"/collect"),RuleRegistry.digest(m));}
    static SystemGraphAnalyzer analyzer(RuleManifest m,RuleRequest r,boolean evaluate) {
        validate(m);return new SystemGraphAnalyzer(){
            public ScientificReference method(){return S1ImplicitHProxyRules.method(m,evaluate);}
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return Set.of("athena:implicit-h-proxy-plan","athena:group-identities","athena:event-source","athena:rule-measurements","athena:system-state","athena:group-source-coverage","athena:source-artifact","athena:rule-manifest","athena:rule-policy-context","athena:rule-qualification-receipt","athena:rule-research-eligibility","athena:rule-implementation-qualification","athena:system-binding","athena:system-certificate","athena:rule-request");}
            public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> supplied,Map<String,String> config)throws Exception {
                require(s.binding().equals(r.state())&&m.key().equals(r.manifestKey())&&RuleRegistry.digest(m).equals(r.manifestSha256())&&r.atoms().isEmpty()&&r.first().isEmpty()&&r.second().isEmpty(),"S1 composition request mismatch");
                var inputs=new ImplicitHProxyInputs(s,m,r,supplied);var raw=raw(ImplicitHProxyRules.build(s,m,r,inputs));
                if(!evaluate)return List.of(new Finding("collect",List.of(s.subject()),SUPPORTED_PRESENT,Map.of("payload",canonical(raw)),List.of("Raw existing I03-A heavy-atom measurements; composition not evaluated"),m.limitations()));
                var measurements=supplied.stream().filter(e->e.evidenceType().equals("athena:rule-measurements")).toList();
                require(measurements.size()==1&&measurements.getFirst().method().equals(S1ImplicitHProxyRules.method(m,false))&&read(measurements.getFirst()).equals(raw),"S1 composition source/measurement replay mismatch");
                var at=S1Qualification.current(m,s,r,index(supplied));
                if(at.isEmpty())return List.of(new Finding("evaluate",List.of(s.subject()),NOT_EVALUATED,Map.of("payload",canonical(raw)),List.of("Separate current S1/I03 composition qualification absent"),m.limitations()));
                var eligible=new HashMap<AtomReference,Boolean>();
                for(var atom:java.util.stream.Stream.concat(inputs.donors.stream(),inputs.acceptors.stream()).toList()) {
                    var assignments=inputs.assignments.getOrDefault(atom,List.of());boolean valid=!assignments.isEmpty();
                    for(var e:assignments)valid &=assignment(s,atom,e,inputs.artifacts,at.orElseThrow());
                    eligible.put(atom,valid);
                }
                var report=(ObjectNode)raw.deepCopy();boolean complete=inputs.inventoryComplete&&eligible.values().stream().allMatch(Boolean::booleanValue)&&!report.path("candidates").isEmpty();boolean positive=false;var represented=new HashSet<AtomReference>();var reasons=new TreeSet<String>();
                if(raw.path("reasons").toString().contains("candidate enumeration truncated")){complete=false;reasons.add("candidate enumeration truncated");}
                for(var node:report.path("candidates")) {
                    var c=(ObjectNode)node;var donor=JSON.treeToValue(c.get("donor"),AtomReference.class);var acceptor=JSON.treeToValue(c.get("acceptor"),AtomReference.class);
                    represented.add(donor);represented.add(acceptor);var geometry=c.path("geometry");Boolean pass=null;
                    if(Boolean.TRUE.equals(eligible.get(donor))&&Boolean.TRUE.equals(eligible.get(acceptor))&&!geometry.isNull())pass=ImplicitHProxyGeometry.predicate(HbondCandidateGeometry.value(geometry,0,"distanceAngstrom"),c.get("donorMinimumDeviationDegrees").isNumber()?c.get("donorMinimumDeviationDegrees").asDouble():null,c.get("acceptorMinimumDeviationDegrees").isNumber()?c.get("acceptorMinimumDeviationDegrees").asDouble():null);
                    var status=pass==null?UNKNOWN_INCONCLUSIVE:pass?SUPPORTED_PRESENT:ABSENT_FALSE;
                    c.put("assessment",status.name());c.set("reasons",JSON.valueToTree(List.of(pass==null?"S1 endpoint eligibility or complete heavy-atom geometry unresolved":pass?"Qualified S1 endpoints and approved heavy-atom directional proxy pass":"Qualified S1 endpoints; complete measured proxy fails its bounded predicate")));
                    positive |=Boolean.TRUE.equals(pass);complete &=pass!=null;
                }
                complete &=represented.containsAll(eligible.keySet());
                var status=positive?SUPPORTED_PRESENT:complete?ABSENT_FALSE:UNKNOWN_INCONCLUSIVE;
                if(!complete)reasons.add("No exhaustive negative: inventory, endpoint applicability, geometry or enumeration incomplete");
                if(positive)reasons.add("Supported heavy-atom directional proxy; no observed or inferred hydrogen position");
                if(complete&&!positive)reasons.add("Complete bounded S1/I03 selection contains no qualifying proxy");
                report.put("complete",complete);report.put("assessment",status.name());report.set("reasons",JSON.valueToTree(reasons));
                return List.of(new Finding("evaluate",List.of(s.subject()),status,Map.of("payload",canonical(report)),List.copyOf(reasons),m.limitations()));
            }
        };
    }
    private static JsonNode raw(JsonNode source) {
        var report=(ObjectNode)source.deepCopy();var reasons=new TreeSet<String>();
        reasons.add("Raw collection has not verified S1 source eligibility or separate I03 composition authority");
        for(var reason:source.path("reasons")){String text=reason.asText();if(!text.startsWith("SP3 assignment producer/protocol")&&!text.startsWith("unqualified assignment producer/protocol:")&&!text.startsWith("selected atom remains scientifically ineligible"))reasons.add(text.replace("conflicting unqualified assignment assertions","conflicting selected assignment assertions"));}
        report.set("reasons",JSON.valueToTree(reasons));
        for(var node:report.path("candidates")){var candidate=(ObjectNode)node;var why=new TreeSet<String>();for(var reason:candidate.path("reasons"))why.add(reason.asText().replace("SP3 assignment producer/protocol unqualified; scientific predicate ineligible","S1 source eligibility not evaluated during raw collection").replace("no qualified SP3 applicability","endpoint applicability awaits independently qualified S1 replay"));candidate.set("reasons",JSON.valueToTree(why));}
        return report;
    }
    private static boolean assignment(SystemStateView s,AtomReference atom,EvidenceEnvelope envelope,Map<String,EvidenceEnvelope> artifacts,Instant at)throws Exception {
        var i=(EvidenceInterpretation)new EvidenceExchange().decodeRecord(envelope.readPayload());
        if(i.status()!=SUPPORTED_PRESENT||!i.measurements().get("hybridization").equals("SP3")||!i.recordedAt().equals(at))return false;
        var selected=new TreeMap<String,EvidenceEnvelope>();for(var dependency:i.inputs()){var e=pinned(JSON.valueToTree(dependency),artifacts);selected.put(canonical(e.reference()),e);}
        for(var e:selected.values())if(e.evidenceType().equals("athena:rule-manifest")) {
            var tree=read(e);if(!tree.path("implementationId").asText().equals(S1NitrogenRules.IMPLEMENTATION))continue;
            var m=RuleRegistry.decode(e.readPayload());if(!i.evaluator().equals(S1NitrogenRules.method(m)))continue;
            for(var proof:selected.values())if(proof.evidenceType().equals("athena:rule-qualification-receipt")) {
                var receipt=ResearchDocuments.decode(proof.readPayload(),RuleQualificationReceipt.class);
                if(!receipt.manifestSha256().equals(RuleRegistry.digest(m))||!receipt.evaluatedAt().equals(at))continue;
                var requestBytes=selected.values().stream().filter(x->x.payloadSha256().equals(receipt.request().sha256())).findFirst().orElseThrow(()->new IllegalArgumentException("S1 assignment request not selected"));
                var request=JSON.treeToValue(read(requestBytes),RuleRequest.class);
                var replay=S1NitrogenRules.analyzer(m,request).analyze(s,List.copyOf(selected.values()),Map.of());
                for(var finding:replay)if(canonical(atom).equals(finding.measurements().get("atom")))
                    return finding.status()==SUPPORTED_PRESENT&&finding.measurements().equals(i.measurements())&&finding.subjects().equals(i.subjects())&&finding.reasons().equals(i.reasons());
            }
        }
        return false;
    }
}
