package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;

/** Engineering fixtures and synthetic authority only; no actual coordination assignments. */
class ZincCarbonylAcceptanceTest {
    @TempDir Path temp;
    static final Path MANIFEST=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/zinc-carbonyl-v1/ATHENA.I14.ZN2_CARBONYL_PROXIMITY.rule.json");
    record Sample(S1NitrogenAcceptanceTest.Fixture source,S1ResearchFixtures.Qualified qualification,ObjectNode plan,List<EvidenceEnvelope> inputs) { }
    Sample sample(String variant,double distance)throws Exception {
        int charge=variant.equals("neutral-metal")?0:variant.equals("plus-one")?1:variant.equals("plus-three")?3:2;
        var ma=new ArrayList<MolecularGraph.Atom>();ma.add(atom("m",variant.equals("other-metal")?"Mg":"Zn",charge,false,0,0,0));var mb=new ArrayList<MolecularGraph.Bond>();var mh=new TreeMap<String,Integer>();mh.put("m",variant.equals("hydrogen-metal")?1:0);
        if(variant.equals("bonded-metal")){ma.add(atom("c","C",0,false,0,0,1));mb.add(bond("m","c",MolecularGraph.BondOrder.SINGLE));mh.put("c",3);}
        var metal=new Fixture(new MolecularGraph(ma,mb,Map.of()),mh);
        var pa=new ArrayList<MolecularGraph.Atom>();pa.add(atom("o","O",variant.equals("charged-carbonyl")?1:0,false,distance,0,0));pa.add(atom("c","C",0,false,distance+1.2,0,0));var pb=new ArrayList<MolecularGraph.Bond>();pb.add(bond("c","o",variant.equals("alcohol")?MolecularGraph.BondOrder.SINGLE:MolecularGraph.BondOrder.DOUBLE));var ph=new TreeMap<String,Integer>();ph.put("o",variant.equals("alcohol")?1:0);ph.put("c",variant.equals("alcohol")?3:2);
        if(variant.equals("ketone")){ph.put("c",0);for(String id:List.of("r1","r2")){pa.add(atom(id,"C",0,false,distance+2,id.equals("r1")?1:-1,0));pb.add(bond("c",id,MolecularGraph.BondOrder.SINGLE));ph.put(id,3);}}
        var partner=new Fixture(new MolecularGraph(pa,pb,Map.of()),ph);
        String fv=switch(variant){case "missing-none","unknown-scope","known-connection","ambiguous"->variant;case "conflict-scope"->"conflict";default->"primary";};
        var supplied=List.of(metal,partner);
        if(variant.equals("same-component")){var atoms=new ArrayList<>(ma);atoms.addAll(pa);var bonds=new ArrayList<>(mb);bonds.addAll(pb);var hydrogens=new TreeMap<>(mh);hydrogens.putAll(ph);supplied=List.of(new Fixture(new MolecularGraph(atoms,bonds,Map.of()),hydrogens));}
        var f=new S1NitrogenAcceptanceTest.Fixture(fv,supplied);
        if(variant.equals("known-connection"))for(var scope:f.inputs.stream().filter(e->e.evidenceType().equals("athena:event-source")).toList()) {
            var i=(EvidenceInterpretation)new EvidenceExchange().decodeRecord(scope.readPayload());var old=f.inputs.stream().filter(e->e.reference().equals(i.inputs().get(2).reference())).findFirst().orElseThrow();var original=(ObjectNode)JSON.readTree(old.readPayload());
            ((ObjectNode)original.path("explicitOriginalConnection")).put("first","component-source-Zn:m");f.inputs.remove(old);var fresh=f.add("athena:source-artifact",SystemStateView.bytes(original),old.method());var deps=new ArrayList<>(i.inputs());deps.set(2,new EvidenceInterpretation.Input(fresh.reference(),fresh.payloadSha256()));
            var next=new EvidenceInterpretation(i.reference(),deps,i.evaluator(),i.configuration(),i.subjects(),i.status(),i.measurements(),List.of("Exact source-dative-17: component-source-Zn:m -> source-O:external, source DATIVE; no covalent conversion"),i.limitations(),i.supersedes(),i.recordedAt());f.inputs.remove(scope);f.add("athena:event-source",new EvidenceExchange().encodeRecord(next),next.evaluator());
        }
        if(Set.of("missing-charge","missing-h","missing-aromaticity","incomplete-graph").contains(variant)) {
            var previous=f.coverages.getFirst();var c=(ObjectNode)JSON.readTree(previous.readPayload());var fact=(ObjectNode)c.path("atomState").path("m");
            if(variant.equals("missing-charge"))fact.put("chargeStatus","UNKNOWN_INCONCLUSIVE").putNull("formalCharge");
            if(variant.equals("missing-h"))fact.put("hydrogenMode","UNKNOWN").putNull("implicitHydrogenCount");
            if(variant.equals("missing-aromaticity"))fact.put("aromaticityStatus","UNKNOWN_INCONCLUSIVE");
            if(variant.equals("incomplete-graph"))c.put("completeGraph","UNKNOWN_INCONCLUSIVE");
            f.inputs.remove(previous);var fresh=f.add("athena:group-source-coverage",SystemStateView.bytes(c),previous.method());f.coverages.set(0,fresh);
            for(var scope:f.inputs.stream().filter(e->e.evidenceType().equals("athena:event-source")).toList()) {
                var i=(EvidenceInterpretation)new EvidenceExchange().decodeRecord(scope.readPayload());if(!i.inputs().getFirst().reference().equals(previous.reference()))continue;var deps=new ArrayList<>(i.inputs());deps.set(0,new EvidenceInterpretation.Input(fresh.reference(),fresh.payloadSha256()));
                var next=new EvidenceInterpretation(i.reference(),deps,i.evaluator(),i.configuration(),i.subjects(),i.status(),i.measurements(),i.reasons(),i.limitations(),i.supersedes(),i.recordedAt());f.inputs.remove(scope);f.add("athena:event-source",new EvidenceExchange().encodeRecord(next),next.evaluator());
            }
        }
        f.inputs.removeIf(e->e.evidenceType().equals("athena:group-identities"));f.manifest=RuleRegistry.decode(Files.readAllBytes(MANIFEST));
        var role=RuleRegistry.decode(SystemStateView.bytes(JSON.readTree(f.manifest.parameters().get("sources").value()).path("ATHENA.PERCEPTION.ACCEPTOR.CARBONYL_O")));var collector=RuleAnalyzers.collector(role,B01FunctionalGroupAcceptanceTest.request(f.state,role),BACKEND);
        var finding=collector.analyze(f.state,List.of(f.coverages.getLast(),envelope(f.state,"athena:group-definition",JSON.readTree(role.parameters().get("definition").value()))),Map.of()).getFirst();f.add("athena:group-identities",finding.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8),collector.method());
        var mm=f.state.components().getFirst().correspondenceAlternatives().getFirst();var pm=f.state.components().getLast().correspondenceAlternatives().getFirst();var tuple=List.of(mm.get("m"),pm.get("o"),pm.get("c"));
        var q=S1ResearchFixtures.qualify(f.manifest,f.state,tuple,temp,"i14");f.manifest=q.manifest();if(!variant.equals("no-scope-authority"))S1QualifiedProducerTest.qualifyScope(f,temp);
        var plan=ContinuousGeometryAcceptanceTest.plan(f.state);ContinuousGeometryAcceptanceTest.op(plan,"DISTANCE",tuple.get(0),tuple.get(1));var inputs=new ArrayList<>(f.inputs);inputs.add(envelope(f.state,"athena:continuous-geometry-plan",plan));return new Sample(f,q,plan,inputs);
    }
    SystemGraphAnalyzer.Finding evaluate(Sample s,boolean current)throws Exception {
        var f=s.source();var inputs=new ArrayList<>(s.inputs());var collector=RuleAnalyzers.collector(f.manifest,s.qualification().request());var raw=collector.analyze(f.state,inputs,Map.of()).getFirst();inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"i14"),"measurement","athena:rule-measurements",raw.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8),collector.method(),f.state.subject(),T,List.of()));if(current)inputs.addAll(s.qualification().artifacts());return RuleAnalyzers.evaluator(f.manifest,s.qualification().request()).analyze(f.state,inputs,Map.of()).getFirst();
    }
    @ParameterizedTest @ValueSource(strings={"valid","ketone"}) void exactSupportedCarbonyls(String variant)throws Exception {var r=evaluate(sample(variant,2.5),true);assertEquals(EvidenceInterpretation.Status.SUPPORTED_PRESENT,r.status(),r.toString());assertEquals("NOT_EVALUATED",r.measurements().get("coordinationAssignment"));}
    @ParameterizedTest @ValueSource(doubles={2.8,2.8000000000000003,20.0}) void exactBoundaryAndFarNegative(double d)throws Exception {var r=evaluate(sample("valid",d),true);assertEquals(d<=2.8?EvidenceInterpretation.Status.SUPPORTED_PRESENT:EvidenceInterpretation.Status.ABSENT_FALSE,r.status(),r.toString());assertEquals(d,Double.parseDouble(r.measurements().get("distanceAngstrom")));}
    @ParameterizedTest @ValueSource(strings={"neutral-metal","plus-one","plus-three","other-metal","bonded-metal","hydrogen-metal","alcohol","charged-carbonyl","known-connection","same-component"}) void excludedSourceStates(String variant)throws Exception {var r=evaluate(sample(variant,2.5),true);assertEquals(EvidenceInterpretation.Status.UNSUPPORTED,r.status(),r.toString());}
    @ParameterizedTest @ValueSource(strings={"missing-charge","missing-h","missing-aromaticity","incomplete-graph","missing-none","unknown-scope","conflict-scope","ambiguous"}) void unresolvedSourceStates(String variant)throws Exception {var r=evaluate(sample(variant,2.5),true);assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,r.status(),r.toString());}
    @Test void noCurrentAuthority()throws Exception {assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,evaluate(sample("valid",2.5),false).status());}
    @Test void scopeAuthorityIsIndependent()throws Exception {assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,evaluate(sample("no-scope-authority",2.5),true).status());}
    @Test void coincidentAtomsRemainUnknown()throws Exception {assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,evaluate(sample("valid",0),true).status());}
    @ParameterizedTest @ValueSource(strings={"athena:group-identities","athena:event-source"}) void missingEvidenceIsNotNegative(String type)throws Exception {var s=sample("valid",2.5);var inputs=new ArrayList<>(s.inputs());inputs.removeIf(e->e.evidenceType().equals(type));assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,evaluate(new Sample(s.source(),s.qualification(),s.plan(),inputs),true).status());}
    @Test void duplicatesAndOrderAreIdempotent()throws Exception {var s=sample("valid",2.5);var r=evaluate(s,true);var inputs=new ArrayList<>(s.inputs());inputs.add(inputs.getFirst());Collections.reverse(inputs);assertEquals(r,evaluate(new Sample(s.source(),s.qualification(),s.plan(),inputs),true));}
    @Test void changedSelectionPlanRejected()throws Exception {var s=sample("valid",2.5);var p=s.plan().deepCopy();((ObjectNode)p.path("operations").get(0)).set("atoms",JSON.valueToTree(s.qualification().request().atoms().subList(1,3)));var inputs=new ArrayList<>(s.inputs());inputs.removeIf(e->e.evidenceType().equals("athena:continuous-geometry-plan"));inputs.add(envelope(s.source().state,"athena:continuous-geometry-plan",p));assertThrows(IllegalArgumentException.class,()->evaluate(new Sample(s.source(),s.qualification(),p,inputs),true));}
    @Test void noQualificationFlagCanSelfCertify()throws Exception {var n=(ObjectNode)JSON.readTree(Files.readAllBytes(MANIFEST));n.put("qualification","QUALIFIED");assertThrows(java.io.IOException.class,()->RuleRegistry.decode(SystemStateView.bytes(n)));}
    @Test void sourceDefinitionCannotBeReplaced()throws Exception {var n=(ObjectNode)JSON.readTree(Files.readAllBytes(MANIFEST));((ObjectNode)n.path("parameters").path("sources")).put("value","{}");assertThrows(java.io.IOException.class,()->RuleRegistry.decode(SystemStateView.bytes(n)));}
    @Test void contradictoryMetalCoveragesRemainUnknown()throws Exception {var s=sample("valid",2.5);var c=(ObjectNode)JSON.readTree(s.source().coverages.getFirst().readPayload());((ObjectNode)c.path("atomState").path("m")).put("formalCharge",1);var inputs=new ArrayList<>(s.inputs());inputs.add(envelope(s.source().state,"athena:group-source-coverage",c));assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,evaluate(new Sample(s.source(),s.qualification(),s.plan(),inputs),true).status());}
    @Test void alteredBytesUnderTheSameIdentityAreRejected()throws Exception {var s=sample("valid",2.5);var first=s.inputs().getFirst();var replacement=new EvidenceEnvelope(first.reference(),first.evidenceType(),first.payloadFormat(),first.payloadVersion(),Optional.of(Base64.getEncoder().encodeToString("{}".getBytes())),Optional.empty(),EvidenceExchange.sha256("{}".getBytes()),first.provenance(),first.method(),first.context(),first.subjects(),first.qualifications(),first.limitations(),first.recordedAt());var inputs=new ArrayList<>(s.inputs());inputs.add(replacement);assertThrows(IllegalArgumentException.class,()->evaluate(new Sample(s.source(),s.qualification(),s.plan(),inputs),true));}
    public static void main(String[] args)throws Exception {var t=new ZincCarbonylAcceptanceTest();t.temp=Files.createDirectories(Path.of(args[0]));Files.write(Path.of(args[1]),SystemStateView.bytes(t.evaluate(t.sample("valid",2.5),true)));}
}
