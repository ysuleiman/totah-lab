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
import static totah.lab.daedalus.system.FoundationVocabularyAcceptanceTest.molecule;

class AromaticSystemAcceptanceTest {
    static final Path MANIFEST = Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/aromatic-systems-v1/ATHENA.PERCEPTION.AROMATIC.SYSTEM.rule.json");
    @TempDir Path temp;
    static RuleManifest model() throws Exception { return RuleRegistry.decode(Files.readAllBytes(MANIFEST)); }
    static List<RuleManifest> groupManifests() throws Exception {
        var result = new ArrayList<RuleManifest>();
        for (var n : JSON.readTree(model().parameters().get("sourceManifests").value())) result.add(RuleRegistry.decode(SystemStateView.bytes(n)));
        return result;
    }
    static EvidenceEnvelope wrap(SystemStateView state, String type, JsonNode payload, ScientificReference method, String id) {
        return SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"aromatic-fixtures"),id,type,
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
        var envelope=wrap(state,"athena:aromatic-systems",report,collector.method(),"systems");
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
    @TestFactory Stream<DynamicTest> structuralHandOracles() {
        return Stream.of("benzene:1:1:6","pyridine:1:1:6","pyrrole:1:1:5","naphthalene:2:1:10","indole:2:1:9","cyclopentane:0:0:0")
                .map(row->DynamicTest.dynamicTest(row,()->{
                    var x=row.split(":");var r=run(molecule(x[0]));
                    assertEquals(Integer.parseInt(x[1]),r.get("cycles").size());assertEquals(Integer.parseInt(x[2]),r.get("systems").size());
                    assertTrue(r.path("coverage").path("completeEligibleCycleEnumeration").asBoolean());
                    assertEquals(x[1].equals("0")?"ABSENT_FALSE":"SUPPORTED_PRESENT",r.path("coverage").path("assessment").asText());
                    if(!x[1].equals("0"))assertEquals(Integer.parseInt(x[3]),r.get("systems").get(0).get("memberAtoms").size());
                    for(var cycle:r.get("cycles")) {
                        var alt=cycle.get("correspondenceAlternatives").get(0);
                        assertEquals(alt.get("memberAtoms").size(),alt.get("sourceBondIds").size(),"context bonds are not cycle bonds");
                    }
                    if(Integer.parseInt(x[1])==2){assertEquals(1,r.get("relations").size());assertEquals("SHARED_BOND",r.get("relations").get(0).get("kind").asText());assertEquals(1,r.get("relations").get(0).get("sharedSourceBondIds").size());}
                }));
    }
    @Test void duplicateHeteroLabelsRetainBothOriginsWithoutDuplicatingCycles() throws Exception {
        var r=run(molecule("pyridine"));assertEquals(1,r.get("cycles").size());assertEquals(2,r.get("cycles").get(0).get("sourceOccurrences").size());
        assertEquals(1,r.get("cycles").get(0).get("correspondenceAlternatives").size());
    }
    @Test void disconnectedRingsRemainIndependentSystems() throws Exception {
        var r=run(doubled(molecule("benzene")));assertEquals(2,r.get("cycles").size());assertEquals(2,r.get("systems").size());assertTrue(r.get("relations").isEmpty());
    }
    @Test void missingReportIsPartialPositiveAndNeverAbsence() throws Exception {
        var f=molecule("benzene");var s=system(List.of(f.graph()),true,false);var inputs=sources(s,coverage(s,f));
        var selected=inputs.stream().filter(e->e.method().id().startsWith("ATHENA.GROUP.AROMATIC.RING6/")).toList();
        var r=run(s,selected);assertEquals(1,r.get("cycles").size());assertFalse(r.path("coverage").path("completeEligibleCycleEnumeration").asBoolean());
        assertEquals("UNKNOWN_INCONCLUSIVE",run(s,List.of()).path("coverage").path("assessment").asText());
    }
    @TestFactory Stream<DynamicTest> incompleteSourceStateNeverBecomesNegative() {
        return Stream.of("graph","charge","aromatic","missingMap","ambiguousMap").map(kind->DynamicTest.dynamicTest(kind,()->{
            var f=molecule("benzene");var original=system(List.of(f.graph()),true,false);var s=original;
            if(kind.endsWith("Map")) {
                var c=original.components().getFirst();
                var rotated=new TreeMap<>(c.correspondenceAlternatives().getFirst());
                var ids=new ArrayList<>(rotated.keySet());
                for(int i=0;i<ids.size();i++)rotated.put(ids.get(i),c.correspondenceAlternatives().getFirst().get(ids.get((i+1)%ids.size())));
                var maps=kind.equals("missingMap")?List.<Map<String,totah.lab.gaia.structure.AtomReference>>of():List.of(c.correspondenceAlternatives().getFirst(),rotated);
                s=new SystemStateView(original.identity(),original.graph(),List.of(new SystemStateView.Component(c.identity(),c.chemistry(),maps,c.limitations())),original.sources(),original.cofactors(),original.charges(),true,false,original.limitations());
            }
            var c=coverage(s,f);
            if(kind.equals("graph"))c.put("completeGraph","UNKNOWN_INCONCLUSIVE");
            if(kind.equals("charge"))c.get("atomState").forEach(a->((ObjectNode)a).put("chargeStatus","UNKNOWN_INCONCLUSIVE"));
            if(kind.equals("aromatic"))c.get("atomState").forEach(a->((ObjectNode)a).put("aromaticityModel","UNKNOWN"));
            var r=run(s,sources(s,c));assertTrue(r.get("cycles").isEmpty());assertEquals("UNKNOWN_INCONCLUSIVE",r.path("coverage").path("assessment").asText());
        }));
    }
    @Test void reportOrderAndAtomPermutationPreserveMembership() throws Exception {
        var f=molecule("indole");var s=system(List.of(f.graph()),true,false);var inputs=sources(s,coverage(s,f));var baseline=run(s,inputs);
        var reversed=new ArrayList<>(inputs);Collections.reverse(reversed);assertEquals(baseline,run(s,reversed));
        var atoms=new ArrayList<>(f.graph().atoms());var bonds=new ArrayList<>(f.graph().bonds());Collections.reverse(atoms);Collections.reverse(bonds);
        var permuted=run(new Fixture(new MolecularGraph(atoms,bonds,f.graph().properties()),f.hydrogens()));
        assertEquals(members(baseline),members(permuted));assertNotEquals(baseline.get("stateBinding"),permuted.get("stateBinding"),"exact source order remains attributed");
    }
    static List<String> members(JsonNode r) {
        var values=new ArrayList<String>();for(var c:r.get("cycles"))values.add(c.get("correspondenceAlternatives").toString());Collections.sort(values);return values;
    }
    @Test void conflictingDefinitionAndStateFailWithoutChangingInputs() throws Exception {
        var f=molecule("benzene");var s=system(List.of(f.graph()),true,false);var inputs=sources(s,coverage(s,f));var m=model();
        for(String kind:List.of("definition","state","occurrence")) {
            var e=inputs.getFirst();var p=(ObjectNode)JSON.readTree(e.readPayload());
            if(kind.equals("definition"))p.put("definitionDigest","0".repeat(64));
            if(kind.equals("state"))((ObjectNode)p.get("sourceStateBinding")).put("stateSha256","0".repeat(64));
            if(kind.equals("occurrence"))((ObjectNode)p).putArray("occurrences").addObject().put("occurrenceId","false");
            var bad=wrap(s,"athena:group-identities",p,e.method(),"bad");var bytes=new EvidenceExchange().encodeRecord(bad);
            var changed=new ArrayList<>(inputs);changed.set(0,bad);
            assertThrows(Exception.class,()->RuleAnalyzers.collector(m,request(s,m)).analyze(s,changed,Map.of()));
            assertArrayEquals(bytes,new EvidenceExchange().encodeRecord(bad));
        }
        var duplicate=new ArrayList<>(inputs);duplicate.add(inputs.getFirst());
        assertThrows(Exception.class,()->RuleAnalyzers.collector(m,request(s,m)).analyze(s,duplicate,Map.of()));
    }
    @Test void falseExternalHashFailsAndArtifactSurvives() throws Exception {
        var f=molecule("benzene");var s=system(List.of(f.graph()),true,false);var inputs=sources(s,coverage(s,f));var e=inputs.getFirst();
        var artifact=temp.resolve("source.json");Files.write(artifact,e.readPayload());
        var bad=new EvidenceEnvelope(e.reference(),e.evidenceType(),e.payloadFormat(),e.payloadVersion(),Optional.empty(),Optional.of(artifact.toString()),"0".repeat(64),e.provenance(),e.method(),e.context(),e.subjects(),e.qualifications(),e.limitations(),e.recordedAt());
        var changed=new ArrayList<>(inputs);changed.set(0,bad);var m=model();
        assertThrows(java.io.IOException.class,()->RuleAnalyzers.collector(m,request(s,m)).analyze(s,changed,Map.of()));assertArrayEquals(e.readPayload(),Files.readAllBytes(artifact));
    }
    @Test void tamperedDerivedReportFailsReplay() throws Exception {
        var f=molecule("naphthalene");var s=system(List.of(f.graph()),true,false);var inputs=sources(s,coverage(s,f));var r=(ObjectNode)run(s,inputs);r.putArray("systems");
        var m=model();var all=new ArrayList<>(inputs);all.add(wrap(s,"athena:aromatic-systems",r,RuleAnalyzers.collector(m,request(s,m)).method(),"tampered"));
        assertThrows(Exception.class,()->RuleAnalyzers.evaluator(m,request(s,m)).analyze(s,all,Map.of()));
    }
    public static void main(String[] args)throws Exception {
        var output=new TreeMap<String,JsonNode>();for(String f:List.of("benzene","pyridine","pyrrole","naphthalene","indole","cyclopentane"))output.put(f,run(molecule(f)));
        Files.write(Path.of(args[0]),SystemStateView.bytes(output));
    }
}
