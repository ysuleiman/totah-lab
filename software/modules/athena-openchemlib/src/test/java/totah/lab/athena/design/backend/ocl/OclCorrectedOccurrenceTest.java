package totah.lab.athena.design.backend.ocl;
import org.junit.jupiter.api.*;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.system.SystemStateView;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
class OclCorrectedOccurrenceTest {
 static final OclMolecularBackend B=new OclMolecularBackend();
 @TestFactory Stream<DynamicTest> correctedCharacterizationCorpus()throws Exception {
  return OclQuerySemanticsQualificationTest.cases().stream().map(c->DynamicTest.dynamicTest(c.id(),()-> {
   var graph=OclQuerySemanticsQualificationTest.fixture(c.fixture());var before=SystemStateView.digest(graph);
   var r=B.match(c.query(),graph);
   String expected=switch(c.id()) {
    case "ring","aromatic","uppercase-not-aliphatic"->"id0;id1;id2;id3;id4;id5";
    case "propane-atoms"->"id0;id1;id2";
    case "propane-bond"->"id0,id1;id1,id2";
    default->c.expected();
   };
   assertEquals(expected,OclQuerySemanticsQualificationTest.mappings(r));
   assertEquals(before,SystemStateView.digest(graph));
   assertTrue(r.evidence().version().endsWith("athena-ocl-occurrences/2"));
   assertTrue(r.evidence().messages().contains("sourceStateSha256="+before));
   assertEquals(r,B.match(c.query(),graph));
  }));
 }
 @TestFactory Stream<DynamicTest> unsupportedIsNotNegative() {
  return Stream.of("[z2]","[C;Q]","[A]","","C(","C)","C()","C ","C name","C\nO","C-","C.","[C","C1CC","C..O",".C","C--O","C(=)").map(q->DynamicTest.dynamicTest("reject "+q,()->assertThrows(MolecularBackendException.class,()->B.match(q,OclQuerySemanticsQualificationTest.fixture("ethanol")))));
 }
 @Test void canonicalAcrossAtomAndBondOrder()throws Exception {
  for(String name:List.of("benzene","propane","ethanol","propanol")) {
   var g=OclQuerySemanticsQualificationTest.fixture(name);var atoms=new ArrayList<>(g.atoms());var bonds=new ArrayList<>(g.bonds());Collections.reverse(atoms);Collections.reverse(bonds);
   var other=new MolecularGraph(atoms,bonds,g.properties());
   for(String query:List.of("C","CC","CCC"))assertEquals(B.match(query,g).queryToTargetAtomIds(),B.match(query,other).queryToTargetAtomIds());
  }
 }
 @Test void wholeOccurrenceDeduplicatesButPreservesEmbeddings()throws Exception {
  var r=B.match("CCC",OclQuerySemanticsQualificationTest.fixture("propane"));assertEquals(1,r.queryToTargetAtomIds().size());
  assertTrue(r.evidence().messages().stream().anyMatch(m->m.contains("[\"id2\",\"id1\",\"id0\"]")));
  assertThrows(UnsupportedOperationException.class,()->r.queryToTargetAtomIds().clear());
 }
 @Test void sourceHydrogenFailure() {
  var g=new MolecularGraph(List.of(new MolecularGraph.Atom("C","C",null,0,1,false,"UNSPECIFIED",null,Map.of())),List.of(),Map.of());
  assertThrows(MolecularBackendException.class,()->B.match("[CH4]",g));
 }
 public static void main(String[] args)throws Exception {
  var out=new ArrayList<Object>();
  for(var c:OclQuerySemanticsQualificationTest.cases()) {var g=OclQuerySemanticsQualificationTest.fixture(c.fixture());out.add(Map.of("case",c.id(),"source",g,"result",B.match(c.query(),g)));}
  Files.write(Path.of(args[0]),SystemStateView.bytes(out));
 }
}
