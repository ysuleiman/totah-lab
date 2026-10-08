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
/** Independently declared thione and ketone engineering graphs; no assay or production authority. */
class AdvisoryAlertAcceptanceTest {
    @TempDir Path temp;
    static final Path MANIFEST=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/advisory-alert-v1/ATHENA.A07.OCL_PAINS_ADVISORY_OCCURRENCES.rule.json");
    static final Path CATALOG=MANIFEST.resolveSibling("ocl-pains-2026.7.2.json");
    record Sample(S1NitrogenAcceptanceTest.Fixture source,S1ResearchFixtures.Qualified qualification,List<EvidenceEnvelope> inputs) { }
    static Fixture chemical(String variant)throws Exception{
        if(variant.startsWith("entry:")){
            int entry=Integer.parseInt(variant.substring(6));String smiles;int[] counts;
            switch(entry){
                case 113->{smiles="N#CC(C#N)C(C#N)C#N";counts=new int[]{0,0,1,0,0,1,0,0,0,0};}
                case 159->{smiles="N#CC(=Cc1ccccc1)C#N";counts=new int[]{0,0,0,1,0,1,1,1,1,1,0,0};}
                case 162->{smiles="N#CC(=C(S)S)C#N";counts=new int[]{0,0,0,0,1,1,0,0};}
                case 169->{smiles="C=C1C(=O)NNC1=O";counts=new int[]{2,0,0,0,1,1,0,0};}
                case 202->{smiles="C#CC(=O)C#C";counts=new int[]{1,0,0,0,0,1};}
                case 207->{smiles="C=C1SC(=S)NC1=O";counts=new int[]{2,0,0,0,0,1,0,0};}
                case 840->{smiles="CC(=S)C";counts=new int[]{3,0,0,3};}
                default->throw new IllegalArgumentException("unreviewed engineering witness");
            }
            var g=BACKEND.decodeStructure("SMILES",smiles);assertEquals(counts.length,g.atoms().size());var atoms=new ArrayList<MolecularGraph.Atom>();var h=new TreeMap<String,Integer>();
            for(int i=0;i<counts.length;i++){var a=g.atoms().get(i);h.put(a.id(),counts[i]);atoms.add(new MolecularGraph.Atom(a.id(),a.element(),a.isotope(),a.formalCharge(),counts[i],a.aromatic(),a.stereochemistry(),new MolecularGraph.Coordinates(i,0,0),a.properties()));}
            return new Fixture(new MolecularGraph(atoms,g.bonds(),g.properties()),h);
        }
        var atoms=new ArrayList<MolecularGraph.Atom>();var hs=Map.of("a0",3,"a1",0,"a2",0,"a3",3);
        for(int i=0;i<4;i++){String id="a"+i;var a=atom(id,i==2?(variant.equals("negative")?"O":"S"):"C",0,false,i,0,0);atoms.add(new MolecularGraph.Atom(id,a.element(),null,0,variant.equals("unspecified-h")?0:hs.get(id),false,a.stereochemistry(),a.coordinates(),Map.of()));}
        return new Fixture(new MolecularGraph(atoms,List.of(bond("a0","a1",MolecularGraph.BondOrder.SINGLE),bond("a1","a2",MolecularGraph.BondOrder.DOUBLE),bond("a1","a3",MolecularGraph.BondOrder.SINGLE)),Map.of()),hs);
    }
    Sample sample(String variant)throws Exception{
        var f=new S1NitrogenAcceptanceTest.Fixture(Set.of("missing-h","missing-none","missing-scope","unknown-scope","conflict").contains(variant)?variant:"primary",List.of(chemical(variant)));f.inputs.removeIf(e->e.evidenceType().equals("athena:group-identities"));
        if(Set.of("missing-charge","missing-aromaticity","incomplete-graph","contradictory-h").contains(variant)){
            var old=f.coverages.getFirst();var n=(ObjectNode)JSON.readTree(old.readPayload());var a=(ObjectNode)n.path("atomState").path("a0");
            if(variant.equals("missing-charge"))a.put("chargeStatus","UNKNOWN_INCONCLUSIVE").putNull("formalCharge");
            if(variant.equals("missing-aromaticity"))a.put("aromaticityStatus","UNKNOWN_INCONCLUSIVE");
            if(variant.equals("incomplete-graph"))n.put("completeGraph","UNKNOWN_INCONCLUSIVE");
            if(variant.equals("contradictory-h"))a.put("implicitHydrogenCount",4);
            f.inputs.remove(old);var fresh=f.add("athena:group-source-coverage",SystemStateView.bytes(n),old.method());f.coverages.set(0,fresh);f.inputs.removeIf(e->e.evidenceType().equals("athena:event-source"));f.scope(fresh,"COMPLETE_ORDINARY_COVALENT_NO_COORDINATION",false,false);
        }
        f.add("athena:advisory-alert-catalog",Files.readAllBytes(CATALOG),ref(ScientificReference.Kind.METHOD,"pinned-native-catalog"));
        f.manifest=RuleRegistry.decode(Files.readAllBytes(MANIFEST));var q=S1ResearchFixtures.qualify(f.manifest,f.state,List.of(),temp.resolve(UUID.randomUUID().toString()),"a07");f.manifest=q.manifest();if(!variant.equals("no-scope-authority"))S1QualifiedProducerTest.qualifyScope(f,temp.resolve(UUID.randomUUID().toString()));return new Sample(f,q,new ArrayList<>(f.inputs));
    }
    SystemGraphAnalyzer.Finding evaluate(Sample s,boolean current)throws Exception{
        var f=s.source();var inputs=new ArrayList<>(s.inputs());var collector=RuleAnalyzers.collector(f.manifest,s.qualification().request(),BACKEND);var raw=collector.analyze(f.state,inputs,Map.of()).getFirst();
        if(!raw.measurements().containsKey("payload"))return raw;
        inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"a07"),"measurement","athena:rule-measurements",raw.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8),collector.method(),f.state.subject(),T,List.of()));if(current)inputs.addAll(s.qualification().artifacts());return RuleAnalyzers.evaluator(f.manifest,s.qualification().request()).analyze(f.state,inputs,Map.of()).getFirst();
    }
    @Test void currentQualifiedEntryOccurrenceIsAdvisoryOnly()throws Exception{var r=evaluate(sample("valid"),true);assertEquals(EvidenceInterpretation.Status.SUPPORTED_PRESENT,r.status(),r.toString());assertEquals("SUPPORTED_PRESENT",JSON.readTree(r.measurements().get("entryAssessments")).path("840").asText());assertEquals("false",r.measurements().get("catalogWideAbsenceAvailable"));}
    @Test void allAdmittedEntriesNegativeStillCannotAssertCatalogAbsence()throws Exception{var r=evaluate(sample("negative"),true);assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,r.status(),r.toString());assertEquals("ABSENT_FALSE",JSON.readTree(r.measurements().get("entryAssessments")).path("840").asText());assertEquals("false",r.measurements().get("catalogWideAbsenceAvailable"));}
    @Test void noCurrentScientificAuthority()throws Exception{assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,evaluate(sample("valid"),false).status());}
    @Test void independentSourceAuthorityRequired()throws Exception{assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,evaluate(sample("no-scope-authority"),true).status());}
    @ParameterizedTest @ValueSource(strings={"missing-h","missing-none","missing-scope","unknown-scope","conflict","missing-charge","missing-aromaticity","incomplete-graph","contradictory-h","unspecified-h"})
    void incompleteAndConflictingChemistryNeverEstablishesAbsence(String variant)throws Exception{var r=evaluate(sample(variant),true);assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,r.status(),r.toString());assertFalse(r.measurements().containsKey("entryAssessments"));}
    @Test void duplicatesAndReorderedEvidenceIdempotent()throws Exception{var s=sample("valid");var a=evaluate(s,true);var inputs=new ArrayList<>(s.inputs());inputs.add(inputs.getFirst());Collections.reverse(inputs);assertEquals(a,evaluate(new Sample(s.source(),s.qualification(),inputs),true));}
    @Test void inheritedCatalogNotImplicitlyConsumed()throws Exception{var s=sample("valid");var inputs=s.inputs().stream().filter(e->!e.evidenceType().equals("athena:advisory-alert-catalog")).toList();assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,evaluate(new Sample(s.source(),s.qualification(),inputs),true).status());}
    @ParameterizedTest @ValueSource(strings={"query","querySha256","sourceStateSha256","format","version","entry","sourcePins","definitionSha256","lineage","correspondence","truncated-correspondence"})
    void changedRawProvenanceCannotReplay(String mutation)throws Exception{
        var s=sample("valid");var f=s.source();var inputs=new ArrayList<>(s.inputs());var collector=RuleAnalyzers.collector(f.manifest,s.qualification().request(),BACKEND);var raw=(ObjectNode)JSON.readTree(collector.analyze(f.state,inputs,Map.of()).getFirst().measurements().get("payload"));
        var result=(ObjectNode)raw.path("results").path("840").path("result");var evidence=(ObjectNode)result.path("evidence");
        switch(mutation){case "truncated-correspondence"->((ObjectNode)result.path("queryToTargetAtomIds").get(0)).remove("query:0");case "lineage"->evidence.putObject("atomLineage");case "correspondence"->((ObjectNode)result.path("queryToTargetAtomIds").get(0)).put("query:0","absent-source");case "version"->evidence.put("version","2026.7.2/athena-ocl-occurrences/2");case "entry"->((ObjectNode)raw.get("results")).remove("840");case "sourcePins"->raw.putArray("sourcePins");case "definitionSha256"->raw.put("definitionSha256","changed");default->{var msgs=(com.fasterxml.jackson.databind.node.ArrayNode)evidence.get("messages");String prefix=mutation.equals("format")?"queryFormat=":mutation+"=";for(int i=0;i<msgs.size();i++)if(msgs.get(i).asText().startsWith(prefix))msgs.set(i,JSON.getNodeFactory().textNode(prefix+"changed"));}}
        inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"a07-mutation"),"measurement","athena:rule-measurements",SystemStateView.bytes(raw),collector.method(),f.state.subject(),T,List.of()));inputs.addAll(s.qualification().artifacts());assertThrows(IllegalArgumentException.class,()->RuleAnalyzers.evaluator(f.manifest,s.qualification().request()).analyze(f.state,inputs,Map.of()));
    }

    @Test void missingOriginalPreparationBytesRemainUnknown()throws Exception {
        var s=sample("valid");var scope=s.inputs().stream().filter(e->e.evidenceType().equals("athena:event-source")).findFirst().orElseThrow();var i=(EvidenceInterpretation)new EvidenceExchange().decodeRecord(scope.readPayload());var original=i.inputs().get(2).reference();var inputs=s.inputs().stream().filter(e->!e.reference().equals(original)).toList();assertEquals(EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE,evaluate(new Sample(s.source(),s.qualification(),inputs),true).status());
    }
    @Test void expiredAuthorityCannotAdmitCatalogMatches()throws Exception {
        var s=sample("valid");var artifacts=new ArrayList<>(s.qualification().artifacts());var e=artifacts.stream().filter(x->x.evidenceType().equals("athena:rule-qualification-receipt")).findFirst().orElseThrow();var r=totah.lab.athena.system.rules.research.ResearchDocuments.decode(e.readPayload(),totah.lab.athena.system.rules.research.RuleQualificationReceipt.class);var expired=new totah.lab.athena.system.rules.research.RuleQualificationReceipt(r.schema(),r.ruleKey(),r.manifestSha256(),r.eligibility(),r.implementationReport(),r.foundationCertificate(),r.stateBinding(),r.request(),r.qualification(),r.mode(),r.evaluatedAt().plusSeconds(60),r.reasons());artifacts.remove(e);artifacts.add(S1ResearchFixtures.envelope(s.source().state,"a07-expired","receipt","athena:rule-qualification-receipt",totah.lab.athena.system.rules.research.ResearchDocuments.encode(expired)));var q=new S1ResearchFixtures.Qualified(s.qualification().manifest(),s.qualification().request(),artifacts);assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,evaluate(new Sample(s.source(),q,s.inputs()),true).status());
    }
    @Test void changedBytesUnderSameEvidenceIdentityReject()throws Exception {
        var s=sample("valid");var old=s.inputs().getFirst();var replacement=new EvidenceEnvelope(old.reference(),old.evidenceType(),old.payloadFormat(),old.payloadVersion(),Optional.of(Base64.getEncoder().encodeToString("{}".getBytes())),Optional.empty(),EvidenceExchange.sha256("{}".getBytes()),old.provenance(),old.method(),old.context(),old.subjects(),old.qualifications(),old.limitations(),old.recordedAt());var inputs=new ArrayList<>(s.inputs());inputs.add(replacement);assertThrows(IllegalArgumentException.class,()->evaluate(new Sample(s.source(),s.qualification(),inputs),true));
    }
    @Test void noProductionQualificationFlagCanSelfActivate()throws Exception {
        var n=(ObjectNode)JSON.readTree(Files.readAllBytes(MANIFEST));n.put("qualification","QUALIFIED");assertThrows(java.io.IOException.class,()->RuleRegistry.decode(SystemStateView.bytes(n)));
    }

    @ParameterizedTest @ValueSource(ints={113,159,162,169,202,207,840})
    void everyAdmittedEntryRequiresIndependentSourceFactsAndCurrentAuthority(int entry)throws Exception {
        var r=evaluate(sample("entry:"+entry),true);assertEquals(EvidenceInterpretation.Status.SUPPORTED_PRESENT,r.status(),r.toString());assertEquals("SUPPORTED_PRESENT",JSON.readTree(r.measurements().get("entryAssessments")).path(Integer.toString(entry)).asText());
    }
    public static void main(String[] args)throws Exception{var t=new AdvisoryAlertAcceptanceTest();t.temp=Files.createDirectories(Path.of(args[0]));Files.write(Path.of(args[1]),SystemStateView.bytes(t.evaluate(t.sample("valid"),true)));}
}
