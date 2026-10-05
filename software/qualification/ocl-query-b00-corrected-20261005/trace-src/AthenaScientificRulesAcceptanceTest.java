package totah.lab.daedalus.system;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.design.backend.ocl.OclMolecularBackend;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.athena.interaction.*;
import totah.lab.athena.interaction.perception.FormalChargeAssignments;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.graph.ResidueGraph;
import totah.lab.gaia.structure.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.mnemosyne.ScientificReference.Kind.*;

class AthenaScientificRulesAcceptanceTest {
    @TempDir Path temp;
    static final Instant T=Instant.parse("2026-10-04T00:00:00Z");
    static ScientificReference ref(ScientificReference.Kind kind,String id){return new ScientificReference(kind,"athena-scientific-tests",id,"1");}
    static final OclMolecularBackend OCL=new OclMolecularBackend();
    static AtomReference ref(int residue,String atom){return new AtomReference("A",residue,' ',atom);}
    static MolecularGraph.Atom atom(String id,String element,int charge,boolean aromatic,double x,double y,double z) {
        return new MolecularGraph.Atom(id,element,null,charge,0,aromatic,"UNSPECIFIED",new MolecularGraph.Coordinates(x,y,z),Map.of());
    }
    static MolecularGraph.Bond bond(String a,String b,MolecularGraph.BondOrder order){return new MolecularGraph.Bond(a+"-"+b,a,b,order,order==MolecularGraph.BondOrder.AROMATIC,"UNSPECIFIED",Map.of());}
    static MolecularGraph methane(double x){return new MolecularGraph(List.of(atom("C","C",0,false,x,0,0)),List.of(),Map.of());}
    static MolecularGraph methanol(){return new MolecularGraph(List.of(atom("O","O",0,false,0,0,0),atom("H","H",0,false,1,0,0),atom("C","C",0,false,-1.4,0,0)),List.of(bond("O","H",MolecularGraph.BondOrder.SINGLE),bond("O","C",MolecularGraph.BondOrder.SINGLE)),Map.of());}
    static MolecularGraph carbonyl(double x){return new MolecularGraph(List.of(atom("O","O",0,false,x,0,0),atom("C","C",0,false,x+1.2,0,0)),List.of(bond("O","C",MolecularGraph.BondOrder.DOUBLE)),Map.of());}
    static MolecularGraph ring(double z) {
        var atoms=new ArrayList<MolecularGraph.Atom>();var bonds=new ArrayList<MolecularGraph.Bond>();
        for(int i=0;i<6;i++){double angle=i*Math.PI/3;atoms.add(atom("C"+i,"C",0,true,1.4*Math.cos(angle),1.4*Math.sin(angle),z));bonds.add(bond("C"+i,"C"+((i+1)%6),MolecularGraph.BondOrder.AROMATIC));}
        return new MolecularGraph(atoms,bonds,Map.of());
    }
    // Neutral betaine supplies chemically explicit ammonium/carboxylate without altering the
    // existing sanitizer's rejection of net ions. Synthetic coordinates are not an energy model.
    static MolecularGraph betaine(double x,double z) {
        var a=List.of(atom("N","N",1,false,x,0,z),atom("C1","C",0,false,x+1,0,z+1),atom("C2","C",0,false,x-1,0,z+1),
                atom("C3","C",0,false,x,1,z-1),atom("C4","C",0,false,x,-1,z-1),atom("C5","C",0,false,x+12,0,z),
                atom("O1","O",-1,false,x+12,1,z),atom("O2","O",0,false,x+12,-1,z));
        var b=new ArrayList<MolecularGraph.Bond>();for(int i=1;i<=4;i++)b.add(bond("N","C"+i,MolecularGraph.BondOrder.SINGLE));
        b.add(bond("C4","C5",MolecularGraph.BondOrder.SINGLE));b.add(bond("C5","O1",MolecularGraph.BondOrder.SINGLE));b.add(bond("C5","O2",MolecularGraph.BondOrder.DOUBLE));
        return new MolecularGraph(a,b,Map.of());
    }
    static SystemStateView system(List<MolecularGraph> graphs,boolean protonation,boolean extraUnknown) {
        var residues=new ArrayList<Residue>();var bonds=new ArrayList<Bond>();var components=new ArrayList<SystemStateView.Component>();var charges=new TreeMap<AtomReference,Integer>();
        for(int i=0;i<graphs.size();i++) {
            int id=i+1;var g=graphs.get(i);var mapping=new TreeMap<String,AtomReference>();var atoms=new ArrayList<Atom>();
            for(var a:g.atoms()) {
                var r=ref(id,a.id());mapping.put(a.id(),r);charges.put(r,a.formalCharge());var p=a.coordinates();
                String type=a.element().equals("O")?"OA":a.element().equals("H")?"HD":a.aromatic()?"A":a.element();
                atoms.add(Atom.builder().name(a.id()).element(Element.valueOf(a.element())).autoDockType(type).position(new Point3D(p.x(),p.y(),p.z())).build());
            }
            residues.add(new Residue("CMP",id,atoms));
            for(var b:g.bonds())bonds.add(new Bond(mapping.get(b.firstAtomId()),mapping.get(b.secondAtomId()),totah.lab.gaia.chemistry.BondOrder.valueOf(b.order().name())));
            components.add(new SystemStateView.Component(ref(SUBJECT,"compound-"+id),g,List.of(mapping),List.of("synthetic source geometry")));
        }
        if(extraUnknown)residues.add(new Residue("UNK",99,List.of(Atom.builder().name("S").element(Element.S).autoDockType("SA").position(new Point3D(40,0,0)).build())));
        var structure=new Structure(List.of(new Chain("A",residues)),bonds,ConnectivityProvenance.EXPLICIT);
        return new SystemStateView(ref(CONTEXT,"explicit-state"),ResidueGraph.from(structure),components,List.of(ref(SOURCE,"synthetic")),Set.of(),new FormalChargeAssignments(charges),true,protonation,List.of("engineering geometry only; not relaxed"));
    }
    static SystemStateView fixture(String family,boolean far,boolean extraUnknown) {
        return switch(family) {
            case "HYDROPHOBIC"->system(List.of(methane(0),methane(far?20:3.8)),true,extraUnknown);
            case "HBOND"->system(List.of(methanol(),carbonyl(far?20:2.8)),true,extraUnknown);
            case "PI_STACKING"->system(List.of(ring(0),ring(far?20:3.5)),true,extraUnknown);
            case "PI_CATION"->system(List.of(ring(0),betaine(0,far?20:4)),true,extraUnknown);
            case "SALT_BRIDGE"->system(List.of(betaine(0,0),betaine(far?40:8,0)),true,extraUnknown);
            default->throw new IllegalArgumentException(family);
        };
    }
    static RuleManifest manifest(RuleRegistry registry,String family){return registry.manifests().values().stream().filter(m->m.implementationId().equals("athena.scientific")&&AthenaScientificRules.family(m.ruleId()).equals(family)).findFirst().orElseThrow();}
    static RuleRequest request(SystemStateView s,RuleManifest m,int budget,boolean unknown) {
        return new RuleRequest(s.binding(),m.key(),RuleRegistry.digest(m),List.of(),unknown?List.of(new ResidueId("A",1,null),new ResidueId("A",99,null)):List.of(new ResidueId("A",1,null)),List.of(new ResidueId("A",2,null)),4.5,2,1000,budget);
    }
    static SystemQualificationPipeline pipeline(){return new SystemQualificationPipeline(new SystemGraphValidation(OCL,OCL,OCL));}
    RuleExecutionPipeline.Result run(SystemStateView state,RuleManifest m,String name,int budget,boolean unknown,Optional<EvidenceEnvelope> reuse)throws Exception {
        var catalog=new EvidenceSnapshotCatalog(temp);var pipeline=pipeline();
        var base=pipeline.run(catalog,Optional.empty(),state,List.of(),Map.of(),List.of(),ref(ACTIVITY,"base-"+name),T);
        var result=new RuleExecutionPipeline(pipeline,OCL).run(catalog,base,Map.of(),RuleRegistry.scientific(),SystemStateView.bytes(m),request(state,m,budget,unknown),reuse,ref(ACTIVITY,name),T);
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var measured=json.readTree(result.measurements().orElseThrow().readPayload()).get("scientific").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)measured).remove("perceptionProvenance");
        var row=new TreeMap<String,Object>();row.put("case",name);row.put("rule",m.ruleId());row.put("state",state.binding());row.put("measurements",measured);row.put("status",assessment(result).status());
        Files.writeString(Path.of(System.getProperty("b00.trace")),new String(SystemStateView.bytes(row))+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        return result;
    }
    EvidenceInterpretation assessment(RuleExecutionPipeline.Result r)throws Exception {
        var history=new EvidenceSnapshotCatalog(temp).read(r.published().catalogSnapshot()).orElseThrow().history();
        return r.published().certificate().interpretations().stream().map(history.interpretations()::get).filter(i->i.evaluator().id().endsWith("/evaluate")).findFirst().orElseThrow();
    }
    @ParameterizedTest @ValueSource(strings={"HYDROPHOBIC","HBOND","PI_STACKING","PI_CATION","SALT_BRIDGE"})
    void positiveWithinBoundedDomain(String family)throws Exception {
        var m=manifest(RuleRegistry.scientific(),family);var result=run(fixture(family,false,false),m,"positive-"+family,1000,false,Optional.empty());
        assertEquals(EvidenceInterpretation.Status.SUPPORTED_PRESENT,assessment(result).status(),new String(result.measurements().orElseThrow().readPayload()));
    }
    @ParameterizedTest @ValueSource(strings={"HYDROPHOBIC","HBOND","PI_STACKING","PI_CATION","SALT_BRIDGE"})
    void negativeRequiresAllEightProofs(String family)throws Exception {
        var m=manifest(RuleRegistry.scientific(),family);var result=run(fixture(family,true,false),m,"negative-"+family,1000,false,Optional.empty());
        assertEquals(EvidenceInterpretation.Status.ABSENT_FALSE,assessment(result).status(),new String(result.measurements().orElseThrow().readPayload()));
        assertFalse(assessment(result).measurements().get("negativeCoverage").contains("false"));
    }
    @ParameterizedTest @ValueSource(strings={"HYDROPHOBIC","HBOND","PI_STACKING","PI_CATION","SALT_BRIDGE"})
    void incompleteElsewherePreservesPositiveButBlocksNegative(String family)throws Exception {
        var m=manifest(RuleRegistry.scientific(),family);
        assertEquals(EvidenceInterpretation.Status.SUPPORTED_PRESENT,assessment(run(fixture(family,false,true),m,"partial-pos-"+family,1000,true,Optional.empty())).status());
        assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,assessment(run(fixture(family,true,true),m,"partial-neg-"+family,1000,true,Optional.empty())).status());
    }
    @Test void schemaOneCanonicalBytesAndResourcesStayCompatible()throws Exception {
        var base=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules");
        for(String name:Files.readAllLines(base.resolve("index.txt"))) {
            var original=Files.readAllBytes(base.resolve(name));var m=RuleRegistry.decode(original);
            assertNull(m.negativeCoverage());assertFalse(new String(SystemStateView.bytes(m)).contains("negativeCoverage"));
            assertEquals(m,RuleRegistry.decode(SystemStateView.bytes(m)));
        }
        assertEquals(26,RuleRegistry.bundled().manifests().size());assertEquals(31,RuleRegistry.scientific().manifests().size());
    }
    @Test void schemaTwoCannotOmitWeakenOrReversionNegativeCoverage()throws Exception {
        var m=manifest(RuleRegistry.scientific(),"HBOND");String encoded=new String(SystemStateView.bytes(m));
        for(String invalid:List.of(encoded.replace("athena-negative-coverage/1","athena-negative-coverage/0"),encoded.replace("UNKNOWN_INCONCLUSIVE","ABSENT_FALSE"),encoded.replace("SEARCH_ENUMERATION_COMPLETE","OPTIONAL_SEARCH"),encoded.replace("athena-rule/2","athena-rule/1")))
            assertThrows(java.io.IOException.class,()->RuleRegistry.decode(invalid.getBytes()));
    }
    @Test void missingProtonationCannotBecomeNegative()throws Exception {
        var s=fixture("HBOND",true,false);var unknown=new SystemStateView(s.identity(),s.graph(),s.components(),s.sources(),s.cofactors(),s.charges(),true,false,s.limitations());
        var m=manifest(RuleRegistry.scientific(),"HBOND");assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,assessment(run(unknown,m,"missing-H",1000,false,Optional.empty())).status());
    }
    @Test void scientificPiCationDoesNotInheritUniversalAminePlaneGuard()throws Exception {
        var m=manifest(RuleRegistry.scientific(),"PI_CATION");var s=fixture("PI_CATION",false,false);
        var measured=AthenaScientificRules.collect(s,m,request(s,m,1000,false),OCL);
        assertTrue(measured.raw().candidates().stream().anyMatch(c->AthenaScientificRules.qualifies(c,m)
                &&InteractionMeasurements.classify("PI_CATION",c.values(),RuleRegistry.thresholds(m))==null));
    }
    @Test void exhaustedSearchCanKeepPositiveButCannotEstablishNegative()throws Exception {
        var m=manifest(RuleRegistry.scientific(),"SALT_BRIDGE");
        var s=fixture("SALT_BRIDGE",true,false);var result=run(s,m,"budget-negative",1,false,Optional.empty());
        assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,assessment(result).status());
        assertTrue(assessment(result).measurements().get("negativeCoverage").contains("\"SEARCH_ENUMERATION_COMPLETE\":false"));
        var near=system(List.of(betaine(0,0),betaine(-8,0)),true,false);
        assertEquals(EvidenceInterpretation.Status.SUPPORTED_PRESENT,assessment(run(near,m,"budget-positive",1,false,Optional.empty())).status());
    }
    @Test void nearestHeavyHydrogenAssignmentIsNotACovalentDonorProof()throws Exception {
        var alcohol=methanol();var changed=new MolecularGraph(alcohol.atoms().stream().map(a->a.id().equals("C")?atom("C","C",0,false,.9,0,0):a).toList(),alcohol.bonds(),alcohol.properties());
        var state=system(List.of(changed,carbonyl(2.8)),true,false);var m=manifest(RuleRegistry.scientific(),"HBOND");
        var raw=new HydrogenBondDetector().detect(state.graph().view(List.of(new ResidueId("A",1,null))).toStructure(),state.graph().view(List.of(new ResidueId("A",2,null))).toStructure(),InteractionThresholds.athenaDefaults());
        assertFalse(raw.isEmpty());
        assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,assessment(run(state,m,"wrong-nearest-donor",1000,false,Optional.empty())).status());
    }
    @Test void unsupportedHeteroaromaticRingIsNotAnAbsentRing()throws Exception {
        var carbonRing=ring(0);var hetero=new MolecularGraph(carbonRing.atoms().stream().map(a->a.id().equals("C0")?atom(a.id(),"N",0,true,a.coordinates().x(),a.coordinates().y(),a.coordinates().z()):a).toList(),carbonRing.bonds(),Map.of());
        var m=manifest(RuleRegistry.scientific(),"PI_STACKING");
        assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,assessment(run(system(List.of(hetero,ring(20)),true,false),m,"heteroring",1000,false,Optional.empty())).status());
    }
    @Test void missingBackendPreservesSourceEvidence()throws Exception {
        var s=fixture("HYDROPHOBIC",true,false);var m=manifest(RuleRegistry.scientific(),"HYDROPHOBIC");var catalog=new EvidenceSnapshotCatalog(temp);var p=pipeline();
        var base=p.run(catalog,Optional.empty(),s,List.of(),Map.of(),List.of(),ref(ACTIVITY,"no-backend-base"),T);
        var r=new RuleExecutionPipeline(p).run(catalog,base,Map.of(),RuleRegistry.scientific(),SystemStateView.bytes(m),request(s,m,1000,false),Optional.empty(),ref(ACTIVITY,"no-backend"),T);
        assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,assessment(r).status());assertTrue(r.measurements().isPresent());
    }
    @ParameterizedTest @ValueSource(strings={"HYDROPHOBIC","HBOND","PI_STACKING","PI_CATION","SALT_BRIDGE"})
    void explicitNativeOperationalBoundariesRemainVersioned(String family)throws Exception {
        var m=manifest(RuleRegistry.scientific(),family);
        double maximum=switch(family){case "HYDROPHOBIC"->4;case "HBOND"->3.5;case "PI_CATION"->6;default->5.5;};
        var values=new TreeMap<String,Double>();values.put("distance",maximum);values.put("hydrogenDistance",2.5);values.put("angle",family.equals("HBOND")?120.0:30.0);values.put("offset",2.0);
        var a=List.of(ref(1,"C"));var b=List.of(ref(2,"C"));
        assertTrue(AthenaScientificRules.qualifies(new InteractionMeasurements.Candidate(family,a,b,values),m));
        values.put("distance",Math.nextUp(maximum));assertFalse(AthenaScientificRules.qualifies(new InteractionMeasurements.Candidate(family,a,b,values),m));
        values.put("distance",.5);assertFalse(AthenaScientificRules.qualifies(new InteractionMeasurements.Candidate(family,a,b,values),m));
    }
    @Test void newThresholdAndRetirementReuseMeasurementsWithoutMutatingHistory()throws Exception {
        var registry=RuleRegistry.scientific();var original=manifest(registry,"HYDROPHOBIC");var s=fixture("HYDROPHOBIC",false,false);
        var first=run(s,original,"original",1000,false,Optional.empty());var raw=first.measurements().orElseThrow();byte[] bytes=raw.readPayload();
        var parameters=new TreeMap<>(original.parameters());parameters.put("hydrophobicDistMax",new RuleManifest.Parameter("3.0","angstrom","versioned synthetic tighter criterion"));
        var next=new RuleManifest(original.schema(),original.ruleId(),"2",original.profile(),original.family(),original.tier(),original.implementationId(),original.implementationVersion(),original.qualification(),false,original.requiredCapabilities(),original.requiredChemistry(),original.requiredGeometry(),original.measurementsProduced(),original.classificationStates(),parameters,original.scientificSources(),original.referenceArtifacts(),original.limitations(),original.negativeCoverage());
        registry=registry.register(next);var catalog=new EvidenceSnapshotCatalog(temp);var p=pipeline();
        var base=p.run(catalog,Optional.empty(),s,List.of(),Map.of(),List.of(),ref(ACTIVITY,"reinterpret-base"),T);
        var second=new RuleExecutionPipeline(p,OCL).run(catalog,base,Map.of(),registry,SystemStateView.bytes(next),request(s,next,1000,false),Optional.of(raw),ref(ACTIVITY,"reinterpret"),T);
        assertEquals(EvidenceInterpretation.Status.ABSENT_FALSE,assessment(second).status());assertArrayEquals(bytes,raw.readPayload());
        assertEquals(EvidenceInterpretation.Status.SUPPORTED_PRESENT,assessment(first).status());
        var retired=new RuleManifest(next.schema(),next.ruleId(),"3",next.profile(),next.family(),next.tier(),next.implementationId(),next.implementationVersion(),next.qualification(),true,next.requiredCapabilities(),next.requiredChemistry(),next.requiredGeometry(),next.measurementsProduced(),next.classificationStates(),next.parameters(),next.scientificSources(),next.referenceArtifacts(),next.limitations(),next.negativeCoverage());
        var third=new RuleExecutionPipeline(p,OCL).run(catalog,base,Map.of(),registry.register(retired),SystemStateView.bytes(retired),request(s,retired,1000,false),Optional.of(raw),ref(ACTIVITY,"retired"),T);
        assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,assessment(third).status());assertArrayEquals(bytes,third.measurements().orElseThrow().readPayload());
    }
    @Test void missingNegativeProofCannotBeReinterpretedAsComplete()throws Exception {
        var m=manifest(RuleRegistry.scientific(),"HYDROPHOBIC");var s=fixture("HYDROPHOBIC",true,false);var first=run(s,m,"before-tamper",1000,false,Optional.empty());
        var raw=new String(first.measurements().orElseThrow().readPayload());
        var corrupt=SystemQualificationPipeline.envelope(ref(ACTIVITY,"corruption"),"missing-proof","athena:rule-measurements",raw.replace("\"SEARCH_ENUMERATION_COMPLETE\":true,","").getBytes(),ref(METHOD,"corruption-test"),s.subject(),T,List.of("test missing proof"));
        var result=run(s,m,"after-tamper",1000,false,Optional.of(corrupt));
        assertEquals(EvidenceInterpretation.Status.FAILED,assessment(result).status());
        assertArrayEquals(corrupt.readPayload(),result.measurements().orElseThrow().readPayload());
    }
    @Test void matcherFailureAppendsFailedWithoutLosingDefinitionOrSource()throws Exception {
        class BrokenMatcher implements totah.lab.athena.design.backend.SubstructureMatcher,totah.lab.athena.design.backend.MolecularSanitizer {
            public totah.lab.athena.design.backend.SubstructureMatcher.Result match(String query,MolecularGraph graph)throws totah.lab.athena.design.backend.MolecularBackendException {throw new totah.lab.athena.design.backend.MolecularBackendException("synthetic unavailable matcher");}
            public totah.lab.athena.design.backend.MolecularSanitizer.Result sanitize(MolecularGraph graph,totah.lab.athena.design.backend.MolecularSanitizer.SanitizationPolicy policy)throws totah.lab.athena.design.backend.MolecularBackendException {return OCL.sanitize(graph,policy);}
        }
        var s=fixture("HYDROPHOBIC",false,false);var m=manifest(RuleRegistry.scientific(),"HYDROPHOBIC");var catalog=new EvidenceSnapshotCatalog(temp);var p=pipeline();
        var base=p.run(catalog,Optional.empty(),s,List.of(),Map.of(),List.of(),ref(ACTIVITY,"broken-base"),T);
        var r=new RuleExecutionPipeline(p,new BrokenMatcher()).run(catalog,base,Map.of(),RuleRegistry.scientific(),SystemStateView.bytes(m),request(s,m,1000,false),Optional.empty(),ref(ACTIVITY,"broken"),T);
        var history=catalog.read(r.published().catalogSnapshot()).orElseThrow().history();
        assertTrue(r.measurements().isEmpty());assertTrue(history.interpretations().values().stream().anyMatch(x->x.status()==EvidenceInterpretation.Status.FAILED));
        assertTrue(history.envelopes().values().stream().anyMatch(x->x.evidenceType().equals("athena:rule-manifest")));
        assertTrue(history.envelopes().values().stream().anyMatch(x->x.evidenceType().equals("athena:system-state")));
    }
    @Test void explicitTopologyIsRequiredBeforeAnyCompleteNegative()throws Exception {
        var s=fixture("HYDROPHOBIC",true,false);var partial=new SystemStateView(s.identity(),ResidueGraph.from(new Structure(s.graph().structure().getChains())),s.components(),s.sources(),s.cofactors(),s.charges(),true,true,List.of("topology not established"));
        var m=manifest(RuleRegistry.scientific(),"HYDROPHOBIC");var result=run(partial,m,"missing-topology",1000,false,Optional.empty());
        assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,assessment(result).status());
        assertTrue(assessment(result).measurements().get("negativeCoverage").contains("\"EXPLICIT_CONNECTIVITY\":false"));
    }
    @Test void coincidentCandidateGeometryDoesNotProveScientificAbsence()throws Exception {
        var m=manifest(RuleRegistry.scientific(),"HYDROPHOBIC");var s=system(List.of(methane(0),methane(0)),true,false);
        var result=run(s,m,"coincident",1000,false,Optional.empty());
        assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,assessment(result).status());
        assertTrue(assessment(result).measurements().get("negativeCoverage").contains("\"FINITE_GEOMETRY\":false"));
    }
    @Test void coverageBooleansCannotBeCoercedFromStrings()throws Exception {
        var m=manifest(RuleRegistry.scientific(),"HYDROPHOBIC");var s=fixture("HYDROPHOBIC",true,false);var first=run(s,m,"typed-proof",1000,false,Optional.empty());
        String original=new String(first.measurements().orElseThrow().readPayload());int index=0;
        for(String corrupted:List.of(original.replace("\"SEARCH_ENUMERATION_COMPLETE\":true","\"SEARCH_ENUMERATION_COMPLETE\":\"true\""),original.replaceFirst("\"complete\":true","\"complete\":false"))) {
            var bytes=corrupted.getBytes();String id="invalid-proof-"+index++;
            var invalid=SystemQualificationPipeline.envelope(ref(ACTIVITY,id),"proof","athena:rule-measurements",bytes,ref(METHOD,"invalid-type"),s.subject(),T,List.of("synthetic malformed proof"));
            var result=run(s,m,id,1000,false,Optional.of(invalid));
            assertEquals(EvidenceInterpretation.Status.FAILED,assessment(result).status());assertArrayEquals(bytes,result.measurements().orElseThrow().readPayload());
        }
    }
    public static void main(String[] args)throws Exception {
        var test=new AthenaScientificRulesAcceptanceTest();test.temp=Files.createDirectory(Path.of(args[0]));
        for(String family:List.of("HYDROPHOBIC","HBOND","PI_STACKING","PI_CATION","SALT_BRIDGE")) {
            var m=manifest(RuleRegistry.scientific(),family);var r=test.run(fixture(family,false,false),m,"replay-"+family,1000,false,Optional.empty());
            assertEquals(EvidenceInterpretation.Status.SUPPORTED_PRESENT,test.assessment(r).status());
            Files.write(test.temp.resolve(family+"-certificate.json"),SystemStateView.bytes(r.published().certificate()),StandardOpenOption.CREATE_NEW);
        }
    }
}
