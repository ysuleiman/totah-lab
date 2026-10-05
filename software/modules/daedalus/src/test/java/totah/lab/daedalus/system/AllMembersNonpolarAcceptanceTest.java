package totah.lab.daedalus.system;
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
class AllMembersNonpolarAcceptanceTest {
 @TempDir Path temp;
 static RuleManifest model()throws Exception{return RuleRegistry.decode(Files.readAllBytes(Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/all-members-nonpolar-v1/ATHENA.PERCEPTION.AROMATIC_CARBOCYCLE.ALL_MEMBERS_NONPOLAR.rule.json")));}
 static Fixture molecule(String n)throws Exception{return n.equals("toluene")||n.equals("nitrobenzene")?ChargeNonpolarAcceptanceTest.molecule(n):FoundationVocabularyAcceptanceTest.molecule(n);}
 static List<EvidenceEnvelope> sources(SystemStateView s,ObjectNode coverage)throws Exception {
  var out=new ArrayList<EvidenceEnvelope>();
  for(var n:JSON.readTree(model().parameters().get("sourceManifests").value())) {
   var m=RuleRegistry.decode(SystemStateView.bytes(n));var collector=RuleAnalyzers.collector(m,request(s,m),BACKEND);
   var result=collector.analyze(s,List.of(envelope(s,"athena:group-source-coverage",coverage),envelope(s,"athena:group-definition",JSON.readTree(m.parameters().get("definition").value()))),Map.of()).getFirst();
   out.add(ChargeGroupAcceptanceTest.wrap(s,"athena:group-identities",JSON.readTree(result.measurements().get("payload")),collector.method(),m.ruleId()));
  }return out;
 }
 static List<SystemGraphAnalyzer.Finding> run(SystemStateView s,List<EvidenceEnvelope> inputs)throws Exception {
  var before=SystemStateView.bytes(s.snapshot());var original=new ArrayList<byte[]>();var exchange=new EvidenceExchange();for(var e:inputs)original.add(exchange.encodeRecord(e));
  var m=model();var analyzer=RuleAnalyzers.evaluator(m,request(s,m));var result=analyzer.analyze(s,inputs,Map.of());
  for(var f:result) {
   assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,f.status());
   if(f.measurements().containsKey("sourceOccurrenceId")) {
    var source=inputs.stream().filter(e->e.payloadSha256().equals(f.measurements().get("sourceReportSha256"))).findFirst().orElseThrow();
    var ids=new TreeSet<String>();boolean found=false;
    for(var occurrence:JSON.readTree(source.readPayload()).get("occurrences"))if(occurrence.get("occurrenceId").asText().equals(f.id())) {found=true;occurrence.get("memberAtomIds").forEach(a->ids.add(a.asText()));}
    assertTrue(found);assertEquals(ids,new TreeSet<>(f.subjects().getFirst().members().stream().map(EvidenceSubject::localId).toList()));
   }
   var record=new EvidenceInterpretation(ref(ScientificReference.Kind.EVIDENCE_INTERPRETATION,"p05-"+f.id()),inputs.stream().map(e->new EvidenceInterpretation.Input(e.reference(),e.payloadSha256())).toList(),analyzer.method(),Map.of(),f.subjects(),f.status(),f.measurements(),f.reasons(),f.limitations(),Optional.empty(),T);
   assertEquals(record,exchange.decodeRecord(exchange.encodeRecord(record)));
  }
  assertArrayEquals(before,SystemStateView.bytes(s.snapshot()));for(int i=0;i<inputs.size();i++)assertArrayEquals(original.get(i),exchange.encodeRecord(inputs.get(i)));return result;
 }
 static List<SystemGraphAnalyzer.Finding> run(Fixture f)throws Exception{var s=system(List.of(f.graph()),true,false);return run(s,sources(s,coverage(s,f)));}
 @TestFactory Stream<DynamicTest> membershipMatrix(){return Stream.of("benzene","toluene","nitrobenzene","pyridine","naphthalene").flatMap(n->Stream.of("normal","explicit","permuted").map(kind->DynamicTest.dynamicTest(n+"/"+kind,()->{
  var f=molecule(n);if(kind.equals("explicit"))f=explicit(f);if(kind.equals("permuted")){var a=new ArrayList<>(f.graph().atoms());var b=new ArrayList<>(f.graph().bonds());Collections.reverse(a);Collections.reverse(b);f=new Fixture(new MolecularGraph(a,b,f.graph().properties()),f.hydrogens());}
  var results=run(f);assertEquals(n.equals("naphthalene")?2:1,results.size());
  for(var r:results){assertEquals(n.equals("pyridine")?"UNSUPPORTED":n.equals("nitrobenzene")?"ABSENT_FALSE":"SUPPORTED_PRESENT",r.measurements().get("assessment"));assertEquals(6,r.subjects().getFirst().members().size());}
  if(n.equals("nitrobenzene"))assertEquals("5",results.getFirst().measurements().get("supportedMemberCount"));
 })));}
 @TestFactory Stream<DynamicTest> incompleteNeverNegative(){return Stream.of("hydrogen","charge","aromaticity","graph","mapping","ambiguousMapping").map(kind->DynamicTest.dynamicTest(kind,()->{
  var f=molecule(kind.equals("ambiguousMapping")?"benzene":"nitrobenzene");var s=system(List.of(f.graph()),true,false);
  if(kind.equals("ambiguousMapping")){var c=s.components().getFirst();var original=c.correspondenceAlternatives().getFirst();var alternate=new TreeMap<>(original);var ids=new ArrayList<>(original.keySet());for(int i=0;i<ids.size();i++)alternate.put(ids.get(i),original.get(ids.get((i+1)%ids.size())));s=new SystemStateView(s.identity(),s.graph(),List.of(new SystemStateView.Component(c.identity(),c.chemistry(),List.of(original,alternate),c.limitations())),s.sources(),s.cofactors(),s.charges(),true,false,s.limitations());}
  if(kind.equals("mapping")){var c=s.components().getFirst();s=new SystemStateView(s.identity(),s.graph(),List.of(new SystemStateView.Component(c.identity(),c.chemistry(),List.of(),c.limitations())),s.sources(),s.cofactors(),s.charges(),true,false,s.limitations());}
  var c=coverage(s,f);if(kind.equals("graph"))c.put("completeGraph","UNKNOWN_INCONCLUSIVE");
  c.get("atomState").forEach(a->{var v=(ObjectNode)a;if(kind.equals("hydrogen")){v.put("hydrogenMode","UNKNOWN");v.putNull("implicitHydrogenCount");}if(kind.equals("charge"))v.put("chargeStatus","UNKNOWN_INCONCLUSIVE");if(kind.equals("aromaticity"))v.put("aromaticityModel","UNKNOWN");});
  for(var r:run(s,sources(s,c)))assertEquals("UNKNOWN_INCONCLUSIVE",r.measurements().get("assessment"));
 }));}
 @Test void orderMissingAndTamper()throws Exception {
  var f=molecule("nitrobenzene");var s=system(List.of(f.graph()),true,false);var inputs=sources(s,coverage(s,f));var original=run(s,inputs);var reversed=new ArrayList<>(inputs);Collections.reverse(reversed);assertEquals(original,run(s,reversed));
  var missing=inputs.stream().filter(e->!e.method().id().contains("AROMATIC_C_H0/")).toList();assertEquals("UNKNOWN_INCONCLUSIVE",run(s,missing).getFirst().measurements().get("assessment"));
  var e=inputs.getFirst();var p=(ObjectNode)JSON.readTree(e.readPayload());p.put("definitionDigest","0".repeat(64));var bad=ChargeGroupAcceptanceTest.wrap(s,"athena:group-identities",p,e.method(),"bad");var changed=new ArrayList<>(inputs);changed.set(0,bad);var bytes=new EvidenceExchange().encodeRecord(bad);var m=model();assertThrows(Exception.class,()->RuleAnalyzers.evaluator(m,request(s,m)).analyze(s,changed,Map.of()));assertArrayEquals(bytes,new EvidenceExchange().encodeRecord(bad));
 }
 @Test void journalRetainsAtomEvidenceAndExactCycleSubject()throws Exception {
  var f=molecule("nitrobenzene");var s=system(List.of(f.graph()),true,false);var inputs=sources(s,coverage(s,f));var m=model();var catalog=new EvidenceSnapshotCatalog(temp);
  var pipeline=new SystemQualificationPipeline(new SystemGraphValidation(BACKEND,BACKEND,BACKEND));
  var published=pipeline.run(catalog,Optional.empty(),s,inputs,Map.of(),List.of(RuleAnalyzers.evaluator(m,request(s,m))),ref(ScientificReference.Kind.ACTIVITY,"p05-journal"),T);
  var history=catalog.read(published.catalogSnapshot()).orElseThrow().history();
  for(var e:inputs)assertArrayEquals(e.readPayload(),history.envelopes().get(e.reference()).readPayload());
  assertTrue(history.interpretations().values().stream().anyMatch(i->"ABSENT_FALSE".equals(i.measurements().get("assessment"))&&i.subjects().getFirst().members().size()==6));
 }
 public static void main(String[] args)throws Exception{var results=new TreeMap<String,Object>();for(String n:List.of("benzene","toluene","nitrobenzene","pyridine","naphthalene"))results.put(n,run(molecule(n)));Files.write(Path.of(args[0]),SystemStateView.bytes(results));}
}
