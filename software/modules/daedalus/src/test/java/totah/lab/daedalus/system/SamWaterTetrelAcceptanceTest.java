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

import static totah.lab.daedalus.system.SamG05AcceptanceTest.*;

/** Synthetic source and review fixtures only; no production authority. */
class SamWaterTetrelAcceptanceTest {
    @TempDir Path temp;
    static final Path G06_RESOURCE=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/sam-water-tetrel-v1/ATHENA.G06.SAM_WATER_METHYL_TETREL_GEOMETRIC_CANDIDATE.rule.json");
    Sample sample(String variant,double distance,double angle)throws Exception {
        var fixture=sam(variant);
        var wf=WaterBridgeFixtures.water(0,true);
        double rad=Math.toRadians(angle),x=distance*Math.sin(rad),z=4-distance*Math.cos(rad);
        wf=WaterBridgeFixtures.position(wf,"o",x,0,z);wf=WaterBridgeFixtures.position(wf,"h1",x+1,0,z);wf=WaterBridgeFixtures.position(wf,"h2",x,1,z);
        if(variant.equals("charged-water"))wf=WaterBridgeFixtures.charge(wf,"o",1);
        if(variant.equals("coincident-h"))wf=WaterBridgeFixtures.position(wf,"h1",x,0,z);
        var state=MetPheSurveyAcceptanceTest.state(List.of(fixture,wf),variant.equals("unqualified-frame")?variant:"valid");
        var f=new Sample(state,fixture,false);var waterMap=state.components().get(1).correspondenceAlternatives().getFirst();
        f.selection.add(waterMap.get("o"));f.selection.addAll(List.of(waterMap.get("h1"),waterMap.get("h2")).stream().sorted().toList());
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
                case "coherence" -> {values.set("stateBinding",node(state.binding()));var selected=new TreeSet<AtomReference>(map.values());if(!variant.equals("missing-coherence"))selected.addAll(f.selection);values.set("selection",node(selected));values.set("sourceLocations",node(List.of(Map.of("artifact",pin(original),"selector","model:1 explicit coherent source"))));}
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
        var samAuthority=S1ResearchFixtures.qualify(f.manifest,state,f.selection.subList(0,2),temp.resolve(UUID.randomUUID().toString()),"g06-sam");
        if(!variant.equals("no-sam-authority"))f.inputs.addAll(samAuthority.artifacts());
        if(!variant.equals("no-fact-authority")) {
            var base=f.manifest;var src=new ArrayList<RuleManifest.Source>();var bytes=new HashMap<String,byte[]>();
            for(var e:java.util.stream.Stream.concat(factEnvelopes.stream(),java.util.stream.Stream.of(protocol)).toList()){src.add(new RuleManifest.Source("sha256:"+e.payloadSha256(),e.payloadSha256(),"Synthetic exact reviewed source fact/protocol"));bytes.put(e.payloadSha256(),e.readPayload());}
            var sm=new RuleManifest("athena-rule/2","ATHENA.G05.SAM_SOURCE_FACT","1.0.0","SYNTHETIC_G05_SOURCE_FACT",base.family(),base.tier(),"fixture.sam-source-fact","1",SystemGraphCertificate.Status.NOT_EVALUATED,false,List.of(),base.requiredChemistry(),List.of(),base.measurementsProduced(),base.classificationStates(),base.parameters(),src,List.of(),List.of("Synthetic source authority only"),base.negativeCoverage());
            f.inputs.addAll(S1ResearchFixtures.qualify(sm,state,f.selection,temp.resolve(UUID.randomUUID().toString()),"g05-facts",bytes).artifacts());
        }
        f.manifest=RuleRegistry.decode(Files.readAllBytes(G06_RESOURCE));
        var wm=RuleRegistry.decode(SystemStateView.bytes(JSON.readTree(f.manifest.parameters().get("sources").value()).get("ATHENA.GROUP.WATER.NEUTRAL_H2")));
        var wc=coverage(state,wf);wc.set("componentReference",node(state.components().get(1).identity()));wc.path("atomState").forEach(v->((ObjectNode)v).put("hydrogenMode","EXPLICIT_GRAPH"));
        if(variant.equals("missing-water-h"))((ObjectNode)wc.path("atomState").path("o")).put("hydrogenMode","UNKNOWN").putNull("implicitHydrogenCount");
        var we=f.add("athena:group-source-coverage",wc);var col=RuleAnalyzers.collector(wm,request(state,wm),BACKEND);
        var wr=col.analyze(state,List.of(we,envelope(state,"athena:group-definition",JSON.readTree(wm.parameters().get("definition").value()))),Map.of()).getFirst();
        var report=f.add("athena:group-identities",wr.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8),col.method());
        var wn=(ObjectNode)JSON.valueToTree(wm);var ws=wn.putArray("scientificSources");var sourceBytes=new HashMap<String,byte[]>();
        for(var e:List.of(we,report)){ws.addObject().put("locator","sha256:"+e.payloadSha256()).put("sha256",e.payloadSha256()).put("citation","Synthetic independently reviewed water source");sourceBytes.put(e.payloadSha256(),e.readPayload());}
        var wq=S1ResearchFixtures.qualify(RuleRegistry.decode(SystemStateView.bytes(wn)),state,List.of(),temp.resolve(UUID.randomUUID().toString()),"g06-water",sourceBytes);
        if(!variant.equals("no-water-authority"))f.inputs.addAll(wq.artifacts());
        var plan=JSON.createObjectNode();plan.put("schema","athena-continuous-geometry-plan/1");plan.set("stateBinding",node(state.binding()));plan.put("coordinateUnit","ANGSTROM");plan.set("coordinateSourceReferences",node(state.sources()));plan.putArray("groups");var ops=plan.putArray("operations");
        ops.addObject().put("id","distance").put("kind","DISTANCE").set("atoms",node(f.selection.subList(1,3)));
        ops.addObject().put("id","angle").put("kind","ANGLE").set("atoms",node(f.selection.subList(0,3)));
        plan.putNull("radiusAssignmentReference");plan.set("sourceReferences",node(state.sources()));plan.putArray("limitations").add("Synthetic geometry only");
        var pe=f.add("athena:continuous-geometry-plan",plan);var gm=RuleRegistry.decode(f.manifest.parameters().get("geometry").value().getBytes(java.nio.charset.StandardCharsets.UTF_8));var gr=new RuleRequest(state.binding(),gm.key(),RuleRegistry.digest(gm),f.selection.subList(0,3).stream().sorted().toList(),List.of(),List.of(),0.1,0,10000,10000);var gc=RuleAnalyzers.collector(gm,gr);var raw=gc.analyze(state,List.of(pe),Map.of()).getFirst();f.add("athena:rule-measurements",raw.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8),gc.method());
        f.main=S1ResearchFixtures.qualify(f.manifest,state,f.selection,temp.resolve(UUID.randomUUID().toString()),"g06-main");f.manifest=f.main.manifest();f.request=f.main.request();
        return f;
    }
    SystemGraphAnalyzer.Finding evaluate(Sample f,boolean authority)throws Exception {
        var before=SystemStateView.bytes(f.state.snapshot());var col=RuleAnalyzers.collector(f.manifest,f.request);var raw=col.analyze(f.state,f.inputs,Map.of()).getFirst();if(!raw.measurements().containsKey("payload"))return raw;
        var in=new ArrayList<>(f.inputs);in.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"g05-output"),"collected","athena:rule-measurements",raw.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8),col.method(),f.state.subject(),T,List.of()));if(authority)in.addAll(f.main.artifacts());
        var result=RuleAnalyzers.evaluator(f.manifest,f.request).analyze(f.state,in,Map.of()).getFirst();assertArrayEquals(before,SystemStateView.bytes(f.state.snapshot()));return result;
    }
    @Test void selectedPositiveAndSeparateQuantities()throws Exception {var r=evaluate(sample("valid",3,170),true);assertEquals(SUPPORTED_PRESENT,r.status(),r.toString());var n=JSON.readTree(r.measurements().get("payload"));assertEquals("athena-sam-water-tetrel-measurements/1",n.path("schema").asText());assertEquals("SUPPORTED_PRESENT",n.path("samIdentityStatus").asText());assertEquals("SUPPORTED_PRESENT",n.path("waterIdentityStatus").asText());assertEquals(3,n.path("geometry").path("methylOxygenDistance").path("value").asDouble(),1e-12);assertEquals(170,n.path("geometry").path("sulfurMethylOxygenAngle").path("value").asDouble(),1e-12);}
    @ParameterizedTest @ValueSource(strings={"no-sam-authority","no-water-authority","no-fact-authority"}) void independentAuthority(String v)throws Exception {var r=evaluate(sample(v,3,170),true);assertEquals(NOT_EVALUATED,r.status(),r.toString());}
    @Test void noMainAuthority()throws Exception {assertEquals(NOT_EVALUATED,evaluate(sample("valid",3,170),false).status());}
    @ParameterizedTest @ValueSource(strings={"ccd","s-epimer","isotope","nonordinary","charged-water"}) void excluded(String v)throws Exception {var r=evaluate(sample(v,3,170),true);assertEquals(UNSUPPORTED,r.status(),r.toString());}
    @ParameterizedTest @ValueSource(strings={"unknown-scope","missing-stereo","missing-coherence","missing-water-h","coincident-h","unqualified-frame"}) void unknown(String v)throws Exception {var r=evaluate(sample(v,3,170),true);assertEquals(UNKNOWN_INCONCLUSIVE,r.status(),r.toString());}
    @Test void distanceCannotSubstituteAngle()throws Exception {assertEquals(ABSENT_FALSE,evaluate(sample("valid",3,150),true).status());}
    @Test void angleCannotSubstituteDistance()throws Exception {assertEquals(ABSENT_FALSE,evaluate(sample("valid",3.5,170),true).status());}
    @Test void inclusiveDistanceAndLinearity()throws Exception {assertEquals(SUPPORTED_PRESENT,evaluate(sample("valid",3.25,180),true).status());}
    @Test void zeroVectorRetainsDistance()throws Exception {var r=evaluate(sample("valid",0,170),true);assertEquals(UNKNOWN_INCONCLUSIVE,r.status(),r.toString());var g=JSON.readTree(r.measurements().get("payload")).path("geometry");assertEquals(0,g.path("methylOxygenDistance").path("value").asDouble());assertTrue(g.path("sulfurMethylOxygenAngle").path("value").isNull());}
    @Test void collectionDoesNotQualify()throws Exception {var f=sample("valid",3,170);var r=RuleAnalyzers.collector(f.manifest,f.request).analyze(f.state,f.inputs,Map.of()).getFirst();var n=JSON.readTree(r.measurements().get("payload"));for(var key:List.of("assessment","samIdentityStatus","waterIdentityStatus"))assertEquals("NOT_EVALUATED",n.path(key).asText());}
    @Test void reorderedInputs()throws Exception {var f=sample("valid",3,170);var first=evaluate(f,true);Collections.reverse(f.inputs);assertEquals(first,evaluate(f,true));}
    @Test void anchorSwapRejected()throws Exception {var f=sample("valid",3,170);var t=new ArrayList<>(f.request.atoms());Collections.swap(t,0,1);f.request=new RuleRequest(f.state.binding(),f.manifest.key(),RuleRegistry.digest(f.manifest),t,List.of(),List.of(),.1,0,10000,10000);assertThrows(IllegalArgumentException.class,()->evaluate(f,true));}
    @Test void changedDefinitionRejected()throws Exception {var f=sample("valid",3,170);var n=(ObjectNode)JSON.valueToTree(f.manifest);((ObjectNode)n.path("parameters").path("definition")).put("value","another-definition");assertThrows(java.io.IOException.class,()->new RuleRegistry().register(RuleRegistry.decode(SystemStateView.bytes(n))));}
    @Test void scalarBoundariesAreExact()throws Exception {
        var c=Class.forName("totah.lab.athena.system.rules.SamWaterTetrelRules");var predicate=c.getDeclaredMethod("predicate",Double.class,Double.class);predicate.setAccessible(true);
        for(double d:new double[]{Math.nextDown(3.25),3.25,Math.nextUp(3.25)})for(double a:new double[]{Math.nextDown(160.0),160,Math.nextUp(160.0),180})assertEquals(d<=3.25&&a>=160?SUPPORTED_PRESENT:ABSENT_FALSE,predicate.invoke(null,d,a));
        for(Double a:Arrays.asList(null,Double.NaN,Double.POSITIVE_INFINITY,Math.nextUp(180.0)))assertEquals(UNKNOWN_INCONCLUSIVE,predicate.invoke(null,3.0,a));
    }
    @Test void waterIdentityOverloadParity()throws Exception {
        var ic=Class.forName("totah.lab.athena.system.rules.WaterBridgeInputs");var cc=Class.forName("totah.lab.athena.system.rules.HbondCandidateSources");var wc=Class.forName("totah.lab.athena.system.rules.WaterIdentity");
        var ctor=ic.getDeclaredConstructors()[0];ctor.setAccessible(true);var field=ic.getDeclaredField("chemistry");field.setAccessible(true);
        var old=wc.getDeclaredMethod("assess",SystemStateView.class,ic,AtomReference.class);old.setAccessible(true);var direct=wc.getDeclaredMethod("assess",SystemStateView.class,cc,AtomReference.class);direct.setAccessible(true);
        for(var variant:List.of("valid","H","charge","graph")){var f=new WaterBridgeFixtures(WaterBridgeFixtures.chain(1),false,variant.equals("valid")?Map.of():Map.of(1,variant));var input=ctor.newInstance(f.state,f.manifest,f.request(),f.inputs());var o=new AtomReference("A",2,' ',"o");assertEquals(old.invoke(null,f.state,input,o),direct.invoke(null,f.state,field.get(input),o));}
    }
    @Test void suppliedPreparedSourceRetainsLineage()throws Exception {var f=sample("prepared",3,170);var r=evaluate(f,true);assertEquals(SUPPORTED_PRESENT,r.status());assertEquals("PREPARED_SOURCE",f.binding.path("sourceKind").asText());assertFalse(f.binding.path("preparationReferences").isEmpty());}
    @Test void forgedOriginalMeasurementRejected()throws Exception {var f=sample("valid",3,170);var e=f.inputs.stream().filter(x->x.evidenceType().equals("athena:rule-measurements")).findFirst().orElseThrow();var n=(ObjectNode)JSON.readTree(e.readPayload());n.put("forged",true);f.inputs.remove(e);f.add(e.evidenceType(),SystemStateView.bytes(n),e.method());assertThrows(IllegalArgumentException.class,()->evaluate(f,true));}
    @Test void changedBindingDigestRejected()throws Exception {var f=sample("valid",3,170);var n=f.binding.deepCopy();((ObjectNode)n.path("referenceArtifact")).put("sha256","0".repeat(64));f.inputs.removeIf(e->e.evidenceType().equals("athena:sam-source-binding"));f.add("athena:sam-source-binding",n);assertThrows(IllegalArgumentException.class,()->evaluate(f,true));}
    @Test void crossStateBindingRejected()throws Exception {var f=sample("valid",3,170);var n=f.binding.deepCopy();n.set("stateBinding",node(Map.of()));f.inputs.removeIf(e->e.evidenceType().equals("athena:sam-source-binding"));f.add("athena:sam-source-binding",n);assertThrows(Exception.class,()->evaluate(f,true));}
    @Test void unreviewedWaterReportCannotQualify()throws Exception {var f=sample("valid",3,170);f.inputs.removeIf(e->e.evidenceType().equals("athena:group-identities"));assertEquals(UNKNOWN_INCONCLUSIVE,evaluate(f,true).status());}
    @Test void geometryPlanDifferentTupleRejected()throws Exception {var f=sample("valid",3,170);var e=f.inputs.stream().filter(x->x.evidenceType().equals("athena:continuous-geometry-plan")).findFirst().orElseThrow();var n=(ObjectNode)JSON.readTree(e.readPayload());((ObjectNode)n.path("operations").get(0)).set("atoms",node(f.selection.subList(0,2)));f.inputs.remove(e);f.add(e.evidenceType(),n);assertThrows(IllegalArgumentException.class,()->evaluate(f,true));}
    @Test void callerClassificationCannotReplaceReplay()throws Exception {var f=sample("valid",3,170);var c=RuleAnalyzers.collector(f.manifest,f.request);var raw=JSON.readTree(c.analyze(f.state,f.inputs,Map.of()).getFirst().measurements().get("payload"));((ObjectNode)raw).put("assessment","SUPPORTED_PRESENT");f.add("athena:rule-measurements",SystemStateView.bytes(raw),c.method());f.inputs.addAll(f.main.artifacts());assertThrows(IllegalArgumentException.class,()->RuleAnalyzers.evaluator(f.manifest,f.request).analyze(f.state,f.inputs,Map.of()));}
    @Test void exactPayloadFieldSetAndEvidencePins()throws Exception {var f=sample("valid",3,170);var n=JSON.readTree(evaluate(f,true).measurements().get("payload"));var fields=new TreeSet<String>();n.fieldNames().forEachRemaining(fields::add);assertEquals(new TreeSet<>(List.of("schema","definition","definitionSha256","stateBinding","requestSha256","samSourceBinding","sourcePins","tuple","samIdentityStatus","waterIdentityStatus","geometry","assessment","reasons","limitations")),fields);assertEquals(SystemStateView.digest(f.request),n.path("requestSha256").asText());for(var e:f.inputs)assertTrue(n.path("sourcePins").toString().contains(e.payloadSha256()));assertEquals(2,n.path("tuple").path("waterHydrogens").size());}
    @Test void unknownSourceCannotBecomeCompleteNegative()throws Exception {assertEquals(UNKNOWN_INCONCLUSIVE,evaluate(sample("unknown-scope",4,120),true).status());}
    @Test void changedGeometryDefinitionRejected()throws Exception {var f=sample("valid",3,170);var n=(ObjectNode)JSON.valueToTree(f.manifest);((ObjectNode)n.path("parameters").path("geometry")).put("value","{}");assertThrows(java.io.IOException.class,()->RuleRegistry.decode(SystemStateView.bytes(n)));}
    @Test void conflictingSourceEvidenceCannotQualify()throws Exception {var f=sample("valid",3,170);var e=f.inputs.stream().filter(x->x.evidenceType().equals("athena:group-identities")).findFirst().orElseThrow();f.add(e.evidenceType(),e.readPayload(),e.method());assertThrows(IllegalArgumentException.class,()->evaluate(f,true));}
    @Test void missingCoverageCannotSelfQualifyWaterReport()throws Exception {var f=sample("valid",3,170);var waterComponent=node(f.state.components().get(1).identity());var remove=new ArrayList<EvidenceEnvelope>();for(var e:f.inputs)if(e.evidenceType().equals("athena:group-source-coverage")&&JSON.readTree(e.readPayload()).path("componentReference").equals(waterComponent))remove.add(e);f.inputs.removeAll(remove);assertEquals(NOT_EVALUATED,evaluate(f,true).status());}
    @Test void missingPolicyAndConflictingPolicyFailClosed()throws Exception {
        var f=sample("valid",3,170);var main=new ArrayList<>(f.main.artifacts());main.removeIf(e->e.evidenceType().equals("athena:rule-policy-context"));f.inputs.removeIf(e->e.evidenceType().equals("athena:rule-policy-context"));f.inputs.addAll(main);assertEquals(NOT_EVALUATED,evaluate(f,false).status());
        var other=sample("valid",3,170);var c=other.main.artifacts().stream().filter(e->e.evidenceType().equals("athena:rule-policy-context")).findFirst().orElseThrow();var n=(ObjectNode)JSON.readTree(c.readPayload());n.put("asOf","2099-01-01T00:00:00Z");other.add(c.evidenceType(),n);assertEquals(NOT_EVALUATED,evaluate(other,true).status());
    }
    @Test void historicalReceiptCannotSupplyCurrentAdmission()throws Exception {var f=sample("valid",3,170);var candidates=new ArrayList<EvidenceEnvelope>();for(var e:f.inputs)if(e.evidenceType().equals("athena:rule-qualification-receipt")&&JSON.readTree(e.readPayload()).path("ruleKey").asText().startsWith(ID))candidates.add(e);assertFalse(candidates.isEmpty());for(var e:candidates){var n=(ObjectNode)JSON.readTree(e.readPayload());n.put("mode","HISTORICAL_REPLAY");f.inputs.remove(e);f.add(e.evidenceType(),n);}assertEquals(NOT_EVALUATED,evaluate(f,true).status());}
    @Test void duplicateJsonKeyRejected()throws Exception {var f=sample("valid",3,170);var e=f.inputs.stream().filter(x->x.evidenceType().equals("athena:sam-source-binding")).findFirst().orElseThrow();String n=new String(e.readPayload(),java.nio.charset.StandardCharsets.UTF_8);n=n.replaceFirst("\\{","{\"schema\":\"forged\",");f.inputs.remove(e);f.add(e.evidenceType(),n.getBytes(java.nio.charset.StandardCharsets.UTF_8),e.method());assertThrows(Exception.class,()->evaluate(f,true));}
    public static void main(String[] args)throws Exception {var test=new SamWaterTetrelAcceptanceTest();test.temp=Files.createDirectories(Path.of(args[0]));var out=new TreeMap<String,Object>();for(var v:List.of("valid","no-water-authority","charged-water")){var r=test.evaluate(test.sample(v,3,170),true);out.put(v,JSON.readTree(r.measurements().get("payload")));}out.put("zero",JSON.readTree(test.evaluate(test.sample("valid",0,170),true).measurements().get("payload")));Files.write(Path.of(args[1]),SystemStateView.bytes(out));}
}
