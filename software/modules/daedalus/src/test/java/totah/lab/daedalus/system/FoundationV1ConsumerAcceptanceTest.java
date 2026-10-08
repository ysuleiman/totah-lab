package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.design.backend.MolecularBackendException;
import totah.lab.athena.design.backend.ocl.OclMolecularBackend;
import totah.lab.athena.interaction.perception.FormalChargeAssignments;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.gaia.graph.ResidueGraph;
import totah.lab.gaia.structure.*;
import totah.lab.hermes.file.pdbqt.PdbqtGaiaMapper;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.mnemosyne.ScientificReference.Kind.*;

/** Engineering consumer of frozen real inputs. No new molecular preparation or scientific authority. */
class FoundationV1ConsumerAcceptanceTest {
    static final Path FRAME=Path.of("software/modules/daedalus/src/test/resources/foundation-frozen-context");
    static final Path SDF=Path.of("software/modules/daedalus/src/test/resources/foundation-v1-consumer/parent_netarsudil.sdf");
    static final String SDF_SHA="fad4272ced0786b0be4621f00488fd5df684b6b95b8f513c89a1859ab857e09f";
    static final Instant AT=Instant.parse("2026-10-08T00:00:00Z");
    @TempDir Path output;
    static ScientificReference ref(ScientificReference.Kind kind,String id){return new ScientificReference(kind,"foundation-v1-consumer",id,"1");}
    static byte[] pinned(Path p,String expected)throws Exception{byte[] b=Files.readAllBytes(p);if(!EvidenceExchange.sha256(b).equals(expected))throw new java.io.IOException("Changed pinned input: "+p);return b;}
    static String declaredSmiles(byte[] bytes){String text=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);String header=">  <canonical_isomeric_smiles>  (1) \n";int at=text.indexOf(header);if(at<0||text.indexOf(header,at+1)>=0)throw new IllegalArgumentException("Unique exact supplied SMILES property required");return text.substring(at+header.length()).split("\n",2)[0];}
    static Map<String,Object> exercise(Path directory)throws Exception {
        Files.createDirectories(directory);var backend=new OclMolecularBackend();var json=B01FunctionalGroupAcceptanceTest.JSON;
        byte[] sdf=pinned(SDF,SDF_SHA),protein=pinned(FRAME.resolve("receptor.pdbqt"),"896c058534c2099f2e12a2356da671cfeb40e7ec9d6a0994f72a5b43371c780d"),ligand=pinned(FRAME.resolve("ligand.pdbqt"),"c4fb304d4da17590c05ccf9bea0281f50d71c4682718c40a66c3dee02f7eaa19");
        String smiles=declaredSmiles(sdf);var chemistry=backend.decodeStructure("SMILES",smiles);var identity=backend.identify(chemistry);
        var matches=new ArrayList<Map<String,Object>>();
        for(String entry:List.of("groups-b01/ATHENA.GROUP.ESTER","groups-b01/ATHENA.GROUP.AMIDE","groups-foundation-v1/ATHENA.GROUP.THIOETHER")){
            var path=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/"+entry+".rule.json");byte[] definition=Files.readAllBytes(path);var m=RuleRegistry.decode(definition);String query=json.readTree(m.parameters().get("definition").value()).get("query").asText();var found=backend.match(query,chemistry);
            matches.add(Map.of("definition",m.key(),"manifestSha256",EvidenceExchange.sha256(definition),"query",query,"mappings",found.queryToTargetAtomIds(),"backendEvidence",found.evidence(),"occurrenceState",found.queryToTargetAtomIds().isEmpty()?"ABSENT_FALSE":"SUPPORTED_PRESENT","scope","Exhaustive B00 query occurrence in the supplied SMILES graph only; not governed group eligibility or pose chemistry"));
        }
        assertFalse(((List<?>)matches.getFirst().get("mappings")).isEmpty());assertFalse(((List<?>)matches.get(1).get("mappings")).isEmpty());assertTrue(((List<?>)matches.get(2).get("mappings")).isEmpty());
        var reader=new PdbqtReader();var receptor=PdbqtGaiaMapper.toStructure(reader.read(FRAME.resolve("receptor.pdbqt")));var placed=PdbqtGaiaMapper.toStructure(reader.read(FRAME.resolve("ligand.pdbqt")));var ligandResidues=placed.getChains().stream().flatMap(c->c.residues().stream()).toList();
        var chains=new ArrayList<>(receptor.getChains());chains.add(new Chain("FROZEN_LIGAND",ligandResidues));var graph=ResidueGraph.from(new Structure(chains));
        var sources=List.of(ref(SOURCE,EvidenceExchange.sha256(protein)),ref(SOURCE,EvidenceExchange.sha256(ligand)));
        var state=new SystemStateView(ref(CONTEXT,"frozen-1158590-coordinate-state"),graph,List.of(),sources,Set.of(),FormalChargeAssignments.EMPTY,true,false,List.of("Prepared frozen coordinates; no experimental-coordinate relabeling","SMILES identity is separate: source-graph-to-pose chemistry correspondence is not asserted","No METTL7 mechanistic, potency or binding interpretation"));
        byte[] before=SystemStateView.bytes(state.snapshot());var selected=state.atoms().keySet().stream().filter(a->a.chainId().equals("FROZEN_LIGAND")).sorted().limit(2).toList();assertEquals(2,selected.size());
        var plan=ContinuousGeometryAcceptanceTest.plan(state);plan.putArray("limitations").add("Two explicitly selected original pose atom references; raw distance only");ContinuousGeometryAcceptanceTest.op(plan,"DISTANCE",selected.toArray(AtomReference[]::new));
        var m=ContinuousGeometryAcceptanceTest.manifest();var request=ContinuousGeometryAcceptanceTest.request(state,m,plan,10000,100);var activity=ref(ACTIVITY,"consumer");
        var evidence=new ArrayList<EvidenceEnvelope>();
        evidence.add(SystemQualificationPipeline.envelope(activity,"protein","source:pdbqt",protein,ref(METHOD,"pinned-source"),state.subject(),AT,List.of("Exact original bytes")));
        evidence.add(SystemQualificationPipeline.envelope(activity,"ligand","source:pdbqt",ligand,ref(METHOD,"pinned-source"),state.subject(),AT,List.of("Exact original bytes")));
        evidence.add(SystemQualificationPipeline.envelope(activity,"source-smiles-artifact","source:sdf",sdf,ref(METHOD,"pinned-source"),state.subject(),AT,List.of("Source artifact contains a supplied SMILES property; no SDF graph import or pose mapping claimed")));
        evidence.add(SystemQualificationPipeline.envelope(activity,"geometry-plan","athena:continuous-geometry-plan",SystemStateView.bytes(plan),ref(METHOD,"explicit-selection"),state.subject(),AT,List.of()));
        var collector=RuleAnalyzers.collector(m,request);var raw=collector.analyze(state,evidence,Map.of()).getFirst();JsonNode report=json.readTree(raw.measurements().get("payload"));
        evidence.add(SystemQualificationPipeline.envelope(activity,"geometry","athena:rule-measurements",SystemStateView.bytes(report),collector.method(),state.subject(),AT,List.of("Raw source geometry only")));
        var current=RuleAnalyzers.evaluator(m,request).analyze(state,evidence,Map.of()).getFirst();assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,current.status());
        Path catalogPath=directory.resolve("catalog");Files.createDirectories(catalogPath);var catalog=new EvidenceSnapshotCatalog(catalogPath);var pipeline=new SystemQualificationPipeline(new SystemGraphValidation(backend,backend,backend));
        var published=pipeline.run(catalog,Optional.empty(),state,evidence,Map.of(),List.of(),activity,AT);var history=catalog.read(published.catalogSnapshot()).orElseThrow().history();
        for(var e:evidence)assertArrayEquals(e.readPayload(),history.envelopes().get(e.reference()).readPayload());assertArrayEquals(before,SystemStateView.bytes(state.snapshot()));
        String unsupported;try{backend.decodeStructure("SDF",new String(sdf,java.nio.charset.StandardCharsets.UTF_8));throw new AssertionError("Unapproved SDF decoder unexpectedly accepted");}catch(MolecularBackendException expected){unsupported=expected.getMessage();}
        var result=new TreeMap<String,Object>();result.put("scope","Engineering-only Foundation v1 consumer; frozen METTL7B parent pose1158590; no new science or preparation");result.put("sourceSha256",Map.of("sdf",SDF_SHA,"receptor",EvidenceExchange.sha256(protein),"pose",EvidenceExchange.sha256(ligand)));result.put("declaredSmiles",smiles);result.put("sourceIdentity",identity);result.put("sourceGraphSha256",SystemStateView.digest(chemistry));result.put("sourceQueryOccurrences",matches);result.put("coordinateState",state.binding());result.put("selectedAtoms",selected);result.put("rawGeometry",report);result.put("scientificEvaluation",current);result.put("sourceChemistryToPose",Map.of("status","UNKNOWN_INCONCLUSIVE","reason","No independently qualified source chemistry/pose correspondence supplied"));result.put("unsupportedSourceImport",Map.of("status","UNSUPPORTED","format","SDF","reason",unsupported));result.put("catalogSnapshot",published.catalogSnapshot());result.put("certificate",published.certificate());result.put("productionScientificReceiptsIssued",0);
        Files.write(directory.resolve("consumer.json"),SystemStateView.bytes(result));Files.write(directory.resolve("state.json"),before);Files.write(directory.resolve("geometry-plan.json"),SystemStateView.bytes(plan));return result;
    }
    @Test void realSourceConsumerPreservesScopeAndReadback()throws Exception {var r=exercise(output);assertEquals(0,r.get("productionScientificReceiptsIssued"));assertTrue(Files.size(output.resolve("consumer.json"))>0);}
    @Test void sourceTamperingFailsBeforeUse()throws Exception {var p=output.resolve("changed.sdf");Files.writeString(p,"different source");assertThrows(java.io.IOException.class,()->pinned(p,SDF_SHA));}
    @Test void missingDeclaredChemicalSourceIsNotReconstructed(){assertThrows(IllegalArgumentException.class,()->declaredSmiles("no property".getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
    public static void main(String[] args)throws Exception {Files.write(Path.of(args[1]),SystemStateView.bytes(exercise(Path.of(args[0]))));}
}
