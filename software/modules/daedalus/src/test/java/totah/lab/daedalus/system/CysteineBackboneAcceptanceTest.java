package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;

/** Synthetic graph/state acceptance; residue labels are never pattern or chemical-state authority. */
class CysteineBackboneAcceptanceTest {
    static RuleManifest model()throws Exception{return RuleRegistry.decode(Files.readAllBytes(Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/cysteine-backbone-b06/ATHENA.GROUP.CYSTEINE_BACKBONE.rule.json")));}
    static Fixture molecule(String variant) {
        String[] elements={"N","C","C","S","C","O","O"};int[] hydrogens={2,1,2,1,0,0,1};
        var a=new ArrayList<MolecularGraph.Atom>();var b=new ArrayList<MolecularGraph.Bond>();var h=new TreeMap<String,Integer>();
        for(int i=0;i<elements.length;i++) {
            int charge=i==3&&variant.equals("thiolate")?-1:i==0&&variant.equals("ammonium")?1:0;
            if(i==3&&variant.equals("thiolate"))hydrogens[i]=0;if(i==0&&variant.equals("ammonium"))hydrogens[i]=3;
            String stereo=i==1&&Set.of("PARITY_1","PARITY_2").contains(variant)?variant:"UNSPECIFIED";
            a.add(new MolecularGraph.Atom("a"+i,elements[i],null,charge,0,false,stereo,new MolecularGraph.Coordinates(i,Math.sin(i),Math.cos(i)),Map.of("sourceResidueLabel",variant.equals("wrong-label")?"UNK":"CYS")));h.put("a"+i,hydrogens[i]);
        }
        for(int[] edge:new int[][]{{0,1},{1,2},{2,3},{1,4},{4,5},{4,6}})b.add(bond("a"+edge[0],"a"+edge[1],edge[0]==4&&edge[1]==5?MolecularGraph.BondOrder.DOUBLE:MolecularGraph.BondOrder.SINGLE));
        if(Set.of("modified","oxidized").contains(variant)) {a.add(atom("cap","C",0,false,8,1,1));h.put("cap",3);h.put("a3",0);b.add(bond("a3","cap",MolecularGraph.BondOrder.SINGLE));}
        if(variant.equals("oxidized")){a.add(atom("sulfoxideO","O",0,false,9,1,2));h.put("sulfoxideO",0);b.add(bond("a3","sulfoxideO",MolecularGraph.BondOrder.DOUBLE));}
        if(variant.equals("aromaticN")) {
            var n=a.get(0);a.set(0,new MolecularGraph.Atom(n.id(),n.element(),null,0,0,true,n.stereochemistry(),n.coordinates(),n.properties()));h.put("a0",0);
            String previous="a0";for(int i=0;i<4;i++){String id="r"+i;a.add(atom(id,"C",0,true,10+i,2,1));h.put(id,1);b.add(bond(previous,id,MolecularGraph.BondOrder.AROMATIC));previous=id;}b.add(bond(previous,"a0",MolecularGraph.BondOrder.AROMATIC));
        }
        if(variant.equals("wrong-connectivity")){b.removeIf(x->x.firstAtomId().equals("a2")&&x.secondAtomId().equals("a3"));b.add(bond("a0","a3",MolecularGraph.BondOrder.SINGLE));h.put("a2",3);h.put("a0",1);}
        return new Fixture(new MolecularGraph(a,b,Map.of("fixture",variant)),h);
    }
    static JsonNode collect(SystemStateView s,JsonNode c)throws Exception {
        var m=model();var r=request(s,m);var definition=JSON.readTree(m.parameters().get("definition").value());
        var finding=RuleAnalyzers.collector(m,r,BACKEND).analyze(s,List.of(envelope(s,"athena:group-source-coverage",c),envelope(s,"athena:group-definition",definition)),Map.of()).getFirst();
        var report=JSON.readTree(finding.measurements().get("payload"));
        var raw=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"cysteine"),"report","athena:group-identities",SystemStateView.bytes(report),RuleAnalyzers.collector(m,r,BACKEND).method(),s.subject(),T,List.of("synthetic"));
        var restored=(EvidenceEnvelope)new EvidenceExchange().decodeRecord(new EvidenceExchange().encodeRecord(raw));
        var eval=RuleAnalyzers.evaluator(m,r).analyze(s,List.of(restored),Map.of()).getFirst();
        assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,eval.status());assertEquals(report,JSON.readTree(eval.measurements().get("payload")));
        return report;
    }
    static JsonNode collect(Fixture f)throws Exception{var s=system(List.of(f.graph()),true,false);return collect(s,coverage(s,f));}
    @ParameterizedTest @ValueSource(strings={"plain","PARITY_1","PARITY_2","thiolate","ammonium","modified","oxidized","aromaticN","wrong-label"})
    void backboneDoesNotResolveIndependentLabelsOrStates(String variant)throws Exception {
        var f=molecule(variant);var before=SystemStateView.bytes(f.graph());var r=collect(f);assertEquals("SUPPORTED_PRESENT",r.path("assessment").asText(),r.toString());assertEquals(1,r.path("occurrences").size());
        var roles=r.path("occurrences").get(0).path("roleCorrespondenceAlternatives").get(0);
        for(var role:Map.of("N","a0","CA","a1","CB","a2","SG","a3","C","a4","O","a5").entrySet())assertEquals(role.getValue(),roles.path(role.getKey()).get(0).asText());
        assertArrayEquals(before,SystemStateView.bytes(f.graph()));assertEquals(JSON.valueToTree(f.graph()),r.path("sourceGraph"));
    }
    @Test void labelAloneCannotEstablishBackbone()throws Exception{assertEquals("ABSENT_FALSE",collect(molecule("wrong-connectivity")).path("assessment").asText());}
    @Test void explicitHydrogensAndDistinctOccurrencesSurvive()throws Exception {
        assertEquals(1,collect(explicit(molecule("plain"))).path("occurrences").size());assertEquals(2,collect(doubled(molecule("plain"))).path("occurrences").size());
    }
    @ParameterizedTest @ValueSource(strings={"unknownH","contradictoryH","partial","unknownCharge"})
    void incompleteSourceCannotEstablishAbsence(String variant)throws Exception {
        var f=molecule("plain");var s=system(List.of(f.graph()),true,false);var c=coverage(s,f);
        if(variant.equals("unknownH"))((ObjectNode)c.path("atomState").path("a1")).put("hydrogenMode","UNKNOWN");
        if(variant.equals("contradictoryH"))((ObjectNode)c.path("atomState").path("a1")).put("implicitHydrogenCount",2);
        if(variant.equals("partial"))c.put("completeGraph","UNKNOWN_INCONCLUSIVE");
        if(variant.equals("unknownCharge"))((ObjectNode)c.path("atomState").path("a1")).put("chargeStatus","UNKNOWN_INCONCLUSIVE");
        assertEquals("UNKNOWN_INCONCLUSIVE",collect(s,c).path("assessment").asText());
    }
    @Test void missingCorrespondenceIsInconclusiveWithoutInventingMapping()throws Exception {
        var f=molecule("plain");var original=system(List.of(f.graph()),true,false);var c=original.components().getFirst();var mapping=new TreeMap<>(c.correspondenceAlternatives().getFirst());mapping.remove("a3");
        var s=new SystemStateView(original.identity(),original.graph(),List.of(new SystemStateView.Component(c.identity(),c.chemistry(),List.of(mapping),c.limitations())),original.sources(),original.cofactors(),original.charges(),original.frameQualified(),original.protonationQualified(),original.limitations());
        assertEquals("UNKNOWN_INCONCLUSIVE",collect(s,coverage(s,f)).path("assessment").asText());
    }
    @Test void graphOrderingDoesNotChangeRoles()throws Exception {
        var f=molecule("plain");var a=new ArrayList<>(f.graph().atoms());var b=new ArrayList<>(f.graph().bonds());Collections.reverse(a);Collections.reverse(b);
        assertEquals(collect(f).path("occurrences").get(0).path("roleCorrespondenceAlternatives"),collect(new Fixture(new MolecularGraph(a,b,f.graph().properties()),f.hydrogens())).path("occurrences").get(0).path("roleCorrespondenceAlternatives"));
    }
    static Fixture pair(boolean peptide,boolean ss) {
        var f=doubled(molecule("plain"));var atoms=new ArrayList<MolecularGraph.Atom>();
        for(var a:f.graph().atoms()) {
            if(peptide&&a.id().equals("a6"))continue;
            var c=a.coordinates();if(a.id().startsWith("z"))c=new MolecularGraph.Coordinates(c.x()+2.3,c.y()+1.7,c.z()+0.8);
            atoms.add(new MolecularGraph.Atom(a.id(),a.element(),a.isotope(),a.formalCharge(),a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),c,a.properties()));
        }
        var bonds=new ArrayList<>(f.graph().bonds());var hs=new TreeMap<>(f.hydrogens());
        if(peptide){bonds.removeIf(b->b.firstAtomId().equals("a6")||b.secondAtomId().equals("a6"));hs.remove("a6");hs.put("za0",1);bonds.add(bond("a4","za0",MolecularGraph.BondOrder.SINGLE));}
        if(ss){hs.put("a3",0);hs.put("za3",0);bonds.add(bond("a3","za3",MolecularGraph.BondOrder.SINGLE));}
        return new Fixture(new MolecularGraph(atoms,bonds,Map.of("fixture","synthetic pair")),hs);
    }
    static RuleManifest attribution()throws Exception{return RuleRegistry.decode(Files.readAllBytes(Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/cysteine-backbone-b06/ATHENA.SULF.CYSTEINE_BACKBONE_ATTRIBUTION.rule.json")));}
    static EvidenceEnvelope reportEnvelope(SystemStateView s,JsonNode report)throws Exception {
        var g=model();return SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"backbone"),"report","athena:group-identities",SystemStateView.bytes(report),RuleAnalyzers.collector(g,request(s,g),BACKEND).method(),s.subject(),T,List.of("synthetic"));
    }
    static List<SystemGraphAnalyzer.Finding> attribute(Fixture f,boolean reverse)throws Exception {return attribute(f,system(List.of(f.graph()),true,false),reverse);}
    static List<SystemGraphAnalyzer.Finding> attribute(Fixture f,SystemStateView s,boolean reverse)throws Exception {
        var report=collect(s,coverage(s,f));var e=reportEnvelope(s,report);var m=attribution();var map=s.components().getFirst().correspondenceAlternatives().getFirst();
        var pair=List.of(map.get(reverse?"za3":"a3"),map.get(reverse?"a3":"za3"));
        var r=new RuleRequest(s.binding(),m.key(),RuleRegistry.digest(m),pair,List.of(),List.of(),4.5,0,1000,1000);
        var before=SystemStateView.bytes(s.snapshot());var out=RuleAnalyzers.evaluator(m,r).analyze(s,List.of(e),Map.of());assertArrayEquals(before,SystemStateView.bytes(s.snapshot()));
        assertTrue(out.stream().allMatch(x->x.status()==EvidenceInterpretation.Status.NOT_EVALUATED));return out;
    }
    @ParameterizedTest @ValueSource(booleans={false,true}) void peptideLinkageAndSulfurConnectivityStayIndependent(boolean ss)throws Exception {
        var out=attribute(pair(true,ss),false);var peptide=out.stream().filter(f->f.id().equals("peptide-0")).findFirst().orElseThrow();
        assertEquals("SUPPORTED_PRESENT",peptide.measurements().get("assessment"));assertEquals(ss,!JSON.readTree(peptide.measurements().get("sourceSulfurBonds")).isEmpty());
        var geometry=out.stream().filter(f->f.id().equals("geometry-0")).findFirst().orElseThrow();
        var report=JSON.readTree(geometry.measurements().get("geometryReport"));assertEquals(10,report.path("operations").size());
        assertEquals("UNKNOWN_INCONCLUSIVE",geometry.measurements().get("assessment"));assertTrue(geometry.measurements().get("unavailable").contains("external"));
    }
    @Test void unrelatedOrReversedPairDoesNotBorrowPeptideEdge()throws Exception {
        for(var out:List.of(attribute(pair(false,false),false),attribute(pair(true,false),true))) {
            assertEquals("ABSENT_FALSE",out.stream().filter(f->f.id().equals("peptide-0")).findFirst().orElseThrow().measurements().get("assessment"));
            assertEquals(6,JSON.readTree(out.stream().filter(f->f.id().equals("geometry-0")).findFirst().orElseThrow().measurements().get("geometryReport")).path("operations").size());
        }
    }
    @Test void incompleteIdentityCannotBePromotedByAttribution()throws Exception {
        var f=pair(true,false);var s=system(List.of(f.graph()),true,false);var c=coverage(s,f);((ObjectNode)c.path("atomState").path("a1")).put("hydrogenMode","UNKNOWN");
        var report=collect(s,c);var m=attribution();var r=new RuleRequest(s.binding(),m.key(),RuleRegistry.digest(m),List.of(ref(1,"a3"),ref(1,"za3")),List.of(),List.of(),4.5,0,1000,1000);
        var out=RuleAnalyzers.evaluator(m,r).analyze(s,List.of(reportEnvelope(s,report)),Map.of());assertTrue(out.stream().anyMatch(x->x.id().equals("pair-unavailable")));assertFalse(out.stream().anyMatch(x->x.id().startsWith("geometry-")));
    }
    @Test void oldV2MissingMappingWitnessRemainsFailure()throws Exception {
        var n=(ObjectNode)JSON.valueToTree(model());n.put("implementationVersion","2");n.put("profile","ATHENA_GROUP_CONTEXT_V2");var m=RuleRegistry.decode(SystemStateView.bytes(n));
        var f=molecule("plain");var original=system(List.of(f.graph()),true,false);var c=original.components().getFirst();var map=new TreeMap<>(c.correspondenceAlternatives().getFirst());map.remove("a3");
        var s=new SystemStateView(original.identity(),original.graph(),List.of(new SystemStateView.Component(c.identity(),c.chemistry(),List.of(map),c.limitations())),original.sources(),original.cofactors(),original.charges(),true,true,original.limitations());
        assertThrows(NullPointerException.class,()->RuleAnalyzers.collector(m,request(s,m),BACKEND).analyze(s,List.of(envelope(s,"athena:group-source-coverage",coverage(s,f)),envelope(s,"athena:group-definition",JSON.readTree(m.parameters().get("definition").value()))),Map.of()));
    }
    @Test void adjacentCysLabelsWithoutSourcePeptideBondAreNotVicinalProof()throws Exception {
        var f=pair(false,false);var base=system(List.of(f.graph()),true,false);var residues=new ArrayList<totah.lab.gaia.structure.Residue>();
        var mapping=new TreeMap<String,totah.lab.gaia.structure.AtomReference>();var charges=new TreeMap<totah.lab.gaia.structure.AtomReference,Integer>();
        for(int index=1;index<=2;index++) {
            var atoms=new ArrayList<totah.lab.gaia.structure.Atom>();
            for(var a:f.graph().atoms())if(a.id().startsWith("z")== (index==2)) {var ar=ref(index,a.id());mapping.put(a.id(),ar);charges.put(ar,a.formalCharge());atoms.add(base.atoms().get(ref(1,a.id())));}
            residues.add(new totah.lab.gaia.structure.Residue("CYS",index,atoms));
        }
        var bonds=f.graph().bonds().stream().map(b->new totah.lab.gaia.structure.Bond(mapping.get(b.firstAtomId()),mapping.get(b.secondAtomId()),totah.lab.gaia.chemistry.BondOrder.valueOf(b.order().name()))).toList();
        var structure=new totah.lab.gaia.structure.Structure(List.of(new totah.lab.gaia.structure.Chain("A",residues)),bonds,totah.lab.gaia.structure.ConnectivityProvenance.EXPLICIT);
        var c=base.components().getFirst();var state=new SystemStateView(base.identity(),totah.lab.gaia.graph.ResidueGraph.from(structure),List.of(new SystemStateView.Component(c.identity(),c.chemistry(),List.of(mapping),c.limitations())),base.sources(),base.cofactors(),new totah.lab.athena.interaction.perception.FormalChargeAssignments(charges),true,true,base.limitations());
        var result=attribute(f,state,false);assertEquals("ABSENT_FALSE",result.stream().filter(x->x.id().equals("peptide-0")).findFirst().orElseThrow().measurements().get("assessment"));
        assertTrue(result.getFirst().measurements().get("sourceLabels").contains("CYS"));
    }
    @Test void correspondenceAlternativesStayUnresolvedAndPreserved()throws Exception {
        var f=molecule("plain");var base=system(List.of(f.graph()),true,false);var c=base.components().getFirst();
        var alternatives=List.of(c.correspondenceAlternatives().getFirst(),c.correspondenceAlternatives().getFirst());
        var state=new SystemStateView(base.identity(),base.graph(),List.of(new SystemStateView.Component(c.identity(),c.chemistry(),alternatives,c.limitations())),base.sources(),base.cofactors(),base.charges(),true,true,base.limitations());
        var before=SystemStateView.bytes(state.snapshot());var result=collect(state,coverage(state,f));assertEquals("UNKNOWN_INCONCLUSIVE",result.path("assessment").asText());assertFalse(result.path("b00Results").isEmpty());assertEquals(2,state.components().getFirst().correspondenceAlternatives().size());assertArrayEquals(before,SystemStateView.bytes(state.snapshot()));
    }
    @Test void journalPreservesOriginalAndFailedTamperedInterpretation(@org.junit.jupiter.api.io.TempDir Path directory)throws Exception {
        var f=pair(true,true);var s=system(List.of(f.graph()),true,false);var original=collect(s,coverage(s,f));var input=reportEnvelope(s,original);var m=attribution();
        var request=new RuleRequest(s.binding(),m.key(),RuleRegistry.digest(m),List.of(ref(1,"a3"),ref(1,"za3")),List.of(),List.of(),4.5,0,1000,1000);
        var catalog=new EvidenceSnapshotCatalog(directory);var pipeline=pipeline();
        var first=pipeline.run(catalog,Optional.empty(),s,List.of(input),Map.of(),List.of(RuleAnalyzers.evaluator(m,request)),ref(ScientificReference.Kind.ACTIVITY,"cys-valid"),T);
        var changed=original.deepCopy();((ObjectNode)changed.path("occurrences").get(0).path("roleCorrespondenceAlternatives").get(0)).putArray("CA").add("a2");
        var bad=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"cys-tamper"),"report","athena:group-identities",SystemStateView.bytes(changed),input.method(),s.subject(),T,List.of("deliberate malformed witness"));
        var second=pipeline.run(catalog,Optional.of(first.catalogSnapshot()),s,List.of(bad),Map.of(),List.of(RuleAnalyzers.evaluator(m,request)),ref(ScientificReference.Kind.ACTIVITY,"cys-failed"),T);
        var history=catalog.read(second.catalogSnapshot()).orElseThrow().history();assertArrayEquals(input.readPayload(),history.envelopes().get(input.reference()).readPayload());assertArrayEquals(bad.readPayload(),history.envelopes().get(bad.reference()).readPayload());
        assertTrue(second.certificate().interpretations().stream().map(history.interpretations()::get).anyMatch(x->x.status()==EvidenceInterpretation.Status.FAILED));
        assertTrue(history.interpretations().values().stream().anyMatch(x->x.measurements().containsKey("geometryReport")));
    }
    @Test void atomAttachmentChangeDoesNotQualifyBackbone()throws Exception {
        var f=molecule("plain");var atoms=new ArrayList<>(f.graph().atoms());atoms.add(atom("extra","C",0,false,9,0,0));var bonds=new ArrayList<>(f.graph().bonds());bonds.add(bond("a1","extra",MolecularGraph.BondOrder.SINGLE));var h=new TreeMap<>(f.hydrogens());h.put("a1",0);h.put("extra",3);
        var r=collect(new Fixture(new MolecularGraph(atoms,bonds,Map.of()),h));assertTrue(r.path("occurrences").isEmpty());assertNotEquals("SUPPORTED_PRESENT",r.path("assessment").asText());
    }
    public static void main(String[] args)throws Exception{Files.write(Path.of(args[0]),SystemStateView.bytes(Map.of("backbone",collect(molecule("plain")),"peptide",attribute(pair(true,true),false),"unlinked",attribute(pair(false,false),false))));}
}
