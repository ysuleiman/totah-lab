package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.*;
import totah.lab.gaia.structure.*;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** I03-A plumbing only. The unresolved SP3 producer is an unconditional scientific activation block. */
final class ImplicitHProxyRules {
    private ImplicitHProxyRules() { }
    static void validate(RuleManifest m){
        require(Set.of("athena-rule/2","athena-rule/3").contains(m.schema())&&m.ruleId().equals("ATHENA.I03.HEAVY_ATOM_DIRECTIONAL_PROXY_SP3_AMINES")
                &&m.version().equals("1.0.0")&&m.implementationVersion().equals("1")&&m.profile().equals("ATHENA_IMPLICIT_H_PROXY_V1")
                &&m.family()==RuleManifest.Family.INTERACTION&&m.requiredCapabilities().isEmpty(),"implicit-H proxy rule contract");
        try(var in=ImplicitHProxyRules.class.getResourceAsStream("implicit-h-proxy-v1/"+m.ruleId()+".rule.json")){
            require(in!=null,"missing pinned proxy definition");var pinned=JSON.readTree(in);
            require(JSON.valueToTree(m.parameters()).equals(pinned.get("parameters"))&&JSON.valueToTree(m.negativeCoverage()).equals(pinned.get("negativeCoverage"))
                    &&JSON.valueToTree(m.scientificSources()).equals(pinned.get("scientificSources")),"proxy scientific definition changed without review");
        }catch(java.io.IOException e){throw new IllegalArgumentException("proxy definition unreadable",e);}
    }
    static ScientificReference method(RuleManifest m,boolean evaluate){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.implicit-h-proxy",m.key()+(evaluate?"/evaluate":"/collect"),RuleRegistry.digest(m));}
    static SystemGraphAnalyzer analyzer(RuleManifest m,RuleRequest request,boolean evaluate){
        validate(m);return new SystemGraphAnalyzer(){
            public ScientificReference method(){return ImplicitHProxyRules.method(m,evaluate);}
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of();}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of();}
            public Set<String> evidenceTypes(){return Set.of("athena:implicit-h-proxy-plan","athena:group-identities","athena:event-source","athena:rule-measurements","athena:system-state");}
            public List<Finding> analyze(SystemStateView s,List<EvidenceEnvelope> inputs,Map<String,String> config)throws Exception{
                require(s.binding().equals(request.state())&&m.key().equals(request.manifestKey())&&RuleRegistry.digest(m).equals(request.manifestSha256())
                        &&request.atoms().isEmpty()&&request.first().isEmpty()&&request.second().isEmpty(),"proxy request binding/scope mismatch");
                var selected=new ImplicitHProxyInputs(s,m,request,inputs);var report=build(s,m,request,selected);
                if(evaluate){var raw=inputs.stream().filter(e->e.evidenceType().equals("athena:rule-measurements")).toList();
                    require(raw.size()==1&&raw.getFirst().method().equals(ImplicitHProxyRules.method(m,false))&&read(raw.getFirst()).equals(report),"proxy source/measurement replay mismatch");}
                // No selected independently qualified producer exists. Neither manifest qualification,
                // schema /3, caller configuration nor SUPPORTED_PRESENT annotation can bypass this.
                return List.of(new Finding(evaluate?"evaluate":"collect",List.of(s.subject()),evaluate?NOT_EVALUATED:SUPPORTED_PRESENT,
                        Map.of("payload",canonical(report)),List.of("Raw heavy-atom diagnostics; SP3 producer qualification absent"),m.limitations()));
            }
        };
    }
    static JsonNode build(SystemStateView s,RuleManifest m,RuleRequest request,ImplicitHProxyInputs input)throws Exception{
        var reasons=new TreeSet<>(input.reasons);var candidates=new ArrayList<Object>();
        var pairs=HbondCandidateRules.parameter(m,"classPairs");int considered=0;
        outer:for(var pair:pairs){String dc=pair.path("donorClass").asText(),ac=pair.path("acceptorClass").asText();
            var donors=anchors(s,input,dc,"donor",input.donors);var acceptors=anchors(s,input,ac,"acceptor",input.acceptors);
            for(var d:donors)for(var a:acceptors){
                if(considered++>=request.maximumCandidates()){reasons.add("candidate enumeration truncated");break outer;}
                var why=new TreeSet<String>();why.add("SP3 assignment producer/protocol unqualified; scientific predicate ineligible");
                var dn=neighbors(s,d,true,dc,why);var an=neighbors(s,a,false,ac,why);
                boolean separate=!d.report().component().identity().equals(a.report().component().identity());
                if(!separate)why.add("same-component pair outside I03-A domain");
                JsonNode geometry=JSON.nullNode();Double dd=null,ad=null;
                if(dn!=null&&an!=null&&separate){
                    geometry=ImplicitHProxyGeometry.measure(s,m,request,input.envelope,d.atom(),a.atom(),dn,an);
                    var da=new ArrayList<Double>();var aa=new ArrayList<Double>();
                    for(int i=0;i<dn.size();i++)da.add(HbondCandidateGeometry.value(geometry,i+1,"angleDegrees"));
                    for(int i=0;i<an.size();i++)aa.add(HbondCandidateGeometry.value(geometry,i+1+dn.size(),"angleDegrees"));
                    dd=ImplicitHProxyGeometry.minimum(da);ad=ImplicitHProxyGeometry.minimum(aa);
                    Boolean pass=ImplicitHProxyGeometry.predicate(HbondCandidateGeometry.value(geometry,0,"distanceAngstrom"),dd,ad);
                    why.add(pass==null?"raw geometry unresolved; no numeric negative":pass?"raw numeric proxy comparisons pass; no qualified SP3 applicability":"raw numeric proxy comparisons fail; no qualified SP3 applicability");
                }
                var c=new TreeMap<String,Object>();c.put("donor",d.atom());c.put("acceptor",a.atom());c.put("donorClass",dc);c.put("acceptorClass",ac);
                c.put("roleOccurrencePins",List.of(Map.of("source",pin(d.report().envelope()),"occurrence",d.occurrence()),Map.of("source",pin(a.report().envelope()),"occurrence",a.occurrence())));
                var hp=new TreeMap<String,Object>();for(var atom:List.of(d.atom(),a.atom()))for(var e:input.assignments.getOrDefault(atom,List.of()))hp.put(canonical(e.reference()),pin(e));
                c.put("hybridizationPins",hp.values());c.put("donorNeighbors",dn==null?List.of():dn);c.put("acceptorNeighbors",an==null?List.of():an);
                c.put("geometry",geometry);c.put("donorMinimumDeviationDegrees",dd);c.put("acceptorMinimumDeviationDegrees",ad);
                c.put("geometryKind","HEAVY_ATOM_DIRECTIONAL_PROXY");c.put("assessment",NOT_EVALUATED.name());c.put("reasons",why);candidates.add(c);
            }
        }
        // Empty/missing-role selections are not a covered negative. Inventory is never chemical authority.
        for(var atom:java.util.stream.Stream.concat(input.donors.stream(),input.acceptors.stream()).toList())
            reasons.add("selected atom remains scientifically ineligible without qualified source SP3: "+atom);
        var out=new TreeMap<String,Object>();out.put("schema","athena-implicit-h-proxy-measurements/1");out.put("definitionSha256",RuleRegistry.digest(m));
        out.put("stateBinding",s.binding());out.put("plan",pin(input.envelope));out.put("inputPins",input.artifacts.values().stream().map(EventPayload::pin).toList());
        out.put("geometryKind","HEAVY_ATOM_DIRECTIONAL_PROXY");out.put("candidates",candidates);out.put("complete",false);out.put("assessment",NOT_EVALUATED.name());
        out.put("reasons",reasons);out.put("limitations",m.limitations());return JSON.valueToTree(out);
    }
    private static List<HbondCandidateSources.Anchor> anchors(SystemStateView s,ImplicitHProxyInputs i,String id,String role,List<AtomReference> scope){
        var descriptors=JSON.valueToTree(List.of(Map.of("id",id,"role",role)));var out=new ArrayList<HbondCandidateSources.Anchor>();
        for(var c:s.components())for(var a:i.chemistry.anchors(c,descriptors))if(scope.contains(a.atom()))out.add(a);
        out.sort(Comparator.comparing(a->canonical(Map.of("atom",a.atom(),"occurrence",a.occurrence(),"source",pin(a.report().envelope())))));return out;
    }
    private static List<AtomReference> neighbors(SystemStateView s,HbondCandidateSources.Anchor anchor,boolean donor,String role,Set<String> reasons){
        var c=anchor.report().component();var graph=c.chemistry();var mapping=c.correspondenceAlternatives().getFirst();
        String id=anchor.atomId();var coverage=anchor.report().payload().path("sourceCoverage");var state=coverage.path("atomState").path(id);
        int degree=role.endsWith("PRIMARY")?1:role.endsWith("SECONDARY")?2:3;
        boolean valid=anchor.report().complete()&&s.frameQualified()&&graph.atom(id).orElseThrow().element().equals("N")
                &&mapping.size()==graph.atoms().size()&&new HashSet<>(mapping.values()).size()==mapping.size()
                &&s.components().stream().filter(x->x.correspondenceAlternatives().stream().anyMatch(y->y.containsValue(anchor.atom()))).count()==1;
        var expected=new HashSet<Bond>();for(var b:graph.bonds())expected.add(new Bond(mapping.get(b.firstAtomId()),mapping.get(b.secondAtomId()),totah.lab.gaia.chemistry.BondOrder.valueOf(b.order().name())));
        var actual=new HashSet<Bond>();s.graph().structure().bonds().stream().filter(b->mapping.containsValue(b.atom1())||mapping.containsValue(b.atom2())).forEach(actual::add);
        valid&=expected.equals(actual)&&s.graph().structure().getConnectivityMetadata().provenance()==ConnectivityProvenance.EXPLICIT;
        var heavy=new ArrayList<AtomReference>();var hydrogen=new TreeSet<String>();
        for(var b:graph.bonds()){
            String other=b.firstAtomId().equals(id)?b.secondAtomId():b.secondAtomId().equals(id)?b.firstAtomId():null;if(other==null)continue;
            var atom=graph.atom(other).orElseThrow();if(atom.element().equals("H")){hydrogen.add(other);continue;}
            heavy.add(mapping.get(other));var evidence=coverage.path("atomState").path(other);
            valid&=atom.element().equals("C")&&b.order()==MolecularGraph.BondOrder.SINGLE&&!atom.aromatic()
                    &&evidence.path("aromaticityStatus").asText().equals("SUPPORTED_PRESENT")&&evidence.path("aromaticityModel").asText().equals("OCL/2026.7.2");
        }
        var stated=new TreeSet<String>();state.path("explicitHydrogenAtomIds").forEach(a->stated.add(a.asText()));
        valid&=stated.equals(hydrogen)&&heavy.size()==degree&&state.path("implicitHydrogenCount").isIntegralNumber()
                &&state.path("implicitHydrogenCount").asInt()+hydrogen.size()==3-degree;
        if(donor)valid&=hydrogen.isEmpty()&&state.path("hydrogenMode").asText().equals("AUTHORITATIVE_IMPLICIT")&&state.path("implicitHydrogenCount").asInt()==3-degree;
        for(var entry:mapping.entrySet()){
            var atom=graph.atom(entry.getKey()).orElseThrow();var ev=coverage.path("atomState").path(entry.getKey());
            valid&=ev.path("aromaticityStatus").asText().equals("SUPPORTED_PRESENT")&&ev.path("aromaticityModel").asText().equals("OCL/2026.7.2")
                    &&ev.path("chargeStatus").asText().equals("SUPPORTED_PRESENT")&&ev.path("formalCharge").isIntegralNumber()
                    &&ev.path("formalCharge").asInt()==atom.formalCharge()&&s.charges().charges().containsKey(entry.getValue())&&s.charges().charge(entry.getValue())==atom.formalCharge();
        }
        if(!valid){reasons.add("source role/domain/neighbor/frame coverage unresolved or outside approved profile: "+anchor.atom());return null;}
        return heavy.stream().sorted(Comparator.comparing(EventPayload::canonical)).toList();
    }
}
