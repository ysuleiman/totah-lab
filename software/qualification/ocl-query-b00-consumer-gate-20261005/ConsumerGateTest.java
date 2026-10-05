import com.actelion.research.chem.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class ConsumerGateTest {
 int count(String target,String query,int mode)throws Exception {
  var t=new StereoMolecule();new SmilesParser(SmilesParser.SMARTS_MODE_IS_SMILES).parse(t,target);
  var q=new StereoMolecule();new SmilesParser(SmilesParser.SMARTS_MODE_IS_SMARTS).parse(q,query);
  var s=new SSSearcher();s.setMol(q,t);return s.findFragmentInMolecule(mode,SSSearcher.cDefaultMatchMode);
 }
 @Test void benzeneDistinctAtoms()throws Exception {assertEquals(1,count("c1ccccc1","[c]",6));assertEquals(6,count("c1ccccc1","[c]",4));}
 @Test void propaneDistinctAtoms()throws Exception {assertEquals(2,count("CCC","C",6));assertEquals(3,count("CCC","C",4));}
 @Test void propaneDistinctBonds()throws Exception {assertEquals(1,count("CCC","CC",6));assertEquals(2,count("CCC","CC",4));assertEquals(4,count("CCC","CC",5));}
 @Test void automorphismsAreNotOccurrences()throws Exception {assertEquals(2,count("CC","CC",5));assertEquals(1,count("CC","CC",4));}
 @Test void distinctSymmetricEstersSurvive()throws Exception {assertEquals(1,count("COC(=O)C(=O)OC","C(=O)O",6));assertEquals(2,count("COC(=O)C(=O)OC","C(=O)O",4));}
 @Test void qualifiedConsumerVerdictChanges()throws Exception {
  var q=Path.of("software/qualification/ocl-query-b00-consumer-gate-20261005");
  var old=Files.readString(q.resolve("old.txt"));var next=Files.readString(q.resolve("candidate.txt"));
  assertTrue(old.contains("assessment=UNKNOWN_INCONCLUSIVE"));assertTrue(next.contains("assessment=SUPPORTED_PRESENT"));
  assertTrue(old.contains("candidateSupported=[true, false]"));assertTrue(next.contains("candidateSupported=[true, true]"));
  assertEquals(old.lines().filter(s->s.startsWith("rawCandidates=")).findFirst(),next.lines().filter(s->s.startsWith("rawCandidates=")).findFirst());
 }
}
