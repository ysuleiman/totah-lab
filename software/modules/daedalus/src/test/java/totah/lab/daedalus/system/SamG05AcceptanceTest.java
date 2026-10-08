package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.*;
import totah.lab.gaia.structure.AtomReference;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;
import static totah.lab.daedalus.system.S1NitrogenAcceptanceTest.canonical;
import static totah.lab.daedalus.system.S1NitrogenAcceptanceTest.pin;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Hand-authored exact source facts and synthetic review authorities; never production scientific evidence. */
class SamG05AcceptanceTest {
    @TempDir Path temp;
    static final String ID="ATHENA.G05.SAM_CHEBI_142094_SOURCE_IDENTITY", GEOM="ATHENA.G05.SAM_PHE_CONTINUOUS_GEOMETRY";
    static final Path RESOURCE=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/sam-g05-v1");
    static final List<String> CATEGORIES=List.of("elements","isotopes","formalCharges","hydrogens","aromaticity","bondOrders","stereochemistry","electronicState","connections");
    static final Map<String,String> STEREO=Map.of("2","S","5","S","11","S","13","R","14","R","16","S");
    static Fixture sam(String variant) throws Exception {
        // Original molfile indexing, independently transcribed source graph, without runtime reference-table reuse.
        String elements="CSCCCNCOOCCOCCOCO NCNCCNCNCN".replace(" ","");
        int[] hs={3,0,2,2,1,3,0,0,0,2,1,0,1,1,1,1,1,0,1,0,0,0,0,1,0,0,2};
        assertEquals(27,elements.length());var hydrogens=new TreeMap<String,Integer>();var atoms=new ArrayList<MolecularGraph.Atom>();
        var originalStereo=new com.actelion.research.chem.StereoMolecule();
        if(variant.equals("relative-stereo")){assertTrue(new com.actelion.research.chem.MolfileParser().parse(originalStereo,Files.readString(RESOURCE.resolve("CHEBI-142094.mol"))));originalStereo.ensureHelperArrays(com.actelion.research.chem.Molecule.cHelperCIP);}
        for(int i=1;i<=27;i++) {
            String id=""+i,element=elements.substring(i-1,i);int charge=i==2||i==6?1:i==8?-1:0;int h=hs[i-1];
            if(variant.equals("ccd")&&i==6){charge=0;h=2;}
            if(variant.equals("acid")&&i==8){charge=0;h=1;}
            if(variant.equals("wrong-h")&&i==1)h=2;
            if(variant.equals("sah")&&i==2)charge=0;
            if(variant.equals("sah")&&i==1)continue;
            String stereo=STEREO.getOrDefault(id,"NONE");
            if((variant.equals("s-epimer")&&i==2)||(variant.equals("alpha-epimer")&&i==5)||(variant.equals("ribose-epimer")&&i==13))stereo=stereo.equals("S")?"R":"S";
            if(variant.equals("missing-stereo")&&i==2)stereo="UNSPECIFIED";
            if(variant.equals("relative-stereo")&&STEREO.containsKey(id))stereo="PARITY_"+originalStereo.getAtomParity(i-1);
            double x=i+20,y=0,z=0;if(i==2){x=0;z=3;}if(i==1){x=0;z=4;}
            if(variant.equals("coplanar")&&i==1){x=4;z=0;}
            if(variant.equals("zero")&&i==2)z=0;
            if(variant.equals("far")&&(i==1||i==2))z=100+i;
            var properties=new TreeMap<String,String>();if(!variant.equals("missing-none"))properties.put("athena.ocl.atomRadicalState/1",Set.of("radical","radical-conflict").contains(variant)&&i==2?"D":variant.equals("unknown-electronic")&&i==2?"X":"NONE");
            atoms.add(new MolecularGraph.Atom(id,element,variant.equals("isotope")&&i==1?13:null,charge,0,i>=18&&i<=26,stereo,new MolecularGraph.Coordinates(x,y,z),properties));hydrogens.put(id,h);
        }
        var bonds=new ArrayList<MolecularGraph.Bond>();
        String edges="2-1 2-3 2-10 3-4 4-5 5-6 5-7 7-8 7=9 11-10 11-12 11-16 12-13 13-14 13-18 14-15 14-16 16-17 18-19 18-22 19=20 20-21 21=22 21-26 22-23 23=24 24-25 25=26 26-27";
        for(var edge:edges.split(" ")){var e=edge.split("[-=]");if(variant.equals("sah")&&(e[0].equals("1")||e[1].equals("1")))continue;boolean aromatic=Integer.parseInt(e[0])>=18&&Integer.parseInt(e[0])<=26&&Integer.parseInt(e[1])>=18&&Integer.parseInt(e[1])<=26;var order=edge.contains("=")?MolecularGraph.BondOrder.DOUBLE:MolecularGraph.BondOrder.SINGLE;if(variant.equals("aromatic-order")&&aromatic)order=MolecularGraph.BondOrder.AROMATIC;bonds.add(new MolecularGraph.Bond("b"+bonds.size(),e[0],e[1],order,aromatic,"NONE",Map.of()));}
        if(variant.equals("extra-bond"))bonds.add(new MolecularGraph.Bond("extra","1","3",MolecularGraph.BondOrder.SINGLE,false,"NONE",Map.of()));
        if(variant.equals("explicit-h")){hydrogens.put("H1",0);hydrogens.put("1",2);atoms.add(new MolecularGraph.Atom("H1","H",null,0,0,false,"NONE",new MolecularGraph.Coordinates(0,1,4),Map.of("athena.ocl.atomRadicalState/1","NONE")));bonds.add(new MolecularGraph.Bond("h1","1","H1",MolecularGraph.BondOrder.SINGLE,false,"NONE",Map.of()));}
        if(variant.equals("permuted")){Collections.reverse(atoms);Collections.reverse(bonds);}
        return new Fixture(new MolecularGraph(atoms,bonds,Map.of("fixture","hand-transcribed CHEBI142094 source domain")),hydrogens);
    }
    static Fixture transformed(Fixture fixture) {
        var g=fixture.graph();var atoms=g.atoms().stream().map(a->{var c=a.coordinates();return new MolecularGraph.Atom(a.id(),a.element(),a.isotope(),a.formalCharge(),a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),new MolecularGraph.Coordinates(-c.y()+10,c.x()-7,c.z()+2),a.properties());}).toList();return new Fixture(new MolecularGraph(atoms,g.bonds(),g.properties()),fixture.hydrogens());
    }
    static JsonNode bonds(MolecularGraph g,boolean aromatic)throws Exception{var list=new ArrayList<Map<String,Object>>();for(var b:g.bonds()){var pair=List.of(b.firstAtomId(),b.secondAtomId()).stream().sorted().toList();list.add(Map.of("first",pair.get(0),"second",pair.get(1),aromatic?"aromatic":"order",aromatic?b.aromatic():b.order().name()));}list.sort(Comparator.comparing(v->v.get("first")+"\u0000"+v.get("second")));return node(list);}
    static class Sample {
        final SystemStateView state;final Fixture fixture;final List<AtomReference> selection;final List<EvidenceEnvelope> inputs=new ArrayList<>();
        RuleManifest manifest;RuleRequest request;S1ResearchFixtures.Qualified main;ObjectNode binding;int seq;
        Sample(SystemStateView s,Fixture f,boolean geometry)throws Exception{state=s;fixture=f;manifest=RuleRegistry.decode(Files.readAllBytes(RESOURCE.resolve((geometry?GEOM:ID)+".rule.json")));var map=s.components().getFirst().correspondenceAlternatives().getFirst();selection=new ArrayList<>(List.of(map.get("2"),map.getOrDefault("1",new AtomReference("A",2,' ',"1"))));if(geometry)for(var name:List.of("CG","CD1","CE1","CZ","CE2","CD2"))selection.add(new AtomReference("A",5,' ',name));}
        EvidenceEnvelope add(String type,byte[] bytes,ScientificReference method){var e=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"g05-synthetic"),"input-"+seq++,type,bytes,method,state.subject(),totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest.AT,List.of("Synthetic engineering fixture; no production authority"));inputs.add(e);return e;}
        EvidenceEnvelope add(String type,Object value){return add(type,SystemStateView.bytes(value),ref(ScientificReference.Kind.METHOD,"g05-fixture"));}
    }
    Sample sample(String variant,boolean geometry)throws Exception {
        var fixture=sam(variant);var pf=MetPheSurveyAcceptanceTest.chemical(false,3,variant.equals("degenerate")?"degenerate":"valid");
        if(variant.equals("rigid-transform")){fixture=transformed(fixture);pf=transformed(pf);}
        var state=MetPheSurveyAcceptanceTest.state(List.of(fixture,pf),variant.equals("different-state")?"different-state":"valid");
        if(variant.equals("coordinate-charge-conflict")) {var charges=new TreeMap<>(state.charges().charges());charges.put(new AtomReference("A",2,' ',"2"),0);state=new SystemStateView(state.identity(),state.graph(),state.components(),state.sources(),Set.of(),new totah.lab.athena.interaction.perception.FormalChargeAssignments(charges),true,true,state.limitations());}
        var f=new Sample(state,fixture,geometry);
        var component=state.components().getFirst();var map=component.correspondenceAlternatives().getFirst();
        var reference=f.add("athena:source-artifact",Files.readAllBytes(RESOURCE.resolve("CHEBI-142094.mol")),ref(ScientificReference.Kind.METHOD,"original-reference"));
        var original=f.add("athena:source-artifact",state.snapshot());var protocol=f.add("athena:source-artifact",Map.of("protocol","synthetic source facts only; absolute CIP R/S source assertions; no toolkit defaults"));
        var coverage=coverage(state,fixture);
        if(variant.equals("explicit-h"))coverage.path("atomState").forEach(v->((ObjectNode)v).put("hydrogenMode","AUTHORITATIVE_IMPLICIT"));
        if(variant.equals("missing-h"))((ObjectNode)coverage.path("atomState").path("1")).put("hydrogenMode","UNKNOWN").putNull("implicitHydrogenCount");
        var cov=f.add("athena:group-source-coverage",coverage);
        var binding=JSON.createObjectNode();binding.put("schema","athena-sam-source-binding/1");binding.put("definition",ID+"/1");binding.set("stateBinding",node(state.binding()));binding.set("componentReference",node(component.identity()));binding.set("referenceArtifact",node(pin(reference)));binding.set("sourceCoverage",node(pin(cov)));
        var mapping=binding.putObject("referenceAtomMap");for(int i=1;i<=27;i++)if(fixture.graph().atom(""+i).isPresent())mapping.put(""+i,""+i);
        if(variant.equals("partial-map"))mapping.remove("13");
        binding.set("sourceHydrogenAtomIds",node(fixture.graph().atoms().stream().filter(a->a.element().equals("H")).map(MolecularGraph.Atom::id).sorted().toList()));
        String scope=variant.equals("nonordinary")?"KNOWN_NONORDINARY":variant.equals("unknown-scope")?"UNKNOWN":"COMPLETE_ORDINARY";binding.put("sourceScope",scope);
        binding.put("sourceKind",variant.equals("prepared")?"PREPARED_SOURCE":variant.equals("derived")?"DERIVED":"SOURCE");var prep=binding.putArray("preparationReferences");if(Set.of("prepared","derived").contains(variant))prep.add(node(pin(protocol)));
        var facts=binding.putObject("factWitnesses");var coherence=binding.putArray("coherenceWitnesses");binding.putArray("limitations").add("Synthetic source identity and coordinates");
        var factEnvelopes=new ArrayList<EvidenceEnvelope>();
        for(var category:java.util.stream.Stream.concat(CATEGORIES.stream(),java.util.stream.Stream.of("coherence")).toList()) {
            var values=JSON.createObjectNode();
            switch(category) {
                case "elements","isotopes","formalCharges","hydrogens","aromaticity","stereochemistry" -> {
                    var atomValues=values.putObject("atoms");
                    for(var a:fixture.graph().atoms()) {
                        switch(category) {
                            case "elements" -> atomValues.put(a.id(),a.element());
                            case "isotopes" -> atomValues.set(a.id(),node(a.isotope()));
                            case "formalCharges" -> atomValues.put(a.id(),a.formalCharge());
                            case "aromaticity" -> atomValues.put(a.id(),a.aromatic());
                            case "stereochemistry" -> {if(!a.element().equals("H"))atomValues.put(a.id(),variant.equals("relative-stereo")?STEREO.getOrDefault(a.id(),"NONE"):a.stereochemistry());}
                            case "hydrogens" -> {if(!a.element().equals("H")){var h=atomValues.putObject(a.id());h.set("explicitAtomIds",coverage.path("atomState").path(a.id()).path("explicitHydrogenAtomIds"));h.put("authoritativeCount",fixture.hydrogens().get(a.id()));}}
                        }
                    }
                    if(category.equals("aromaticity")){values.put("model","OCL/2026.7.2");values.set("bonds",bonds(fixture.graph(),true));}
                    if(category.equals("stereochemistry"))values.set("protocol",node(pin(protocol)));
                    if(variant.equals("missing-fact")&&category.equals("isotopes"))atomValues.remove("1");
                }
                case "bondOrders" -> values.set("bonds",bonds(fixture.graph(),false));
                case "electronicState" -> values.put("state",variant.equals("radical")?"KNOWN_NON_NONE":"NONE");
                case "connections" -> {values.put("coverage",scope);values.put("ordinaryBondDigest",SystemStateView.digest(fixture.graph().bonds()));var non=values.putArray("nonordinary");if(scope.equals("KNOWN_NONORDINARY")){var c=non.addObject();c.put("first","2");c.put("second","source:metal:1");c.put("kind","source DATIVE record");c.set("source",node(pin(original)));c.put("selector","connection:1");}}
                case "coherence" -> {values.set("stateBinding",node(state.binding()));var selected=new TreeSet<AtomReference>(map.values());if(geometry)selected.addAll(f.selection);values.set("selection",node(selected));values.set("sourceLocations",node(List.of(Map.of("artifact",pin(original),"selector","model:1 explicit coherent source"))));}
            }
            var pins=category.equals("coherence")?coherence:facts.putArray(category);
            if(variant.equals("missing-witness")&&category.equals("stereochemistry"))continue;
            int copies=variant.equals("conflict")&&category.equals("formalCharges")?2:1;
            for(int k=0;k<copies;k++) {
                if(k==1)((ObjectNode)values.path("atoms")).put("2",0);
                var measurement=new TreeMap<String,String>();measurement.put("proposition","ATHENA.G05.SAM_SOURCE_FACT/1");measurement.put("stateBinding",canonical(state.binding()));measurement.put("componentReference",canonical(component.identity()));measurement.put("category",category);measurement.put("factValues",canonical(values));measurement.put("sourceProtocol",canonical(pin(protocol)));measurement.put("sourceLocations",canonical(List.of(Map.of("artifact",pin(original),"selector","category:"+category))));measurement.put("preparationReferences",canonical(prep));
                var method=ref(ScientificReference.Kind.METHOD,"synthetic-g05-source");var i=new EvidenceInterpretation(ref(ScientificReference.Kind.EVIDENCE_INTERPRETATION,"g05-"+category+"-"+k),List.of(new EvidenceInterpretation.Input(original.reference(),original.payloadSha256()),new EvidenceInterpretation.Input(protocol.reference(),protocol.payloadSha256())),method,Map.of(),List.of(state.subject()),SUPPORTED_PRESENT,measurement,List.of("Hand-authored fixture source assertions"),List.of("Not production authority"),Optional.empty(),totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest.AT);
                var e=f.add("athena:event-source",new EvidenceExchange().encodeRecord(i),method);factEnvelopes.add(e);pins.add(node(pin(e)));
            }
        }
        f.binding=binding;f.add("athena:sam-source-binding",binding);
        if(geometry) {
            var helper=new MetPheSurveyAcceptanceTest.Source(state);helper.selected.clear();helper.selected.addAll(f.selection);
            var pc=coverage(state,pf);pc.set("componentReference",node(state.components().get(1).identity()));var ce=helper.add("athena:group-source-coverage",SystemStateView.bytes(pc),ref(ScientificReference.Kind.METHOD,"phe-source"));helper.scope(ce,"COMPLETE_ORDINARY_COVALENT_NO_COORDINATION",false,false);
            var defs=JSON.readTree(f.manifest.parameters().get("sources").value());for(var def:defs){var m=RuleRegistry.decode(SystemStateView.bytes(def));var col=RuleAnalyzers.collector(m,request(state,m),BACKEND);var report=col.analyze(state,List.of(ce,envelope(state,"athena:group-definition",JSON.readTree(m.parameters().get("definition").value()))),Map.of()).getFirst();helper.add("athena:group-identities",report.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8),col.method());}
            MetPheSurveyAcceptanceTest.qualifyScope(helper,temp.resolve(UUID.randomUUID().toString()));f.inputs.addAll(helper.inputs);
            var plan=JSON.createObjectNode();plan.put("schema","athena-continuous-geometry-plan/1");plan.set("stateBinding",node(state.binding()));plan.put("coordinateUnit","ANGSTROM");plan.set("coordinateSourceReferences",node(state.sources()));var group=plan.putArray("groups").addObject();group.put("id","phe");group.set("atoms",node(f.selection.subList(2,8).stream().sorted().toList()));group.set("sourceReferences",node(state.sources()));var ops=plan.putArray("operations");for(int i=0;i<2;i++){var o=ops.addObject();o.put("id",i==0?"sulfur":"methyl");o.put("kind","POINT_PAIR_GROUP");o.set("atoms",node(i==0?List.of(f.selection.get(1),f.selection.get(0)):f.selection.subList(0,2)));o.put("groupId","phe");}plan.putNull("radiusAssignmentReference");plan.set("sourceReferences",node(state.sources()));plan.putArray("limitations").add("Synthetic selected source geometry");
            var pe=f.add("athena:continuous-geometry-plan",plan);var gm=RuleRegistry.decode(f.manifest.parameters().get("geometry").value().getBytes(java.nio.charset.StandardCharsets.UTF_8));var gr=new RuleRequest(state.binding(),gm.key(),RuleRegistry.digest(gm),f.selection.stream().sorted().toList(),List.of(),List.of(),0.1,0,10000,10000);var gc=RuleAnalyzers.collector(gm,gr);var raw=gc.analyze(state,List.of(pe),Map.of()).getFirst();f.add("athena:rule-measurements",raw.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8),gc.method());
        }
        f.main=S1ResearchFixtures.qualify(f.manifest,state,f.selection,temp.resolve(UUID.randomUUID().toString()),"g05-main");f.manifest=f.main.manifest();f.request=f.main.request();
        if(!variant.equals("no-fact-authority")) {
            var base=f.manifest;var src=new ArrayList<RuleManifest.Source>();var bytes=new HashMap<String,byte[]>();
            for(var e:java.util.stream.Stream.concat(factEnvelopes.stream(),java.util.stream.Stream.of(protocol)).toList()){src.add(new RuleManifest.Source("sha256:"+e.payloadSha256(),e.payloadSha256(),"Synthetic exact reviewed source fact/protocol"));bytes.put(e.payloadSha256(),e.readPayload());}
            var sm=new RuleManifest("athena-rule/2","ATHENA.G05.SAM_SOURCE_FACT","1.0.0","SYNTHETIC_G05_SOURCE_FACT",base.family(),base.tier(),"fixture.sam-source-fact","1",SystemGraphCertificate.Status.NOT_EVALUATED,false,List.of(),base.requiredChemistry(),List.of(),base.measurementsProduced(),base.classificationStates(),base.parameters(),src,List.of(),List.of("Synthetic source authority only"),base.negativeCoverage());
            f.inputs.addAll(S1ResearchFixtures.qualify(sm,state,f.selection,temp.resolve(UUID.randomUUID().toString()),"g05-facts",bytes).artifacts());
        }
        return f;
    }
    SystemGraphAnalyzer.Finding evaluate(Sample f,boolean authority)throws Exception {
        var before=SystemStateView.bytes(f.state.snapshot());var col=RuleAnalyzers.collector(f.manifest,f.request);var raw=col.analyze(f.state,f.inputs,Map.of()).getFirst();if(!raw.measurements().containsKey("payload"))return raw;
        var in=new ArrayList<>(f.inputs);in.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"g05-output"),"collected","athena:rule-measurements",raw.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8),col.method(),f.state.subject(),T,List.of()));if(authority)in.addAll(f.main.artifacts());
        var result=RuleAnalyzers.evaluator(f.manifest,f.request).analyze(f.state,in,Map.of()).getFirst();assertArrayEquals(before,SystemStateView.bytes(f.state.snapshot()));return result;
    }
    @ParameterizedTest @ValueSource(strings={"valid","permuted","relative-stereo","aromatic-order","explicit-h","prepared"}) void exactSourceIdentity(String variant)throws Exception {var result=evaluate(sample(variant,false),true);assertEquals(SUPPORTED_PRESENT,result.status(),result.toString());}
    @ParameterizedTest @ValueSource(strings={"ccd","acid","s-epimer","alpha-epimer","ribose-epimer","isotope","radical","nonordinary","wrong-h","extra-bond","derived","sah"}) void knownOutsideIsUnsupported(String variant)throws Exception {var result=evaluate(sample(variant,false),true);assertEquals(UNSUPPORTED,result.status(),result.toString());}
    @ParameterizedTest @ValueSource(strings={"missing-stereo","missing-none","missing-h","missing-fact","missing-witness","unknown-scope","partial-map","conflict","coordinate-charge-conflict","radical-conflict","unknown-electronic"}) void unresolvedCannotBecomeAbsent(String variant)throws Exception {var result=evaluate(sample(variant,false),true);assertEquals(UNKNOWN_INCONCLUSIVE,result.status(),result.toString());}
    @Test void bindingCannotSelfQualify()throws Exception {assertEquals(NOT_EVALUATED,evaluate(sample("no-fact-authority",false),true).status());assertEquals(NOT_EVALUATED,evaluate(sample("valid",false),false).status());}
    @Test void collectorNeverClaimsCurrentIdentity()throws Exception {var f=sample("valid",false);var r=RuleAnalyzers.collector(f.manifest,f.request).analyze(f.state,f.inputs,Map.of()).getFirst();assertEquals(SUPPORTED_PRESENT,r.status());assertEquals("NOT_EVALUATED",JSON.readTree(r.measurements().get("payload")).path("identityStatus").asText());}
    @Test void fourContinuousQuantitiesAreSeparate()throws Exception {var r=evaluate(sample("valid",true),true);assertEquals(SUPPORTED_PRESENT,r.status(),r.toString());var g=JSON.readTree(r.measurements().get("payload")).path("geometry");assertEquals(3,g.path("sulfurCentroidDistance").path("value").doubleValue(),1e-12);assertEquals(4,g.path("methylCentroidDistance").path("value").doubleValue(),1e-12);assertEquals(0,g.path("sulfurNormalAngle").path("value").doubleValue(),1e-12);assertEquals(0,g.path("methylNormalAngle").path("value").doubleValue(),1e-12);}
    @ParameterizedTest @ValueSource(strings={"coplanar","far"}) void noFavorableCutoff(String v)throws Exception {assertEquals(SUPPORTED_PRESENT,evaluate(sample(v,true),true).status());}
    @ParameterizedTest @ValueSource(strings={"degenerate","zero"}) void undefinedOrientationRetainsDistances(String v)throws Exception {var r=evaluate(sample(v,true),true);assertEquals(UNKNOWN_INCONCLUSIVE,r.status(),r.toString());var g=JSON.readTree(r.measurements().get("payload")).path("geometry");assertTrue(g.path("sulfurNormalAngle").path("value").isNull());assertEquals("SUPPORTED_PRESENT",g.path("sulfurCentroidDistance").path("status").asText());}
    @Test void changedReferenceRejected()throws Exception {var f=sample("valid",false);var b=(ObjectNode)f.binding.deepCopy();((ObjectNode)b.path("referenceArtifact")).put("sha256","0".repeat(64));replaceBinding(f,b);assertThrows(IllegalArgumentException.class,()->evaluate(f,true));}
    static void replaceBinding(Sample f,ObjectNode n){f.inputs.removeIf(e->e.evidenceType().equals("athena:sam-source-binding"));f.add("athena:sam-source-binding",n);}
    @Test void duplicateMapRejected()throws Exception {var f=sample("valid",false);var b=f.binding.deepCopy();((ObjectNode)b.path("referenceAtomMap")).put("1","2");replaceBinding(f,b);assertThrows(IllegalArgumentException.class,()->evaluate(f,true));}
    @Test void crossStateReplayRejected()throws Exception {var f=sample("valid",false);var other=sample("different-state",false);other.inputs.clear();other.inputs.addAll(f.inputs);assertThrows(IllegalArgumentException.class,()->evaluate(other,true));}
    @Test void crossDefinitionMeasurementRejected()throws Exception {var f=sample("valid",false);var collector=RuleAnalyzers.collector(f.manifest,f.request);var raw=(ObjectNode)JSON.readTree(collector.analyze(f.state,f.inputs,Map.of()).getFirst().measurements().get("payload"));raw.put("definition",GEOM+"/1");f.add("athena:rule-measurements",SystemStateView.bytes(raw),collector.method());f.inputs.addAll(f.main.artifacts());assertThrows(IllegalArgumentException.class,()->RuleAnalyzers.evaluator(f.manifest,f.request).analyze(f.state,f.inputs,Map.of()));}
    @Test void exactDuplicateEvidenceReordersDeterministically()throws Exception {var f=sample("valid",false);var a=evaluate(f,true);f.inputs.add(f.inputs.getFirst());Collections.reverse(f.inputs);assertEquals(a,evaluate(f,true));}
    @Test void absentBindingCannotConsumeHistoricalEvidence()throws Exception {var f=sample("valid",false);f.inputs.removeIf(e->e.evidenceType().equals("athena:sam-source-binding"));assertEquals(UNKNOWN_INCONCLUSIVE,evaluate(f,true).status());}
    @Test void expiredMainAuthorityNotAccepted()throws Exception {var f=sample("valid",false);var artifacts=new ArrayList<>(f.main.artifacts());var e=artifacts.stream().filter(x->x.evidenceType().equals("athena:rule-qualification-receipt")).findFirst().orElseThrow();var r=totah.lab.athena.system.rules.research.ResearchDocuments.decode(e.readPayload(),totah.lab.athena.system.rules.research.RuleQualificationReceipt.class);var expired=new totah.lab.athena.system.rules.research.RuleQualificationReceipt(r.schema(),r.ruleKey(),r.manifestSha256(),r.eligibility(),r.implementationReport(),r.foundationCertificate(),r.stateBinding(),r.request(),r.qualification(),r.mode(),r.evaluatedAt().plusSeconds(60),r.reasons());artifacts.remove(e);artifacts.add(S1ResearchFixtures.envelope(f.state,"g05-expired","receipt","athena:rule-qualification-receipt",totah.lab.athena.system.rules.research.ResearchDocuments.encode(expired)));f.main=new S1ResearchFixtures.Qualified(f.main.manifest(),f.main.request(),artifacts);assertEquals(NOT_EVALUATED,evaluate(f,true).status());}
    @Test void originalMolfileAbsoluteStereoAndChargeComparison()throws Exception {
        var molecule=new com.actelion.research.chem.StereoMolecule();assertTrue(new com.actelion.research.chem.MolfileParser().parse(molecule,Files.readString(RESOURCE.resolve("CHEBI-142094.mol"))));
        molecule.ensureHelperArrays(com.actelion.research.chem.Molecule.cHelperCIP);
        assertEquals(27,molecule.getAllAtoms());assertEquals(29,molecule.getAllBonds());
        for(var e:STEREO.entrySet())assertEquals(e.getValue().equals("R")?com.actelion.research.chem.Molecule.cAtomCIPParityRorM:com.actelion.research.chem.Molecule.cAtomCIPParitySorP,molecule.getAtomCIPParity(Integer.parseInt(e.getKey())-1),"Original reference center "+e.getKey());
        assertEquals(1,molecule.getAtomCharge(1));assertEquals(1,molecule.getAtomCharge(5));assertEquals(-1,molecule.getAtomCharge(7));
    }
    @Test void exactAnchorSwapRejected()throws Exception {var f=sample("valid",false);var r=f.request;f.request=new RuleRequest(r.state(),r.manifestKey(),r.manifestSha256(),List.of(f.selection.get(1),f.selection.get(0)),r.first(),r.second(),r.radiusAngstrom(),r.maximumHops(),r.maximumNodes(),r.maximumCandidates());assertThrows(IllegalArgumentException.class,()->evaluate(f,true));}
    @Test void unknownBindingFieldsRejected()throws Exception {var f=sample("valid",false);var b=f.binding.deepCopy();b.put("favorable",true);replaceBinding(f,b);assertThrows(IllegalArgumentException.class,()->evaluate(f,true));}
    @Test void preparedLineageCannotDisappear()throws Exception {var f=sample("prepared",false);var b=f.binding.deepCopy();b.putArray("preparationReferences");replaceBinding(f,b);assertThrows(IllegalArgumentException.class,()->evaluate(f,true));}
    @Test void wrongReferenceMapCannotAdmitSulfonium()throws Exception {var f=sample("valid",false);var b=f.binding.deepCopy();((ObjectNode)b.path("referenceAtomMap")).put("3","4").put("4","3");replaceBinding(f,b);assertEquals(UNSUPPORTED,evaluate(f,true).status());}
    @Test void geometryRetainsExactRingAndSourcePins()throws Exception {var f=sample("coplanar",true);var r=evaluate(f,true);var p=JSON.readTree(r.measurements().get("payload"));assertEquals(node(f.state.binding()),p.path("stateBinding"));assertEquals(90,p.path("geometry").path("methylNormalAngle").path("value").doubleValue(),1e-12);assertEquals(0,p.path("geometry").path("sulfurNormalAngle").path("value").doubleValue(),1e-12);assertTrue(p.path("geometry").path("plan").has("sha256"));}
    @Test void ineligibleSamDoesNotBecomeGeometryPositive()throws Exception {assertEquals(UNSUPPORTED,evaluate(sample("ccd",true),true).status());}
    @Test void manifestCannotSelfQualify()throws Exception {var n=(ObjectNode)JSON.readTree(Files.readAllBytes(RESOURCE.resolve(ID+".rule.json")));n.put("qualification","QUALIFIED");assertThrows(java.io.IOException.class,()->RuleRegistry.decode(SystemStateView.bytes(n)));}
    @Test void unselectedCompetingSourceFactCannotBeHidden()throws Exception {
        var f=sample("valid",false);var e=f.inputs.stream().filter(v->v.evidenceType().equals("athena:event-source")).findFirst().orElseThrow();
        f.add(e.evidenceType(),e.readPayload(),e.method());assertEquals(UNKNOWN_INCONCLUSIVE,evaluate(f,true).status());
    }
    @Test void changedRawGeometryRejected()throws Exception {
        var f=sample("valid",true);var e=f.inputs.stream().filter(v->v.evidenceType().equals("athena:rule-measurements")).findFirst().orElseThrow();var changed=(ObjectNode)JSON.readTree(e.readPayload());changed.put("invented","angle");f.inputs.remove(e);f.add(e.evidenceType(),SystemStateView.bytes(changed),e.method());assertThrows(IllegalArgumentException.class,()->evaluate(f,true));
    }
    @Test void geometryCannotUseDifferentRingSelection()throws Exception {
        var f=sample("valid",true);var e=f.inputs.stream().filter(v->v.evidenceType().equals("athena:continuous-geometry-plan")).findFirst().orElseThrow();var changed=(ObjectNode)JSON.readTree(e.readPayload());((ObjectNode)changed.path("groups").get(0)).set("atoms",node(f.selection.subList(2,7)));f.inputs.remove(e);f.add(e.evidenceType(),SystemStateView.bytes(changed),e.method());assertThrows(IllegalArgumentException.class,()->evaluate(f,true));
    }
    @Test void duplicatePayloadKeysRejected()throws Exception {var f=sample("valid",false);f.inputs.removeIf(e->e.evidenceType().equals("athena:sam-source-binding"));f.add("athena:sam-source-binding","{\"schema\":\"athena-sam-source-binding/1\",\"schema\":\"other\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8),ref(ScientificReference.Kind.METHOD,"duplicate-field"));assertThrows(java.io.IOException.class,()->evaluate(f,true));}
    @Test void rigidTransformDoesNotChangeFourQuantities()throws Exception {var a=JSON.readTree(evaluate(sample("valid",true),true).measurements().get("payload")).path("geometry");var b=JSON.readTree(evaluate(sample("rigid-transform",true),true).measurements().get("payload")).path("geometry");for(var key:List.of("sulfurCentroidDistance","methylCentroidDistance","sulfurNormalAngle","methylNormalAngle"))assertEquals(a.path(key),b.path(key));}
    @Test void expiredSourceFactAuthorityRemainsNotEvaluated()throws Exception {var f=sample("valid",false);var e=f.inputs.stream().filter(v->v.evidenceType().equals("athena:rule-qualification-receipt")&&v.reference().id().startsWith("g05-facts/")).findFirst().orElseThrow();var r=totah.lab.athena.system.rules.research.ResearchDocuments.decode(e.readPayload(),totah.lab.athena.system.rules.research.RuleQualificationReceipt.class);var expired=new totah.lab.athena.system.rules.research.RuleQualificationReceipt(r.schema(),r.ruleKey(),r.manifestSha256(),r.eligibility(),r.implementationReport(),r.foundationCertificate(),r.stateBinding(),r.request(),r.qualification(),r.mode(),r.evaluatedAt().plusSeconds(60),r.reasons());f.inputs.remove(e);f.inputs.add(S1ResearchFixtures.envelope(f.state,"g05-facts-expired","receipt","athena:rule-qualification-receipt",totah.lab.athena.system.rules.research.ResearchDocuments.encode(expired)));assertEquals(NOT_EVALUATED,evaluate(f,true).status());}
    @Test void relativeStereoFixtureMatchesExactSourceSmilesThroughExistingBackend()throws Exception {
        var expected=BACKEND.decodeStructure("SMILES","C[S@@+](CC[C@H]([NH3+])C(=O)[O-])C[C@H]1O[C@@H](n2cnc3c(N)ncnc32)[C@H](O)[C@@H]1O");
        var g=sam("relative-stereo").graph();var comparison=new MolecularGraph(g.atoms().stream().map(a->new MolecularGraph.Atom(a.id(),a.element(),a.isotope(),a.formalCharge(),a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),null,a.properties())).toList(),g.bonds(),g.properties());
        assertEquals(BACKEND.identify(expected).canonicalKey(),BACKEND.identify(comparison).canonicalKey());
    }
    public static void main(String[] args)throws Exception {var t=new SamG05AcceptanceTest();t.temp=Files.createDirectories(Path.of(args[0]));var result=List.of(t.evaluate(t.sample("valid",false),true),t.evaluate(t.sample("valid",true),true),t.evaluate(t.sample("degenerate",true),true));Files.write(Path.of(args[1]),SystemStateView.bytes(result));}
}
