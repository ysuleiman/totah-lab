package totah.lab.daedalus.system;
public class RingDialectProbe {
 public static void main(String[] x)throws Exception {
  for(String atom:new String[]{"[#6,#7,#8,#16]","[*]"})for(String bond:new String[]{"-,:","-,:,=","~,:","~"})for(int size:new int[]{5,6}){
   String q=atom+"1"+(bond+atom).repeat(size-1)+bond+"1";
   System.out.print(q);
   for(String name:new String[]{"indole","naphthalene","pyrrole","benzene","cyclohexane"})try{
    var f=FoundationVocabularyAcceptanceTest.molecule(name);
    System.out.print(" "+name+"="+B01FunctionalGroupAcceptanceTest.BACKEND.match(q,f.graph()).queryToTargetAtomIds().size());
   }catch(Exception e){System.out.print(" ERROR="+e.getMessage());}
   System.out.println();
  }
 }
}
