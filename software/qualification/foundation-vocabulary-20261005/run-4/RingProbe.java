package totah.lab.daedalus.system;
public class RingProbe {
 public static void main(String[] x)throws Exception {
  for(String name:new String[]{"indole","naphthalene","pyrrole"}) {
   var f=FoundationVocabularyAcceptanceTest.molecule(name);
   for(String q:new String[]{"[c]","[n]","[c]1:[c]:[c]:[c]:[c]:[c]~1","[c,n,o,s]1~[c,n,o,s]~[c,n,o,s]~[c,n,o,s]~[c,n,o,s]~1","[c,n,o,s]1~[c,n,o,s]~[c,n,o,s]~[c,n,o,s]~[c,n,o,s]~[c,n,o,s]~1"})
    System.out.println(name+" "+q+" "+B01FunctionalGroupAcceptanceTest.BACKEND.match(q,f.graph()).queryToTargetAtomIds());
  }
 }
}
