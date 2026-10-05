package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;
import static totah.lab.daedalus.system.FoundationGroupAcceptanceTest.graph;

class ChargeGroupAcceptanceTest {
    static final Path MANIFEST = Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/charge-groups-v1/ATHENA.PERCEPTION.CHARGED_GROUP.rule.json");
    @TempDir Path temp;
    static RuleManifest model() throws Exception { return RuleRegistry.decode(Files.readAllBytes(MANIFEST)); }
    static List<RuleManifest> groupManifests() throws Exception {
        var result = new ArrayList<RuleManifest>();
        for (var n : JSON.readTree(model().parameters().get("sourceManifests").value())) result.add(RuleRegistry.decode(SystemStateView.bytes(n)));
        return result;
    }
    static EvidenceEnvelope wrap(SystemStateView state, String type, JsonNode payload, ScientificReference method, String id) {
        return SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"charge-fixtures"),id,type,
                SystemStateView.bytes(payload),method,state.subject(),T,List.of("synthetic structural fixture; no scientific interaction claim"));
    }
    static List<EvidenceEnvelope> sources(SystemStateView state, ObjectNode coverage) throws Exception {
        var result = new ArrayList<EvidenceEnvelope>();
        for (var m : groupManifests()) {
            var collector = RuleAnalyzers.collector(m,request(state,m),BACKEND);
            var findings = collector.analyze(state,List.of(envelope(state,"athena:group-source-coverage",coverage),
                    envelope(state,"athena:group-definition",JSON.readTree(m.parameters().get("definition").value()))),Map.of());
            result.add(wrap(state,"athena:group-identities",JSON.readTree(findings.getFirst().measurements().get("payload")),collector.method(),m.ruleId()));
        }
        return result;
    }
    static JsonNode run(SystemStateView state, List<EvidenceEnvelope> sources) throws Exception {
        var m=model(); var r=request(state,m); var before=SystemStateView.bytes(state.snapshot());
        var preserved = new ArrayList<byte[]>();
        for (var e : sources) preserved.add(new EvidenceExchange().encodeRecord(e));
        var collector=RuleAnalyzers.collector(m,r);
        var finding=collector.analyze(state,sources,Map.of()).getFirst();
        var report=JSON.readTree(finding.measurements().get("payload"));
        var envelope=wrap(state,"athena:charge-groups",report,collector.method(),"systems");
        var exchange=new EvidenceExchange(); var restored=(EvidenceEnvelope)exchange.decodeRecord(exchange.encodeRecord(envelope));
        assertArrayEquals(envelope.readPayload(),restored.readPayload());
        var inputs=new ArrayList<>(sources);inputs.add(restored);
        var evaluated=RuleAnalyzers.evaluator(m,r).analyze(state,inputs,Map.of()).getFirst();
        assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,evaluated.status(),"candidate must not self-qualify");
        assertEquals(report,JSON.readTree(evaluated.measurements().get("payload")));
        assertArrayEquals(before,SystemStateView.bytes(state.snapshot()));
        for(int i=0;i<sources.size();i++)assertArrayEquals(preserved.get(i),exchange.encodeRecord(sources.get(i)));
        return report;
    }
    static JsonNode run(Fixture f) throws Exception { var state=system(List.of(f.graph()),true,false);return run(state,sources(state,coverage(state,f))); }
    static Fixture molecule(String name) throws Exception {
        return switch(name) {
            case "acetate" -> fixture("carboxylate");
            case "formate" -> graph("C O O", "0=1 0-2", "1 0 0", Map.of(2,-1), Set.of());
            case "acetateSwapped" -> graph("C C O O", "0-1 1-2 1=3", "3 0 0 0", Map.of(2,-1), Set.of());
            case "zwitterion" -> graph("N C C O O", "0-1 1-2 2=3 2-4", "3 2 0 0 0", Map.of(0,1,4,-1), Set.of());
            case "primary" -> graph("C N", "0-1", "3 3", Map.of(1,1), Set.of());
            case "secondary" -> graph("C N C", "0-1 1-2", "3 2 3", Map.of(1,1), Set.of());
            case "tertiary" -> graph("C N C C", "0-1 1-2 1-3", "3 1 3 3", Map.of(1,1), Set.of());
            case "quaternary" -> graph("C N C C C", "0-1 1-2 1-3 1-4", "3 0 3 3 3", Map.of(1,1), Set.of());
            case "nitro" -> ChargeNonpolarAcceptanceTest.molecule("nitrobenzene");
            default -> fixture(name);
        };
    }
    static void invariant(Fixture f,JsonNode r) {
        for(var g:r.get("groups"))for(var alt:g.get("correspondenceAlternatives")) {
            var ids=new HashSet<String>();int total=0;
            for(var member:alt.get("members")) {
                String id=member.get("chemicalAtomId").asText();assertTrue(ids.add(id));
                int charge=f.graph().atom(id).orElseThrow().formalCharge();
                assertEquals(charge,member.get("formalCharge").intValue());total=Math.addExact(total,charge);
            }
            assertEquals(total,alt.get("totalFormalCharge").intValue());
            assertFalse(alt.has("centroid"));assertFalse(alt.has("chargeCenter"));
        }
    }
    @TestFactory Stream<DynamicTest> completeMatrix() {
        return Stream.of("acetate","formate","acetateSwapped","primary","secondary","tertiary","quaternary","zwitterion","acid","ester","amide","amine","nitro")
            .flatMap(name->Stream.of("normal","explicit","double","permuted").map(kind->DynamicTest.dynamicTest(name+"/"+kind,()->{
                var f=molecule(name);if(kind.equals("explicit"))f=explicit(f);if(kind.equals("double"))f=doubled(f);
                if(kind.equals("permuted")){var a=new ArrayList<>(f.graph().atoms());var b=new ArrayList<>(f.graph().bonds());Collections.reverse(a);Collections.reverse(b);f=new Fixture(new MolecularGraph(a,b,f.graph().properties()),f.hydrogens());}
                var r=run(f);invariant(f,r);int count=Set.of("acid","ester","amide","amine","nitro").contains(name)?0:name.equals("zwitterion")?2:1;
                assertEquals(count*(kind.equals("double")?2:1),r.get("groups").size());
                assertEquals(!name.equals("formate"),r.path("coverage").path("completeEligibleGroupEnumeration").asBoolean());
                if(name.equals("formate"))assertTrue(r.path("coverage").path("reasons").toString().contains("ATHENA.GROUP.CARBOXYLATE"));
                assertEquals(count==0?"ABSENT_FALSE":"SUPPORTED_PRESENT",r.path("coverage").path("assessment").asText());
            })));
    }
    @Test void zeroComponentChargePreservesOppositeGroups() throws Exception {
        var f=molecule("zwitterion");assertEquals(0,f.graph().atoms().stream().mapToInt(MolecularGraph.Atom::formalCharge).sum());
        var validator=totah.lab.athena.design.backend.ocl.OclMolecularBackend.forChemicalStateValidation();
        for(var policy:totah.lab.athena.design.backend.MolecularValidationService.NeutralityPolicy.values()) {
            var result=validator.validateDimensions(f.graph(),policy);assertEquals(0,result.netFormalCharge());
            assertEquals(2,run(f).get("groups").size());
        }
        var totals=new TreeSet<Integer>();for(var g:run(f).get("groups"))totals.add(g.get("correspondenceAlternatives").get(0).get("totalFormalCharge").intValue());
        assertEquals(Set.of(-1,1),totals);
    }
    @Test void neutralityFailureDoesNotSuppressSuppliedCation() throws Exception {
        var f=molecule("primary");
        var r=totah.lab.athena.design.backend.ocl.OclMolecularBackend.forChemicalStateValidation().validateDimensions(f.graph(),
                totah.lab.athena.design.backend.MolecularValidationService.NeutralityPolicy.REQUIRE_COMPONENT_NEUTRAL);
        assertEquals(EvidenceInterpretation.Status.ABSENT_FALSE,r.dimensions().get(totah.lab.athena.design.backend.MolecularValidationService.Dimension.NET_NEUTRALITY).status());
        assertEquals(1,run(f).get("groups").size());
    }
    @Test void permutationKeepsExactMembershipAndCharge() throws Exception {
        var f=molecule("zwitterion");var a=new ArrayList<>(f.graph().atoms());var b=new ArrayList<>(f.graph().bonds());Collections.reverse(a);Collections.reverse(b);
        var original=run(f);var permuted=run(new Fixture(new MolecularGraph(a,b,f.graph().properties()),f.hydrogens()));
        var left=new TreeSet<String>();var right=new TreeSet<String>();
        original.get("groups").forEach(g->left.add(g.get("correspondenceAlternatives").toString()));
        permuted.get("groups").forEach(g->right.add(g.get("correspondenceAlternatives").toString()));
        assertEquals(left,right);assertNotEquals(original.get("stateBinding"),permuted.get("stateBinding"));
    }
    @Test void carboxylateKeepsThreeChemicalMembersAndDepiction() throws Exception {
        var a=run(molecule("acetate"));var b=run(molecule("acetateSwapped"));
        assertEquals(3,a.get("groups").get(0).get("correspondenceAlternatives").get(0).get("members").size());
        assertNotEquals(a.get("stateBinding"),b.get("stateBinding"));
        assertEquals(-1,b.get("groups").get(0).get("correspondenceAlternatives").get(0).get("totalFormalCharge").intValue());
    }
    @TestFactory Stream<DynamicTest> incompleteMappingAndState() {
        return Stream.of("unknownH","unknownCharge","contradictoryH","contradictoryCharge","missingMap","ambiguousMap","incomplete").map(kind->DynamicTest.dynamicTest(kind,()->{
            var f=molecule("primary");var original=system(List.of(f.graph()),true,false);var s=original;
            if(kind.endsWith("Map")) {
                var c=original.components().getFirst();var maps=kind.equals("missingMap")?List.<Map<String,totah.lab.gaia.structure.AtomReference>>of():List.of(c.correspondenceAlternatives().getFirst(),c.correspondenceAlternatives().getFirst());
                s=new SystemStateView(original.identity(),original.graph(),List.of(new SystemStateView.Component(c.identity(),c.chemistry(),maps,c.limitations())),original.sources(),original.cofactors(),original.charges(),true,false,original.limitations());
            }
            var c=coverage(s,f);var n=(ObjectNode)c.get("atomState").get("a1");
            if(kind.equals("unknownH")){n.put("hydrogenMode","UNKNOWN");n.putNull("implicitHydrogenCount");}
            if(kind.equals("unknownCharge"))n.put("chargeStatus","UNKNOWN_INCONCLUSIVE");
            if(kind.equals("contradictoryH"))n.put("implicitHydrogenCount",2);
            if(kind.equals("contradictoryCharge"))n.put("formalCharge",0);
            if(kind.equals("incomplete"))c.put("completeGraph","UNKNOWN_INCONCLUSIVE");
            var r=run(s,sources(s,c));assertTrue(r.get("groups").isEmpty());assertEquals("UNKNOWN_INCONCLUSIVE",r.path("coverage").path("assessment").asText());
        }));
    }
    @Test void missingReportAndInputOrder() throws Exception {
        var f=molecule("primary");var s=system(List.of(f.graph()),true,false);var inputs=sources(s,coverage(s,f));var baseline=run(s,inputs);
        var reversed=new ArrayList<>(inputs);Collections.reverse(reversed);assertEquals(baseline,run(s,reversed));
        var partial=run(s,inputs.subList(2,inputs.size()));assertEquals(1,partial.get("groups").size());assertFalse(partial.path("coverage").path("completeEligibleGroupEnumeration").asBoolean());
        assertEquals("UNKNOWN_INCONCLUSIVE",run(s,List.of()).path("coverage").path("assessment").asText());
    }
    @Test void suppliedTotalCannotOverrideRecomputedTotal() throws Exception {
        var f=molecule("acetate");var s=system(List.of(f.graph()),true,false);var inputs=sources(s,coverage(s,f));var report=run(s,inputs);
        ((ObjectNode)report.get("groups").get(0).get("correspondenceAlternatives").get(0)).put("totalFormalCharge",77);
        var m=model();var all=new ArrayList<>(inputs);all.add(wrap(s,"athena:charge-groups",report,RuleAnalyzers.collector(m,request(s,m)).method(),"tampered"));
        assertThrows(Exception.class,()->RuleAnalyzers.evaluator(m,request(s,m)).analyze(s,all,Map.of()));
    }
    @Test void sourceTamperingPreservedAndFailsClosed() throws Exception {
        var f=molecule("acetate");var s=system(List.of(f.graph()),true,false);var inputs=sources(s,coverage(s,f));var e=inputs.getFirst();var m=model();
        for(String kind:List.of("definition","state","members")) {
            var p=(ObjectNode)JSON.readTree(e.readPayload());
            if(kind.equals("definition"))p.put("definitionDigest","0".repeat(64));
            if(kind.equals("state"))((ObjectNode)p.get("sourceStateBinding")).put("stateSha256","0".repeat(64));
            if(kind.equals("members"))((ObjectNode)p.get("occurrences").get(0)).putArray("memberAtomIds").add("a2").add("a3");
            var bad=wrap(s,"athena:group-identities",p,e.method(),"bad");var bytes=new EvidenceExchange().encodeRecord(bad);var changed=new ArrayList<>(inputs);changed.set(0,bad);
            assertThrows(Exception.class,()->RuleAnalyzers.collector(m,request(s,m)).analyze(s,changed,Map.of()));assertArrayEquals(bytes,new EvidenceExchange().encodeRecord(bad));
        }
        var artifact=temp.resolve("source.json");Files.write(artifact,e.readPayload());
        var bad=new EvidenceEnvelope(e.reference(),e.evidenceType(),e.payloadFormat(),e.payloadVersion(),Optional.empty(),Optional.of(artifact.toString()),"0".repeat(64),e.provenance(),e.method(),e.context(),e.subjects(),e.qualifications(),e.limitations(),e.recordedAt());
        var changed=new ArrayList<>(inputs);changed.set(0,bad);assertThrows(java.io.IOException.class,()->RuleAnalyzers.collector(m,request(s,m)).analyze(s,changed,Map.of()));assertArrayEquals(e.readPayload(),Files.readAllBytes(artifact));
    }
    public static void main(String[] args)throws Exception {
        var output=new TreeMap<String,JsonNode>();for(String f:List.of("acetate","acetateSwapped","formate","primary","secondary","tertiary","quaternary","zwitterion","nitro"))output.put(f,run(molecule(f)));
        Files.write(Path.of(args[0]),SystemStateView.bytes(output));
    }
}
