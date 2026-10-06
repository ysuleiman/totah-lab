package totah.lab.daedalus.system;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.athena.interaction.perception.FormalChargeAssignments;
import totah.lab.athena.design.backend.ocl.OclMolecularBackend;
import totah.lab.gaia.chemistry.*;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.graph.ResidueGraph;
import totah.lab.gaia.structure.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.mnemosyne.ScientificReference.Kind.*;

public class RuleRegistryAcceptanceTest {
    @TempDir Path temp;
    static final Instant T=Instant.parse("2026-10-04T00:00:00Z");
    static ScientificReference ref(ScientificReference.Kind k,String id){return new ScientificReference(k,"rule-tests",id,"1");}
    static SystemStateView state(boolean hydrogensKnown) {
        var n=Atom.builder().name("N").element(Element.N).autoDockType("N").position(new Point3D(0,0,0)).build();
        var h=Atom.builder().name("H").element(Element.H).autoDockType("HD").position(new Point3D(1,0,0)).build();
        var o=Atom.builder().name("O").element(Element.O).autoDockType("OA").position(new Point3D(3.8,0,0)).build();
        var structure=new Structure(List.of(new Chain("A",List.of(new Residue("DON",1,List.of(n,h)),new Residue("ACC",2,List.of(o))))),List.of(new Bond(atom(1,"N"),atom(1,"H"),BondOrder.SINGLE)),ConnectivityProvenance.EXPLICIT);
        return new SystemStateView(ref(CONTEXT,"state"),ResidueGraph.from(structure),List.of(),List.of(ref(SOURCE,"synthetic")),Set.of(),new FormalChargeAssignments(Map.of(atom(1,"N"),0,atom(1,"H"),0,atom(2,"O"),0)),true,hydrogensKnown,List.of("synthetic prepared fixture only"));
    }
    static AtomReference atom(int r,String a){return new AtomReference("A",r,' ',a);}
    static SystemQualificationPipeline pipeline(){var o=new OclMolecularBackend();return new SystemQualificationPipeline(new SystemGraphValidation(o,o,o));}
    static RuleManifest manifest(RuleRegistry r,String profile){return r.manifests().values().stream().filter(m->m.profile().equals(profile)&&m.ruleId().equals("INT.HBOND.001")).findFirst().orElseThrow();}
    static RuleRequest request(RuleManifest m,SystemStateView s){return new RuleRequest(s.binding(),m.key(),RuleRegistry.digest(m),List.of(atom(1,"N"),atom(2,"O")),List.of(new ResidueId("A",1,null)),List.of(new ResidueId("A",2,null)),4.5,2,100,100);}
    SystemQualificationPipeline.Published foundation(SystemStateView s)throws Exception{return pipeline().run(new EvidenceSnapshotCatalog(temp),Optional.empty(),s,List.of(),Map.of(),List.of(),ref(ACTIVITY,"foundation"),T);}
    RuleExecutionPipeline.Result run(RuleRegistry registry,SystemQualificationPipeline.Published base,RuleManifest m,String id,Optional<EvidenceEnvelope> reuse)throws Exception {
        return new RuleExecutionPipeline(pipeline()).run(new EvidenceSnapshotCatalog(temp),base,Map.of(),registry,SystemStateView.bytes(m),request(m,base.state()),reuse,ref(ACTIVITY,id),T);
    }
    List<EvidenceInterpretation> current(RuleExecutionPipeline.Result result)throws Exception {
        var history=new EvidenceSnapshotCatalog(temp).read(result.published().catalogSnapshot()).orElseThrow().history();
        return result.published().certificate().interpretations().stream().map(history.interpretations()::get).toList();
    }
    static RuleManifest change(RuleManifest m,String version,boolean retired,Map<String,RuleManifest.Parameter> parameters){return new RuleManifest(m.schema(),m.ruleId(),version,m.profile(),m.family(),m.tier(),m.implementationId(),m.implementationVersion(),m.qualification(),retired,m.requiredCapabilities(),m.requiredChemistry(),m.requiredGeometry(),m.measurementsProduced(),m.classificationStates(),parameters,m.scientificSources(),m.referenceArtifacts(),m.limitations());}
    @Test void completeEightFamilyReferenceRegistryNeverClaimsPlipParity()throws Exception {
        var r=RuleRegistry.bundled();assertEquals(26,r.manifests().size());
        var external=r.manifests().values().stream().filter(m->m.implementationId().equals("external.plip")).toList();assertEquals(8,external.size());
        assertTrue(external.stream().allMatch(m->m.qualification()==SystemGraphCertificate.Status.UNSUPPORTED));
        assertTrue(external.stream().anyMatch(m->m.profile().endsWith("WATER_BRIDGE")));assertTrue(external.stream().anyMatch(m->m.profile().endsWith("METAL_COMPLEX")));
    }
    @Test void strictSchemaRejectsUnknownMissingDuplicateAndInvalidUnits()throws Exception {
        var m=manifest(RuleRegistry.bundled(),"ATHENA_NATIVE_HBOND");String json=new String(SystemStateView.bytes(m));
        for(var bad:List.of(json.replace("\"schema\":\"athena-rule/1\"","\"schema\":\"future/9\""),json.replaceFirst("\\{","{\"unexplained\":true,"),json.replace("\"angstrom\"","\"meter\""),json.replace("\"retired\":false,",""),json.replaceFirst("\\{","{\"ruleId\":\"duplicate\",")))
            assertThrows(java.io.IOException.class,()->RuleRegistry.decode(bad.getBytes()));
    }
    @Test void identityVersionDigestConflictNeverMutatesRegistry()throws Exception {
        var r=RuleRegistry.bundled();var m=manifest(r,"ATHENA_NATIVE_HBOND");var p=new TreeMap<>(m.parameters());p.put("donorAcceptorCutoff",new RuleManifest.Parameter("4.0","angstrom","changed threshold"));
        assertThrows(IllegalArgumentException.class,()->r.register(change(m,m.version(),false,p)));
        assertThrows(IllegalArgumentException.class,()->r.require(m.key(),"0".repeat(64)));
        assertEquals(m,r.require(m.key(),RuleRegistry.digest(m)));
    }
    @Test void sameMeasurementsProduceDifferentNativeAndReferenceThresholdAssessments()throws Exception {
        var registry=RuleRegistry.bundled();var base=foundation(state(true));var nativeM=manifest(registry,"ATHENA_NATIVE_HBOND");var refM=manifest(registry,"ATHENA_PLIP_3_0_1_THRESHOLDS_HBOND");
        var a=run(registry,base,nativeM,"native",Optional.empty());var b=run(registry,base,refM,"ref",a.measurements());
        assertEquals(a.measurements(),b.measurements());
        assertTrue(current(a).stream().anyMatch(i->i.status()==EvidenceInterpretation.Status.ABSENT_FALSE));
        assertTrue(current(b).stream().anyMatch(i->i.evaluator().id().endsWith("/evaluate")&&i.status()==EvidenceInterpretation.Status.SUPPORTED_PRESENT));
        var raw=new String(a.measurements().orElseThrow().readPayload());assertTrue(raw.contains("3.8"));assertTrue(raw.contains("hydrogenDistance"));
    }
    @Test void missingHydrogenKnowledgeIsNeverFalse()throws Exception {
        var registry=RuleRegistry.bundled();var base=foundation(state(false));var result=run(registry,base,manifest(registry,"ATHENA_NATIVE_HBOND"),"missing-h",Optional.empty());
        assertTrue(result.measurements().isPresent());assertTrue(current(result).stream().anyMatch(i->i.status()==EvidenceInterpretation.Status.NOT_EVALUATED));
        assertTrue(current(result).stream().noneMatch(i->i.status()==EvidenceInterpretation.Status.ABSENT_FALSE));
    }
    @Test void versionChangeRetirementAndReplayPreserveOriginalMeasurements()throws Exception {
        var r=RuleRegistry.bundled();var m=manifest(r,"ATHENA_NATIVE_HBOND");var base=foundation(state(true));var a=run(r,base,m,"v1",Optional.empty());
        var old=new EvidenceExchange().encode(new EvidenceSnapshotCatalog(temp).read(a.published().catalogSnapshot()).orElseThrow());
        var p=new TreeMap<>(m.parameters());p.put("donorAcceptorCutoff",new RuleManifest.Parameter("4.1","angstrom","versioned test threshold"));p.put("hydrogenAcceptorCutoff",new RuleManifest.Parameter("UNGATED","angstrom","versioned ungated control"));
        var v2=change(m,"2",false,p);r=r.register(v2);var b=run(r,base,v2,"v2",a.measurements());assertNotEquals(a.published().certificate(),b.published().certificate());
        var retired=change(v2,"3",true,p);r=r.register(retired);var c=run(r,base,retired,"retired",a.measurements());
        assertTrue(current(c).stream().anyMatch(i->i.status()==EvidenceInterpretation.Status.NOT_EVALUATED));
        assertArrayEquals(old,new EvidenceExchange().encode(new EvidenceSnapshotCatalog(temp).read(a.published().catalogSnapshot()).orElseThrow()));
        assertEquals(a.published().certificate(),run(r,base,m,"v1",Optional.empty()).published().certificate());
    }
    @Test void invalidManifestAndCollectorFailureRetainInputEvidence()throws Exception {
        var r=RuleRegistry.bundled();var m=manifest(r,"ATHENA_NATIVE_HBOND");var base=foundation(state(true));
        var failed=new RuleExecutionPipeline(pipeline()).run(new EvidenceSnapshotCatalog(temp),base,Map.of(),r,"corrupt source".getBytes(),request(m,base.state()),Optional.empty(),ref(ACTIVITY,"bad-json"),T);
        assertTrue(current(failed).stream().anyMatch(i->i.status()==EvidenceInterpretation.Status.FAILED));
        var h=new EvidenceSnapshotCatalog(temp).read(failed.published().catalogSnapshot()).orElseThrow().history();
        assertTrue(h.envelopes().values().stream().anyMatch(e->e.reference().id().equals("bad-json/manifest")));
        var req=new RuleRequest(base.state().binding(),m.key(),RuleRegistry.digest(m),List.of(),List.of(),List.of(),4.5,2,100,100);
        var bad=new RuleExecutionPipeline(pipeline()).run(new EvidenceSnapshotCatalog(temp),base,Map.of(),r,SystemStateView.bytes(m),req,Optional.empty(),ref(ACTIVITY,"bad-partners"),T);
        assertTrue(current(bad).stream().anyMatch(i->i.status()==EvidenceInterpretation.Status.FAILED));assertTrue(bad.measurements().isEmpty());
    }
    @Test void unsupportedExternalRuleRetainsSubjectsAndNoApproximation()throws Exception {
        var r=RuleRegistry.bundled();var m=r.manifests().values().stream().filter(x->x.profile().equals("PLIP_3_0_1_WATER_BRIDGE")).findFirst().orElseThrow();
        var result=run(r,foundation(state(true)),m,"water",Optional.empty());
        assertTrue(result.measurements().isPresent());assertTrue(current(result).stream().anyMatch(i->i.status()==EvidenceInterpretation.Status.UNSUPPORTED));
        assertTrue(new String(result.measurements().orElseThrow().readPayload()).contains("subjects"));
    }
    @Test void missingFormalChargeCoverageCannotProduceNegativeSaltFinding()throws Exception {
        var r=RuleRegistry.bundled();var m=r.manifests().values().stream().filter(x->x.profile().equals("ATHENA_NATIVE_SALT_BRIDGE")).findFirst().orElseThrow();
        var original=state(true);var partial=new SystemStateView(original.identity(),original.graph(),original.components(),original.sources(),original.cofactors(),FormalChargeAssignments.EMPTY,true,true,original.limitations());
        var result=run(r,foundation(partial),m,"unknown-charges",Optional.empty());
        assertTrue(current(result).stream().anyMatch(x->x.status()==EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE));
        assertTrue(current(result).stream().noneMatch(x->x.status()==EvidenceInterpretation.Status.ABSENT_FALSE));
    }
    @Test void unsupportedVicinalMotifKeepsAvailableMeasurementsAndUndefinedGeometry()throws Exception {
        var s=SystemQualificationAcceptanceTest.state(0,false);var base=foundation(s);var registry=RuleRegistry.bundled();
        var m=registry.manifests().values().stream().filter(x->x.ruleId().equals("SULF.VICINAL.001")).findFirst().orElseThrow();
        var req=new RuleRequest(s.binding(),m.key(),RuleRegistry.digest(m),List.of(atom(17,"SG"),atom(48,"SG")),List.of(),List.of(),4.5,2,100,100);
        var result=new RuleExecutionPipeline(pipeline()).run(new EvidenceSnapshotCatalog(temp),base,Map.of(),registry,SystemStateView.bytes(m),req,Optional.empty(),ref(ACTIVITY,"sulfur"),T);
        assertTrue(current(result).stream().anyMatch(x->x.status()==EvidenceInterpretation.Status.UNSUPPORTED));
        var raw=new String(result.measurements().orElseThrow().readPayload());
        assertTrue(raw.contains("SG_SG_distanceAngstrom"));assertTrue(raw.contains("UNAVAILABLE"));assertTrue(raw.contains("UNKNOWN"));
    }
    @Test void candidateBudgetAndTraversalBoundsNeverAssertFalse()throws Exception {
        var s=SystemQualificationAcceptanceTest.state(0,false);var base=foundation(s);var r=RuleRegistry.bundled();
        var m=r.manifests().values().stream().filter(x->x.ruleId().equals("GEO.PATH.001")).findFirst().orElseThrow();
        var request=new RuleRequest(s.binding(),m.key(),RuleRegistry.digest(m),List.of(atom(1,"C1")),List.of(),List.of(),3,1,1,1);
        var result=new RuleExecutionPipeline(pipeline()).run(new EvidenceSnapshotCatalog(temp),base,Map.of(),r,SystemStateView.bytes(m),request,Optional.empty(),ref(ACTIVITY,"budget"),T);
        assertTrue(current(result).stream().anyMatch(x->x.status()==EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE));
        assertTrue(new String(result.measurements().orElseThrow().readPayload()).contains("incomplete coverage"));
    }
    @Test void corruptEvaluationInputProducesFailedWithoutDeletingRawPayload()throws Exception {
        var r=RuleRegistry.bundled();var base=foundation(state(true));var m=manifest(r,"ATHENA_NATIVE_HBOND");
        var corrupt=SystemQualificationPipeline.envelope(ref(ACTIVITY,"original"),"bad","athena:rule-measurements",new byte[]{0,1,2},ref(METHOD,"unreadable"),base.state().subject(),T,List.of("synthetic corrupted transport"));
        var result=run(r,base,m,"failed-evaluation",Optional.of(corrupt));
        assertTrue(current(result).stream().anyMatch(x->x.status()==EvidenceInterpretation.Status.FAILED));
        assertArrayEquals(new byte[]{0,1,2},result.measurements().orElseThrow().readPayload());
        assertTrue(new EvidenceSnapshotCatalog(temp).read(result.published().catalogSnapshot()).orElseThrow().history().envelopes().containsKey(corrupt.reference()));
    }
    @Test void existingOclMatcherSuppliesStableAtomMappingWithoutAnotherSmartsEngine()throws Exception {
        var backend=new OclMolecularBackend();var graph=backend.decodeStructure("SMILES","CCO");var result=backend.match("[#8]",graph);
        assertEquals(1,result.queryToTargetAtomIds().size());assertTrue(result.queryToTargetAtomIds().getFirst().values().stream().allMatch(id->graph.atom(id).orElseThrow().element().equals("O")));
        assertEquals("OPEN_CHEM_LIB",result.evidence().backend());
    }
    @Test void everyExternalFamilyPreservesEvidenceAsUnsupported()throws Exception {
        var registry=RuleRegistry.bundled();var base=foundation(state(true));
        for(var m:registry.manifests().values())if(m.implementationId().equals("external.plip")) {
            var result=run(registry,base,m,"external-"+m.ruleId(),Optional.empty());
            assertTrue(result.measurements().isPresent());
            assertTrue(current(result).stream().anyMatch(i->i.status()==EvidenceInterpretation.Status.UNSUPPORTED));
            assertTrue(current(result).stream().noneMatch(i->i.status()==EvidenceInterpretation.Status.ABSENT_FALSE));
        }
    }
    @Test void actualCandidateBudgetPreservesPartialGeometryWithoutNegativeFinding()throws Exception {
        var original=state(true);var oxygen=Atom.builder().name("O2").element(Element.O).autoDockType("OA").position(new Point3D(4,1,0)).build();
        var residues=new ArrayList<>(original.graph().structure().getChains().getFirst().residues());
        residues.set(1,new Residue("ACC",2,List.of(residues.get(1).getAtoms().getFirst(),oxygen)));
        var structure=new Structure(List.of(new Chain("A",residues)),original.graph().structure().bonds(),ConnectivityProvenance.EXPLICIT);
        var s=new SystemStateView(original.identity(),ResidueGraph.from(structure),List.of(),original.sources(),Set.of(),original.charges(),true,true,List.of());
        var registry=RuleRegistry.bundled();var m=manifest(registry,"ATHENA_NATIVE_HBOND");var base=foundation(s);
        var originalRequest=request(m,s);var req=new RuleRequest(s.binding(),m.key(),RuleRegistry.digest(m),originalRequest.atoms(),originalRequest.first(),originalRequest.second(),4.5,2,100,1);
        var result=new RuleExecutionPipeline(pipeline()).run(new EvidenceSnapshotCatalog(temp),base,Map.of(),registry,SystemStateView.bytes(m),req,Optional.empty(),ref(ACTIVITY,"candidate-budget"),T);
        assertTrue(new String(result.measurements().orElseThrow().readPayload()).contains("candidate budget exhausted"));
        assertTrue(current(result).stream().anyMatch(i->i.status()==EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE));
        assertTrue(current(result).stream().noneMatch(i->i.status()==EvidenceInterpretation.Status.ABSENT_FALSE));
    }
    @Test void sourceSulfurBondIsNotInferredFromDistance()throws Exception {
        var raw=SystemQualificationAcceptanceTest.state(0,false);
        var structure=new Structure(raw.graph().structure().getChains(),List.of(new Bond(atom(17,"SG"),atom(48,"SG"),BondOrder.SINGLE)),ConnectivityProvenance.EXPLICIT);
        var s=new SystemStateView(raw.identity(),ResidueGraph.from(structure),raw.components(),raw.sources(),Set.of(),raw.charges(),true,false,List.of());
        var registry=RuleRegistry.bundled();var m=registry.manifests().values().stream().filter(x->x.ruleId().equals("SULF.SS.001")).findFirst().orElseThrow();
        var base=foundation(s);var req=new RuleRequest(s.binding(),m.key(),RuleRegistry.digest(m),List.of(atom(17,"SG"),atom(48,"SG")),List.of(),List.of(),4.5,2,100,100);
        var result=new RuleExecutionPipeline(pipeline()).run(new EvidenceSnapshotCatalog(temp),base,Map.of(),registry,SystemStateView.bytes(m),req,Optional.empty(),ref(ACTIVITY,"source-bond"),T);
        assertTrue(current(result).stream().anyMatch(i->i.evaluator().id().endsWith("/evaluate")&&i.status()==EvidenceInterpretation.Status.SUPPORTED_PRESENT));
        assertTrue(new String(result.measurements().orElseThrow().readPayload()).contains("sourceBondListed"));
    }
    @Test void metadataCannotRelabelARegisteredImplementation()throws Exception {
        var m=manifest(RuleRegistry.bundled(),"ATHENA_NATIVE_HBOND");var bytes=new String(SystemStateView.bytes(m));
        assertThrows(java.io.IOException.class,()->RuleRegistry.decode(bytes.replace("ATHENA_NATIVE_HBOND","PLIP_3_0_1_HBOND").getBytes()));
        assertThrows(java.io.IOException.class,()->RuleRegistry.decode(bytes.replace("\"family\":\"INTERACTION\"","\"family\":\"MOTIF\"").getBytes()));
        assertThrows(java.io.IOException.class,()->RuleRegistry.decode(bytes.replace("\"SUPPORTED_PRESENT\"","\"SUPPORTED_PRESENT\",\"SUPPORTED_PRESENT\"").getBytes()));
    }
    @Test void geometryAndExistingClashAdaptersRunWithoutIntroducingChemicalClaims()throws Exception {
        var registry=RuleRegistry.bundled();var base=foundation(state(true));
        for(String id:List.of("GEO.PROX.001","GEO.SHELL.001","GEO.PATH.001","VAL.CLASH.001","SULF.ENV.001")) {
            var m=registry.manifests().values().stream().filter(x->x.ruleId().equals(id)).findFirst().orElseThrow();
            var result=run(registry,base,m,"geometry-"+id,Optional.empty());
            assertTrue(result.measurements().isPresent());
            assertTrue(current(result).stream().noneMatch(x->x.status()==EvidenceInterpretation.Status.FAILED));
            var expected=id.equals("VAL.CLASH.001")?EvidenceInterpretation.Status.ABSENT_FALSE:EvidenceInterpretation.Status.SUPPORTED_PRESENT;
            assertTrue(current(result).stream().anyMatch(x->x.status()==expected),id);
        }
    }
    @Test void missingRingTopologyCannotBecomeFalse()throws Exception {
        var registry=RuleRegistry.bundled();var original=state(true);
        var s=new SystemStateView(original.identity(),ResidueGraph.from(new Structure(original.graph().structure().getChains())),List.of(),original.sources(),Set.of(),original.charges(),true,true,List.of());
        var m=registry.manifests().values().stream().filter(x->x.profile().equals("ATHENA_NATIVE_PI_STACKING")).findFirst().orElseThrow();
        var result=run(registry,foundation(s),m,"missing-rings",Optional.empty());
        assertTrue(current(result).stream().anyMatch(x->x.status()==EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE));
        assertTrue(current(result).stream().noneMatch(x->x.status()==EvidenceInterpretation.Status.ABSENT_FALSE));
    }
    public static void main(String[] args)throws Exception {
        var test=new RuleRegistryAcceptanceTest();test.temp=Files.createDirectory(Path.of(args[0]));var r=RuleRegistry.bundled();var base=test.foundation(state(true));
        var result=test.run(r,base,manifest(r,"ATHENA_NATIVE_HBOND"),"replay",Optional.empty());
        Files.write(test.temp.resolve("certificate.json"),SystemStateView.bytes(result.published().certificate()),StandardOpenOption.CREATE_NEW);
    }
}
