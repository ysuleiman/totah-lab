package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;

/** Exact I10 geometry and source scopes; all governance is synthetic test evidence. */
class HalogenCarbonylAcceptanceTest {
    @TempDir Path temp;
    static final Path MANIFEST=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/halogen-carbonyl-v1/ATHENA.I10.CARBON_BOUND_HALOGEN_CARBONYL_DIRECTIONAL_CANDIDATE.rule.json");
    record Sample(S1NitrogenAcceptanceTest.Fixture source,S1ResearchFixtures.Qualified qualification,ObjectNode plan,List<EvidenceEnvelope> inputs) { }
    Sample sample(String element,String variant,double distance,double da,double aa)throws Exception {
        double d=Math.toRadians(da),a=Math.toRadians(aa);
        var donor=new Fixture(new MolecularGraph(List.of(atom("c",variant.equals("nitrogen-bound")?"N":"C",variant.equals("charged-carbon")?1:0,false,Math.cos(d),Math.sin(d),0),atom("x",element,0,false,0,0,0)),List.of(bond("c","x",MolecularGraph.BondOrder.SINGLE)),Map.of()),Map.of("c",variant.equals("nitrogen-bound")?2:3,"x",0));
        if(variant.equals("aromatic")) {
            var atoms=new ArrayList<MolecularGraph.Atom>();var bonds=new ArrayList<MolecularGraph.Bond>();var h=new TreeMap<String,Integer>();
            atoms.add(atom("x",element,0,false,0,0,0));h.put("x",0);
            for(int i=0;i<6;i++){String id=i==0?"c":"r"+i;atoms.add(atom(id,"C",0,true,Math.cos(d)+i,Math.sin(d)+i%2,0));h.put(id,i==0?0:1);String next=i==5?"c":"r"+(i+1);bonds.add(new MolecularGraph.Bond("ring"+i,id,next,MolecularGraph.BondOrder.AROMATIC,true,null,Map.of()));}
            bonds.add(bond("c","x",MolecularGraph.BondOrder.SINGLE));donor=new Fixture(new MolecularGraph(atoms,bonds,Map.of()),h);
        }
        var acceptor=new Fixture(new MolecularGraph(List.of(atom("o","O",0,false,distance,0,0),atom("c","C",0,false,distance-Math.cos(a),Math.sin(a),0)),List.of(bond("c","o",MolecularGraph.BondOrder.DOUBLE)),Map.of()),Map.of("c",2,"o",0));
        var f=new S1NitrogenAcceptanceTest.Fixture(variant.equals("missing-none")?"missing-none":variant.equals("unknown-scope")?"unknown-scope":variant.equals("known-connection")?"known-connection":variant.equals("conflict-scope")?"conflict":variant.equals("ambiguous")?"ambiguous":"primary",List.of(donor,acceptor));
        if(variant.equals("known-connection"))for(var scope:f.inputs.stream().filter(e->e.evidenceType().equals("athena:event-source")).toList()) {
            var i=(EvidenceInterpretation)new EvidenceExchange().decodeRecord(scope.readPayload());var old=f.inputs.stream().filter(e->e.reference().equals(i.inputs().get(2).reference())).findFirst().orElseThrow();var original=(ObjectNode)JSON.readTree(old.readPayload());
            ((ObjectNode)original.path("explicitOriginalConnection")).put("first","component-source-C:c");f.inputs.remove(old);var fresh=f.add("athena:source-artifact",SystemStateView.bytes(original),old.method());var deps=new ArrayList<>(i.inputs());deps.set(2,new EvidenceInterpretation.Input(fresh.reference(),fresh.payloadSha256()));
            var next=new EvidenceInterpretation(i.reference(),deps,i.evaluator(),i.configuration(),i.subjects(),i.status(),i.measurements(),List.of("Exact source-dative-17: component-source-C:c -> source-Zn:external, explicitly DATIVE; no covalent conversion"),i.limitations(),i.supersedes(),i.recordedAt());f.inputs.remove(scope);f.add("athena:event-source",new EvidenceExchange().encodeRecord(next),next.evaluator());
        }
        f.inputs.removeIf(e->e.evidenceType().equals("athena:group-identities"));f.manifest=RuleRegistry.decode(Files.readAllBytes(MANIFEST));
        var sources=JSON.readTree(f.manifest.parameters().get("sources").value());int n=0;
        for(var id:List.of("ATHENA.GROUP.HALOGENATED","ATHENA.PERCEPTION.ACCEPTOR.CARBONYL_O")) {
            var m=RuleRegistry.decode(SystemStateView.bytes(sources.get(id)));var collector=RuleAnalyzers.collector(m,B01FunctionalGroupAcceptanceTest.request(f.state,m),BACKEND);
            var coverage=f.coverages.get(n++);
            if(Set.of("missing-h","missing-charge","missing-aromaticity").contains(variant)&&n==2) {
                var c=(ObjectNode)JSON.readTree(coverage.readPayload());var fact=(ObjectNode)c.path("atomState").path("o");
                if(variant.equals("missing-h"))fact.put("hydrogenMode","UNKNOWN").putNull("implicitHydrogenCount");
                if(variant.equals("missing-charge"))fact.put("chargeStatus","UNKNOWN_INCONCLUSIVE").putNull("formalCharge");
                if(variant.equals("missing-aromaticity"))fact.put("aromaticityStatus","UNKNOWN_INCONCLUSIVE");
                var previous=coverage;f.inputs.remove(previous);coverage=f.add("athena:group-source-coverage",SystemStateView.bytes(c),previous.method());
                for(var scope:f.inputs.stream().filter(e->e.evidenceType().equals("athena:event-source")).toList()) {
                    var i=(EvidenceInterpretation)new EvidenceExchange().decodeRecord(scope.readPayload());
                    if(!i.inputs().getFirst().reference().equals(previous.reference()))continue;
                    var deps=new ArrayList<>(i.inputs());deps.set(0,new EvidenceInterpretation.Input(coverage.reference(),coverage.payloadSha256()));
                    var next=new EvidenceInterpretation(i.reference(),deps,i.evaluator(),i.configuration(),i.subjects(),i.status(),i.measurements(),i.reasons(),i.limitations(),i.supersedes(),i.recordedAt());
                    f.inputs.remove(scope);f.add("athena:event-source",new EvidenceExchange().encodeRecord(next),next.evaluator());
                }
            }
            var finding=collector.analyze(f.state,List.of(coverage,envelope(f.state,"athena:group-definition",JSON.readTree(m.parameters().get("definition").value()))),Map.of()).getFirst();
            f.add("athena:group-identities",finding.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8),collector.method());
        }
        var dm=f.state.components().get(0).correspondenceAlternatives().getFirst();var am=f.state.components().get(1).correspondenceAlternatives().getFirst();var tuple=List.of(dm.get("c"),dm.get("x"),am.get("o"),am.get("c"));
        var q=S1ResearchFixtures.qualify(f.manifest,f.state,tuple,temp,"i10");f.manifest=q.manifest();
        if(!variant.equals("no-scope-authority"))S1QualifiedProducerTest.qualifyScope(f,temp);
        var p=ContinuousGeometryAcceptanceTest.plan(f.state);ContinuousGeometryAcceptanceTest.op(p,"DISTANCE",tuple.get(1),tuple.get(2));ContinuousGeometryAcceptanceTest.op(p,"ANGLE",tuple.get(0),tuple.get(1),tuple.get(2));ContinuousGeometryAcceptanceTest.op(p,"ANGLE",tuple.get(1),tuple.get(2),tuple.get(3));
        var inputs=new ArrayList<>(f.inputs);inputs.add(envelope(f.state,"athena:continuous-geometry-plan",p));return new Sample(f,q,p,inputs);
    }
    SystemGraphAnalyzer.Finding evaluate(Sample s,boolean current)throws Exception {
        var f=s.source();var inputs=new ArrayList<>(s.inputs());var collector=RuleAnalyzers.collector(f.manifest,s.qualification().request());var raw=collector.analyze(f.state,inputs,Map.of()).getFirst();inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"i10"),"measurement","athena:rule-measurements",raw.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8),collector.method(),f.state.subject(),T,List.of()));if(current)inputs.addAll(s.qualification().artifacts());return RuleAnalyzers.evaluator(f.manifest,s.qualification().request()).analyze(f.state,inputs,Map.of()).getFirst();
    }
    @ParameterizedTest @ValueSource(strings={"Cl","Br","I"}) void separateHalogenElements(String element)throws Exception {var r=evaluate(sample(element,"valid",3,165,120),true);assertEquals(EvidenceInterpretation.Status.SUPPORTED_PRESENT,r.status(),r.toString());assertEquals(element,r.measurements().get("halogenElement"));assertEquals("NONAROMATIC_C",r.measurements().get("carbonEnvironment"));}
    @ParameterizedTest @ValueSource(strings={"Cl","Br","I"}) void aromaticCarbonEnvironmentRemainsExplicit(String element)throws Exception {var r=evaluate(sample(element,"aromatic",3,165,120),true);assertEquals(EvidenceInterpretation.Status.SUPPORTED_PRESENT,r.status(),r.toString());assertEquals("AROMATIC_C",r.measurements().get("carbonEnvironment"));}
    @Test void nitrogenBoundHalogenIsNotCarbonDonor()throws Exception {assertEquals(EvidenceInterpretation.Status.UNSUPPORTED,evaluate(sample("Cl","nitrogen-bound",3,165,120),true).status());}
    @ParameterizedTest @ValueSource(strings={"distance","donor","acceptor","far"}) void exactEligibleTupleNegatives(String variant)throws Exception {var r=evaluate(sample("Cl","valid",variant.equals("distance")?3.5001:variant.equals("far")?30:3,variant.equals("donor")?129:165,variant.equals("acceptor")?141:120),true);assertEquals(EvidenceInterpretation.Status.ABSENT_FALSE,r.status(),r.toString());assertTrue(r.measurements().containsKey("distanceAngstrom"));}
    @ParameterizedTest @ValueSource(strings={"missing-none","missing-h","missing-charge","missing-aromaticity","unknown-scope","conflict-scope","ambiguous"}) void missingAndContradictorySources(String variant)throws Exception {var r=evaluate(sample("Cl",variant,3,165,120),true);assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,r.status(),r.toString());}
    @Test void sourceAuthorityAndInteractionAuthorityAreIndependent()throws Exception {assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,evaluate(sample("Cl","no-scope-authority",3,165,120),true).status());}
    @Test void noInteractionAuthority()throws Exception {assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,evaluate(sample("Cl","valid",3,165,120),false).status());}
    @Test void knownNonordinaryIsNotUnknownOrCovalent()throws Exception {assertEquals(EvidenceInterpretation.Status.UNSUPPORTED,evaluate(sample("Cl","known-connection",3,165,120),true).status());}
    @Test void fluorineIsExcluded()throws Exception {assertEquals(EvidenceInterpretation.Status.UNSUPPORTED,evaluate(sample("F","valid",3,165,120),true).status());}
    @Test void chargedCarbonIsExcluded()throws Exception {assertEquals(EvidenceInterpretation.Status.UNSUPPORTED,evaluate(sample("Cl","charged-carbon",3,165,120),true).status());}
    @Test void coincidentTupleIsNotNegative()throws Exception {assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,evaluate(sample("Cl","valid",0,165,120),true).status());}
    @Test void exactDuplicateAndReorderedInputs()throws Exception {var s=sample("Br","valid",3,165,120);var expected=evaluate(s,true);var inputs=new ArrayList<>(s.inputs());inputs.add(inputs.getFirst());Collections.reverse(inputs);assertEquals(expected,evaluate(new Sample(s.source(),s.qualification(),s.plan(),inputs),true));}
    @Test void changedPlanCannotRelabelTuple()throws Exception {var s=sample("Cl","valid",3,165,120);var p=s.plan().deepCopy();((ObjectNode)p.path("operations").get(1)).put("kind","DIHEDRAL");var inputs=new ArrayList<>(s.inputs());inputs.removeIf(e->e.evidenceType().equals("athena:continuous-geometry-plan"));inputs.add(envelope(s.source().state,"athena:continuous-geometry-plan",p));assertThrows(IllegalArgumentException.class,()->evaluate(new Sample(s.source(),s.qualification(),p,inputs),true));}
    @Test void qualificationFlagCannotSelfCertify()throws Exception {var n=(ObjectNode)JSON.readTree(Files.readAllBytes(MANIFEST));n.put("qualification","QUALIFIED");assertThrows(java.io.IOException.class,()->RuleRegistry.decode(SystemStateView.bytes(n)));}
    @Test void sourceDefinitionCannotBeChanged()throws Exception {var n=(ObjectNode)JSON.readTree(Files.readAllBytes(MANIFEST));((ObjectNode)n.path("parameters").path("sources")).put("value","{}");assertThrows(java.io.IOException.class,()->RuleRegistry.decode(SystemStateView.bytes(n)));}
    @Test void conflictingBytesUnderSameEnvelopeIdentityAreRejected()throws Exception {var s=sample("Cl","valid",3,165,120);var first=s.inputs().getFirst();var replacement=new EvidenceEnvelope(first.reference(),first.evidenceType(),first.payloadFormat(),first.payloadVersion(),Optional.of(Base64.getEncoder().encodeToString("{}".getBytes())),Optional.empty(),EvidenceExchange.sha256("{}".getBytes()),first.provenance(),first.method(),first.context(),first.subjects(),first.qualifications(),first.limitations(),first.recordedAt());var inputs=new ArrayList<>(s.inputs());inputs.add(replacement);assertThrows(IllegalArgumentException.class,()->evaluate(new Sample(s.source(),s.qualification(),s.plan(),inputs),true));}
    @Test void missingOriginalSourceCannotBeReplacedByReceipt()throws Exception {var s=sample("Cl","valid",3,165,120);var scope=s.inputs().stream().filter(e->e.evidenceType().equals("athena:event-source")).findFirst().orElseThrow();var i=(EvidenceInterpretation)new EvidenceExchange().decodeRecord(scope.readPayload());var inputs=new ArrayList<>(s.inputs());inputs.removeIf(e->e.reference().equals(i.inputs().get(2).reference()));assertThrows(IllegalArgumentException.class,()->evaluate(new Sample(s.source(),s.qualification(),s.plan(),inputs),true));}
    @Test void expiredInteractionReceiptIsNotCurrentAuthority()throws Exception {var s=sample("Cl","valid",3,165,120);var artifacts=new ArrayList<>(s.qualification().artifacts());var e=artifacts.stream().filter(x->x.evidenceType().equals("athena:rule-qualification-receipt")).findFirst().orElseThrow();var r=totah.lab.athena.system.rules.research.ResearchDocuments.decode(e.readPayload(),totah.lab.athena.system.rules.research.RuleQualificationReceipt.class);var expired=new totah.lab.athena.system.rules.research.RuleQualificationReceipt(r.schema(),r.ruleKey(),r.manifestSha256(),r.eligibility(),r.implementationReport(),r.foundationCertificate(),r.stateBinding(),r.request(),r.qualification(),r.mode(),r.evaluatedAt().plusSeconds(60),r.reasons());artifacts.remove(e);artifacts.add(S1ResearchFixtures.envelope(s.source().state,"i10-expired","receipt","athena:rule-qualification-receipt",totah.lab.athena.system.rules.research.ResearchDocuments.encode(expired)));var q=new S1ResearchFixtures.Qualified(s.qualification().manifest(),s.qualification().request(),artifacts);assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,evaluate(new Sample(s.source(),q,s.plan(),s.inputs()),true).status());}
    @ParameterizedTest @ValueSource(strings={"athena:group-identities","athena:event-source"})
    void missingEvidenceDoesNotBecomeNegative(String type)throws Exception {var s=sample("Cl","valid",3,165,120);var inputs=new ArrayList<>(s.inputs());inputs.removeIf(e->e.evidenceType().equals(type));assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,evaluate(new Sample(s.source(),s.qualification(),s.plan(),inputs),true).status());}
    public static void main(String[] args)throws Exception {var t=new HalogenCarbonylAcceptanceTest();t.temp=Files.createDirectories(Path.of(args[0]));Files.write(Path.of(args[1]),SystemStateView.bytes(t.evaluate(t.sample("Br","valid",3,165,120),true)));}
}
